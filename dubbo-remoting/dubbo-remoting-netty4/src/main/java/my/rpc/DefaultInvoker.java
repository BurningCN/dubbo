package my.rpc;

import my.server.Client;
import my.server.RemotingException;
import my.server.URL;
import org.apache.dubbo.rpc.TimeoutCountDown;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static my.common.constants.CommonConstants.*;
import static my.rpc.Constants.*;
import static my.server.Constants.SENT_KEY;

/**
 * @author geyu
 * @date 2021/2/4 19:13
 */
public class DefaultInvoker<T> extends AbstractInvoker<T> {

    private final Client[] clients;
    private final Set<Invoker<?>> invokers;
    private AtomicPositiveInteger index = new AtomicPositiveInteger();
    private final ReentrantLock destroyLock = new ReentrantLock();

    public DefaultInvoker(Class<T> type, URL url, Client[] clients, Set<Invoker<?>> invokers) {
        super(type, url, new String[]{INTERFACE_KEY, GROUP_KEY, TOKEN_KEY});
        this.clients = clients;
        this.invokers = invokers;
    }

    @Override
    protected Result doInvoke(Invocation invocation) throws RemotingException {
        RpcInvocation inv = (RpcInvocation) invocation;

        Client client;
        if (clients.length == 1) {
            client = clients[0];
        } else {
            client = clients[index.getAndIncrement() % clients.length];
        }
        // 默认是非oneway + 同步 + 超时1000
        boolean isOneway = RpcUtils.isOneway(getURL(), invocation);
        if (isOneway) {
            boolean isSent = getURL().getMethodParameter(RpcUtils.getMethodName(invocation), SENT_KEY, false);
            client.getChannel().send(inv, isSent);
            return AsyncRpcResult.newDefaultAsyncResult(invocation);
        } else {
            int timeout = (int) invocation.get(TIMEOUT_KEY);
            ExecutorService executor = getCallbackExecutor(getURL(), inv);

            CompletableFuture<AppResponse> appResponseFuture =
                    client.getChannel().request(inv, timeout, executor).thenApply(obj -> (AppResponse) obj);

            AsyncRpcResult result = new AsyncRpcResult(appResponseFuture, inv);
            result.setExecutor(executor);
            return result;
        }
    }

    @Override
    public void destroy() {
        if (super.isDestroyed()) {
            return;
        } else {
            destroyLock.lock();
            try {
                if (super.isDestroyed()) {
                    return;
                }
                super.destroy();
                if (invokers != null) {
                    invokers.remove(this);
                }
                for (Client client : clients) {
                    client.close();
                }
            } catch (RemotingException e) {
                e.printStackTrace();
            } finally {
                destroyLock.unlock();
            }
        }
    }

    @Override
    public boolean isAvailable() {
        if (!super.isAvailable()) {
            return false;
        }
        for (Client client : clients) {
            if (client.isConnected() && !client.getChannel().hasAttribute("channel.readonly")) {
                return true;
            }
        }
        return false;
    }
}

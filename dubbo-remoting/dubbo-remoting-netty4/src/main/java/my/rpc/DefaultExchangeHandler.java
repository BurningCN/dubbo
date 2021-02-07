package my.rpc;

import my.server.ExchangeHandler;
import my.server.InnerChannel;
import my.server.RemotingException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static my.common.constants.CommonConstants.*;

/**
 * @author geyu
 * @date 2021/2/4 13:42
 */
public class DefaultExchangeHandler implements ExchangeHandler {

    private final DefaultProtocol defaultProtocol;

    public DefaultExchangeHandler(DefaultProtocol defaultProtocol) {
        this.defaultProtocol = defaultProtocol;
    }

    @Override
    public CompletableFuture<Object> reply(InnerChannel channel, Object request) throws RemotingException, Exception {
        if (!(request instanceof Invocation)) {
            throw new RemotingException(channel, "Unsupported request: "
                    + (request == null ? null : (request.getClass().getName() + ": " + request))
                    + ", channel: consumer: " + channel.getRemoteAddress() + " --> provider: " + channel.getLocalAddress());
        }
        Invocation inv = (Invocation) request;
        Invoker<?> invoker = getInvoker(channel, inv);
        // todo myRPC 回调逻辑
        Result result = invoker.invoke(inv);
        return result.thenApply(Function.identity());
    }


    @Override
    public void connected(InnerChannel channel) throws RemotingException {

    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {

    }

    @Override
    public void sent(InnerChannel channel, Object message) throws RemotingException {

    }

    @Override
    public void received(InnerChannel channel, Object message) throws RemotingException {

    }

    @Override
    public void caught(InnerChannel channel, Throwable exception) throws RemotingException {

    }

    private Invoker<?> getInvoker(InnerChannel channel, Invocation inv) throws RemotingException {
        String serviceKey = GroupServiceKeyCache.serviceKey(
                (String) (inv.getObjectAttachment(GROUP_KEY)),
                (String) (inv.getObjectAttachment(PATH_KEY)),
                (String) (inv.getObjectAttachment(VERSION_KEY)),
                channel.getLocalAddress().getPort());
        Exporter<?> exporter = defaultProtocol.exporterMap.get(serviceKey);
        if (exporter == null) {
            throw new RemotingException(channel, "Not found exported service: " + serviceKey + " in " + defaultProtocol.exporterMap.keySet() + ", may be version or group mismatch " +
                    ", channel: consumer: " + channel.getRemoteAddress() + " --> provider: " + channel.getLocalAddress() + ", message:" + getInvocationWithoutData(inv));

        }
        return exporter.getInvoker();

    }

    private Invocation getInvocationWithoutData(Invocation inv) {
        if (inv instanceof RpcInvocation) {
            RpcInvocation rpcInvocation = (RpcInvocation) inv;
            rpcInvocation.setObjectAttachments(null);
            return rpcInvocation;
        }
        return inv;
    }
}

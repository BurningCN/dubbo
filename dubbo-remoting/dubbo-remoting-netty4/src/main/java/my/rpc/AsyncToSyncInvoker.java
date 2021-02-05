package my.rpc;

import my.server.RemotingException;
import my.server.URL;
import org.apache.dubbo.rpc.InvokeMode;
import org.apache.dubbo.rpc.RpcInvocation;

import java.util.concurrent.TimeUnit;

/**
 * @author geyu
 * @date 2021/2/4 11:57
 */
public class AsyncToSyncInvoker<T> implements Invoker<T> {

    private final Invoker<T> invoker;

    public AsyncToSyncInvoker(Invoker<T> invoker) {
        this.invoker = invoker;
    }

    @Override
    public Class<T> getInterface() {
        return invoker.getInterface();
    }

    @Override
    public Result invoke(Invocation invocation) throws Exception, RemotingException {
        Result asyncResult = invoker.invoke(invocation);
        if (InvokeMode.SYNC == ((RpcInvocation) invocation).getInvokeMode()) {
            asyncResult.get(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        return asyncResult;
    }
    @Override
    public URL getURL() {
        return invoker.getURL();
    }

    @Override
    public void destroy() {
        invoker.destroy();
    }

    @Override
    public boolean isAvailable() {
        return invoker.isAvailable();
    }
}

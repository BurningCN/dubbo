package my.rpc;

import my.server.RemotingException;
import my.server.TimeoutException;
import my.server.URL;

import java.util.concurrent.ExecutionException;
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
        try {
            if (InvokeMode.SYNC == ((RpcInvocation) invocation).getInvokeMode()) {
                asyncResult.get(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);// 进去
            }
        }catch (InterruptedException e){
             throw new RpcException("Interrupted unexpectedly while waiting for remote result to return!  method: " +
                    invocation.getMethodName() + ", provider: " + invoker.getURL() + ", cause: " + e.getMessage(), e);

        }catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof TimeoutException) {
                // 三个参数code 、 msg、 cause
                throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout. method: " +
                        invocation.getMethodName() + ", provider: " + invoker.getURL() + ", cause: " + e.getMessage(), e);
            } else if (t instanceof RemotingException) {  // 网络异常，比如序列化的对象没有实现Serializable接口，netty encode就是失败
                throw new RpcException(RpcException.NETWORK_EXCEPTION, "Failed to invoke remote method: " +
                        invocation.getMethodName() + ", provider: " + invoker.getURL() + ", cause: " + e.getMessage(), e);
            } else {
                throw new RpcException(RpcException.UNKNOWN_EXCEPTION, "Fail to invoke remote method: " +
                        invocation.getMethodName() + ", provider: " + invoker.getURL() + ", cause: " + e.getMessage(), e);
            }
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

    // for Test
    public Invoker<T> getInvoker() {
        return invoker;
    }
}

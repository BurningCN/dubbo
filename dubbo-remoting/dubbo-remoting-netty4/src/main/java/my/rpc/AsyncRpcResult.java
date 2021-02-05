package my.rpc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * @author geyu
 * @date 2021/2/4 16:16
 */
public class AsyncRpcResult implements Result {
    private RpcContext storedContext;
    private RpcContext storedServerContext;
    private Invocation invocation;
    private CompletableFuture<AppResponse> responseFuture;
    private Executor executor;

    public AsyncRpcResult(CompletableFuture<AppResponse> future, Invocation invocation) {
        this.responseFuture = future;
        this.invocation = invocation;
        this.storedContext = RpcContext.getContext();
        this.storedServerContext = RpcContext.getServerContext();
    }

    public static AsyncRpcResult newDefaultAsyncResult(Invocation invocation, AppResponse appResponse) {
        return new AsyncRpcResult(CompletableFuture.completedFuture(appResponse), invocation);
    }

    public static AsyncRpcResult newDefaultAsyncResult(Invocation invocation) {
        return newDefaultAsyncResult(invocation, null, null);

    }

    public static AsyncRpcResult newDefaultAsyncResult(Invocation invocation, Throwable t) {
        return newDefaultAsyncResult(invocation, null, t);
    }

    public static AsyncRpcResult newDefaultAsyncResult(Invocation invocation, Object v) {
        return newDefaultAsyncResult(invocation, v, null);
    }


    public static AsyncRpcResult newDefaultAsyncResult(Invocation inv, Object v, Throwable throwable) {
        CompletableFuture<AppResponse> future = new CompletableFuture<>();
        AppResponse appResponse = new AppResponse();
        if (throwable == null) {
            appResponse.setException(throwable);
        } else if (v != null) {
            appResponse.setValue(v);
        }
        future.complete(appResponse);
        return new AsyncRpcResult(future, inv);
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public void setValue(Object value) {

    }

    @Override
    public Throwable getException() {
        return null;
    }

    @Override
    public void setException(Throwable t) {

    }

    @Override
    public Object recreate() {
        RpcInvocation rpcInvocation = (RpcInvocation) invocation;
        if (rpcInvocation.getInvokeMode() == InvokeMode.FUTURE) {
            return RpcContext.getContext().getFuture();
        }
        return getAppResponse();
    }

    public Result getAppResponse() {
        try {
            if (responseFuture.isDone()) {
                return responseFuture.get();
            }
        }catch (Exception e) {
            // This should not happen in normal request process;
            System.out.println("Got exception when trying to fetch the underlying result from AsyncRpcResult.");
            throw new RpcException(e);
        }
        return null; // todo myRPC 返回一个默认的AppResponse
    }

    @Override
    public Result get() {
        return null;
    }

    @Override
    public Result get(long timeout, TimeUnit unit) {
        return null;
    }

    public CompletableFuture<AppResponse> getResponseFuture() {
        return responseFuture;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }
}

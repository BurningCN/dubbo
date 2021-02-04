package my.rpc;
import java.util.concurrent.CompletableFuture;
/**
 * @author geyu
 * @date 2021/2/4 16:16
 */
public class AsyncRpcResult implements Result {
    private RpcContext storedContext;
    private RpcContext storedServerContext;
    private Invocation invocation;

    private CompletableFuture<AppResponse> responseFuture;

    public AsyncRpcResult(CompletableFuture<AppResponse> future, Invocation invocation) {
        this.responseFuture = future;
        this.invocation = invocation;
        this.storedContext = RpcContext.getContext();
        this.storedServerContext = RpcContext.getServerContext();
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
}

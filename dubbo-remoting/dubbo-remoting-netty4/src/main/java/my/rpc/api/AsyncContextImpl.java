package my.rpc.api;

import my.rpc.RpcContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author geyu
 * @date 2021/2/9 15:42
 */
public class AsyncContextImpl implements AsyncContext {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private CompletableFuture<Object> future;
    private RpcContext storedContext;
    private RpcContext storedServerContext;

    public AsyncContextImpl() {
        this.storedContext = RpcContext.getContext();
        this.storedServerContext = RpcContext.getServerContext();
    }

    @Override
    public boolean isAsyncStarted() {
        return started.get();
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            future = new CompletableFuture<>();
        }
    }

    @Override
    public void write(Object value) {
        if (isAsyncStarted() && stop()) {
            if (value instanceof Throwable) {
                Throwable throwable = (Throwable) value;
                future.completeExceptionally(throwable);
            } else {
                future.complete(value);
            }
        } else {
            throw new IllegalStateException("The async response has probably been wrote back by another thread, or the asyncContext has been closed.");
        }
    }

    @Override
    public boolean stop() {
        return stopped.compareAndSet(false, true);
    }

    public CompletableFuture<Object> getFuture() {
        return future;
    }
}

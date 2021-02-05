package my.rpc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author geyu
 * @date 2021/2/5 11:56
 */
public class FutureContext {
    private static ThreadLocal<FutureContext> futureTL = ThreadLocal.withInitial(() -> new FutureContext());
    private CompletableFuture<?> future;

    public static FutureContext getContext() {
        return futureTL.get();
    }

    public void setFuture(CompletableFuture<?> future) {
        this.future = future;
    }

    public <T> CompletableFuture<T> getFuture() {
        return (CompletableFuture<T>) future;
    }
}

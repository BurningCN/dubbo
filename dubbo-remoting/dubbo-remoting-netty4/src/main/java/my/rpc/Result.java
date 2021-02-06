package my.rpc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * @author geyu
 * @date 2021/2/4 11:28
 */
public interface Result {
    Object getValue();

    void setValue(Object value);

    Throwable getException();

    void setException(Throwable t);

    Object recreate() throws Throwable;

    Result get();

    Result get(long timeout, TimeUnit unit);

    <U> CompletableFuture<U> thenApply(Function<Result, ? extends U> fn);

    Map<String, Object> getObjectAttachments();

    void setObjectAttachments(Map<String, Object> map);


}

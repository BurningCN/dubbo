package my.rpc;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author geyu
 * @date 2021/2/4 16:13
 */
public class AppResponse implements Result {
    private Object value;

    private Throwable exception;

    private Map<String, Object> attachments = new HashMap<>();

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public Throwable getException() {
        return exception;
    }

    @Override
    public void setException(Throwable exception) {
        this.exception = exception;
    }

    @Override
    public Object recreate() throws Throwable {
        if (exception != null) {
            try {
                Class<?> exceptionClass = exception.getClass();
                while (exceptionClass != Throwable.class) {
                    exceptionClass = exceptionClass.getSuperclass();
                }
                Field stackTraceField = exceptionClass.getDeclaredField("stackTrace");
                stackTraceField.setAccessible(true);
                Object stackTrace = stackTraceField.get(exception);
                if (stackTrace == null) {
                    exception.setStackTrace(new StackTraceElement[0]);
                }
            } catch (Throwable e) {
                // ignore
            }
            throw exception;

        }
        return value;
    }

    @Override
    public Result get() {
        return null;
    }

    @Override
    public Result get(long timeout, TimeUnit unit) {
        return null;
    }

    @Override
    public <U> CompletableFuture<U> thenApply(Function<Result, ? extends U> fn) {
        throw new UnsupportedOperationException("AppResponse represents an concrete business response, there will be no status changes, you should get internal values directly.");
    }

    @Override
    public Result whenCompleteWithContext(BiConsumer<Result, Throwable> fn) {
        throw new UnsupportedOperationException("AppResponse represents an concrete business response, there will be no status changes, you should get internal values directly.");
    }


    @Override
    public Map<String, Object> getObjectAttachments() {
        return attachments;
    }

    @Override
    public void setObjectAttachments(Map<String, Object> map) {
        this.attachments = map == null ? new HashMap<>() : map;
    }
}

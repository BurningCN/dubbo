package my.rpc;

/**
 * @author geyu
 * @date 2021/2/4 16:13
 */
public class AppResponse implements Result{
    private Object value;

    private Throwable exception;

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
}

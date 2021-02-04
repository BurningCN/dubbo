package my.rpc;

/**
 * @author geyu
 * @date 2021/2/4 11:28
 */
public interface Result {
    Object getValue();

    void setValue(Object value);

    Throwable getException();

    void setException(Throwable t);
}

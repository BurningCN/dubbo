package my.rpc.api;

/**
 * @author geyu
 * @date 2021/2/9 15:41
 */
public interface AsyncContext {

    boolean isAsyncStarted();

    void start();

    void write(Object value);

    boolean stop();
}

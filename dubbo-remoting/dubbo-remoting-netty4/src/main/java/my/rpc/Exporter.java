package my.rpc;

/**
 * @author gy821075
 * @date 2021/2/4 11:26
 */
public interface Exporter<T> {
    Invoker<T> getInvoker();

    void unExport();
}

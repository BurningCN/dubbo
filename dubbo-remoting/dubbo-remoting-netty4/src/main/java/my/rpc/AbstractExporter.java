package my.rpc;

/**
 * @author geyu
 * @date 2021/2/4 11:29
 */
public abstract class AbstractExporter<T> implements Exporter<T> {

    private final Invoker<T> invoker;

    private volatile boolean unExported = false;

    public AbstractExporter(Invoker<T> invoker) {
        if (invoker == null) {
            throw new IllegalStateException("service invoker == null");
        }
        if (invoker.getInterface() == null) {
            throw new IllegalStateException("service type == null");
        }
        if (invoker.getURL() == null) {
            throw new IllegalStateException("service url == null");
        }
        this.invoker = invoker;
    }

    @Override
    public Invoker<T> getInvoker() {
        return invoker;
    }

    @Override
    public void unExport() {
        if (!unExported) {
            invoker.destroy();
            unExported = true;
        }
    }

    @Override
    public String toString() {
        return "AbstractExporter{" +
                "invoker=" + invoker +
                '}';
    }
}

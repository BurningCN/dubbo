package my.rpc;

import java.util.Map;

/**
 * @author geyu
 * @date 2021/2/4 11:41
 */
public class DefaultExporter<T> extends AbstractExporter<T> {

    private String serviceKey;
    private Map<String, Exporter<T>> exporterMap;

    public DefaultExporter(Invoker<T> invoker, String serviceKey, Map<String, Exporter<T>> exporterMap) {
        super(invoker);
        this.serviceKey = serviceKey;
        this.exporterMap = exporterMap;
    }

    @Override
    public void unExport() {
        super.unExport();
        exporterMap.remove(serviceKey);
    }
}

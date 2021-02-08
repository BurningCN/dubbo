package my.rpc;

import my.server.NettyTransporter;
import my.server.Server;
import my.server.Transporter;
import my.server.URL;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author geyu
 * @date 2021/2/4 11:54
 */
public abstract class AbstractProtocol implements Protocol {

    protected Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<>();
    protected Set<Invoker<?>> invokerSet = new HashSet<>();
    protected Map<String, Server> serverMap = new ConcurrentHashMap<>();
    protected Transporter transporter = new NettyTransporter();
    protected Map<String, List<ReferenceCountClient>> referenceCountClientMap = new ConcurrentHashMap<>();
    protected ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) {
        Invoker<T> invoker = doRefer(type, url);
        return new AsyncToSyncInvoker(invoker);
    }

    protected abstract <T> Invoker<T> doRefer(Class<T> type, URL url);

    public Map<String, Exporter<?>> getExporterMap() {
        return exporterMap;
    }

}

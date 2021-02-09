package my.rpc.api.protocol;

import my.common.extension.Activate;
import my.common.extension.ExtensionLoader;
import my.rpc.*;
import my.server.RemotingException;
import my.server.URL;

import java.util.List;
import java.util.ListIterator;

/**
 * @author geyu
 * @date 2021/2/8 17:34
 */
@Activate(order = 100)
public class ProtocolFilterWrapper implements Protocol {

    private final Protocol protocol;

    public ProtocolFilterWrapper(Protocol protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol == null");
        }
        this.protocol = protocol;
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RemotingException {
        // todo myRPC isRegistry
        return protocol.export(buildInvokerChain(invoker, "service.filter", "provider"));
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) {
        return buildInvokerChain(protocol.refer(type, url), "reference.filter", "consumer");
    }

    private static <T> Invoker<T> buildInvokerChain(Invoker<T> invoker, String key, String group) {
        List<Filter> filterList = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(invoker.getURL(), key, group);
        Invoker innerInvoker = invoker;
        if (CollectionUtils.isNotEmpty(filterList)) {
            for (int i = filterList.size() - 1; i >= 0; i--) {
                Filter filter = filterList.get(i);
                Invoker finalInnerInvoker = innerInvoker;
                Invoker<T> tempInvoker = new Invoker<T>() {
                    @Override
                    public Result invoke(Invocation invocation) throws RemotingException, Exception {
                        Result result = null;
                        try {
                            result = filter.invoke(finalInnerInvoker, invocation);
                        } catch (Exception e) {
                            if (filter instanceof Filter.Listener) {
                                ((Filter.Listener) filter).onError(e, invoker, invocation);
                            }
                        }
                        return result.whenCompleteWithContext((v, t) -> {
                            if (filter instanceof Filter.Listener) {
                                if (t != null) {
                                    ((Filter.Listener) filter).onError(t, invoker, invocation);
                                } else {
                                    ((Filter.Listener) filter).onResponse(v, invoker, invocation);
                                }

                            }
                        });
                    }

                    @Override
                    public Class<T> getInterface() {
                        return finalInnerInvoker.getInterface();
                    }

                    @Override
                    public URL getURL() {
                        return finalInnerInvoker.getURL();
                    }

                    @Override
                    public boolean isAvailable() {
                        return finalInnerInvoker.isAvailable();
                    }

                    @Override
                    public void destroy() {
                        finalInnerInvoker.destroy();
                    }
                };
                innerInvoker = tempInvoker;
            }
        }
        return innerInvoker;
    }


}

package my.rpc;

import my.server.Client;
import my.server.Constants;
import my.server.URL;

import java.util.Map;
import java.util.Set;


/**
 * @author geyu
 * @date 2021/2/4 19:13
 */
public class DefaultInvoker<T> extends AbstractInvoker<T> {

    private final Client[] clients;
    private final Set<Invoker<?>> invokers;

    public DefaultInvoker(Class<T> type, URL url, Client[] clients, Set<Invoker<?>> invokers) {
        super(type, url, new String[]{Constants.INTERFACE_KEY, Constants.GROUP_KEY, Constants.TOKEN_KEY});
        this.clients = clients;
        this.invokers = invokers;
    }
}

package my.rpc;

import my.common.extension.Adaptive;
import my.common.extension_todo.SPI;
import my.server.RemotingException;
import my.server.URL;
/**
 * @author gy821075
 * @date 2021/2/4 11:47
 */
@SPI("default")
public interface Protocol {
    @Adaptive
    <T> Exporter<T> export(Invoker<T> invoker) throws RemotingException;

    @Adaptive
    <T> Invoker<T> refer(Class<T> type, URL url);
}

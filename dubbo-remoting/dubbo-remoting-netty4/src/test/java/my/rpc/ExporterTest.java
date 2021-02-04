package my.rpc;

import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.rpc.support.HelloService;
import my.rpc.support.HelloServiceImpl;
import my.server.NetUtils;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author geyu
 * @date 2021/2/4 16:26
 */
public class ExporterTest {
    public static ProxyFactory proxy = new JavassistProxyFactory();// ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
    private static DefaultProtocol protocol = new DefaultProtocol();

    @Test
    public void export() throws RemotingException {
        Exporter<DemoService> export1;
        Exporter<HelloService> export2;
        for (int i = 0; i < 3; i++) {
            int port = NetUtils.getAvailablePort();
            URL demoUrl = URL.valueOf("dubbo://127.0.0.1:" + port + "/demo?version=1.0.0&group=lala");
            export1 = export(new DemoServiceImpl(), DemoService.class, demoUrl);
        }
        for (int i = 0; i < 3; i++) {
            int port = NetUtils.getAvailablePort();
            URL demoUrl = URL.valueOf("dubbo://127.0.0.1:" + port + "/demo?version=1.0.0&group=haha");
            export2 = export(new HelloServiceImpl(), HelloService.class, demoUrl);
        }
        Map<String, Exporter<?>> exporterMap = protocol.getExporterMap();
        Assertions.assertEquals(exporterMap.size(),6);
    }

    public static <T> Exporter<T> export(T instance, Class<T> type, URL url) throws RemotingException {
        return protocol.export(proxy.getInvoker(instance, type, url));
    }

}

package my.rpc;

import my.common.rpc.model.ApplicationModel;
import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.rpc.support.HelloService;
import my.rpc.support.HelloServiceImpl;
import my.server.Client;
import my.server.NetUtils;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * @author geyu
 * @date 2021/2/4 16:26
 */
public class DefaultProtocolTest {
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
        Assertions.assertEquals(exporterMap.size(), 6);
    }

    @Test
    public void testRefer() throws RemotingException, NoSuchFieldException, IllegalAccessException {
        int port = NetUtils.getAvailablePort();
        URL demoUrl = URL.valueOf("dubbo://127.0.0.1:" + port + "/demo?version=1.0.0&group=lala");
        demoUrl.addParameter("connections", 0);
        demoUrl.addParameter("shareconnections", 10);
        export(new DemoServiceImpl(), DemoService.class, demoUrl);

        Invoker<DemoService> refer = refer(DemoService.class, demoUrl);
        Assertions.assertTrue(refer instanceof AsyncToSyncInvoker);
        AsyncToSyncInvoker asyncToSyncInvoker = (AsyncToSyncInvoker) refer;
        Field defaultInvokerFiled = asyncToSyncInvoker.getClass().getDeclaredField("invoker");
        defaultInvokerFiled.setAccessible(true);
        DefaultInvoker defaultInvoker = (DefaultInvoker) defaultInvokerFiled.get(asyncToSyncInvoker);
        Field clientsField = defaultInvoker.getClass().getDeclaredField("clients");
        clientsField.setAccessible(true);
        Client[] clients = (Client[]) clientsField.get(defaultInvoker);
        Assertions.assertEquals(clients.length, 10);
    }


    @Test
    public void testInvoke() throws RemotingException, InterruptedException {
        URL demoUrl = URL.valueOf("dubbo://localhost:8989/demoService?heartbeat=600000&timeout=6000000&group=demoGroup&version=2.0.0");
        Invoker<DemoService> serverInvoker = proxy.getInvoker(new DemoServiceImpl(), DemoService.class, demoUrl);
        Exporter<DemoService> severExporter = protocol.export(serverInvoker);
        Invoker<DemoService> clientInvoker = protocol.refer(DemoService.class, demoUrl);
        DemoService clientProxy = DefaultProtocolTest.proxy.getProxy(clientInvoker);  // 看到没，4步骤正好是一个对称
        ApplicationModel.getServiceRepository().registerService("demoService",DemoService.class);
        // System.out.println("");
        clientProxy.echo("哈喽，我是mmmm");

        Thread.sleep(600000);

    }

    public static <T> Exporter<T> export(T instance, Class<T> type, URL url) throws RemotingException {
        return protocol.export(proxy.getInvoker(instance, type, url));
    }

    public static <T> Invoker<T> refer(Class<T> type, URL url) throws RemotingException {
        return protocol.refer(type, url);
    }



}

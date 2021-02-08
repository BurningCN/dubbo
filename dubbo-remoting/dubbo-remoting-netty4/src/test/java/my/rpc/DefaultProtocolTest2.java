package my.rpc;

import my.common.extension.ExtensionLoader;
import my.common.rpc.model.ApplicationModel;
import my.common.service.EchoService;
import my.common.utils.NetUtils;
import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.rpc.support.Type;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author geyu
 * @date 2021/2/8 16:56
 */
public class DefaultProtocolTest2 {
    private Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    private ProxyFactory proxy = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    @BeforeAll
    public static void setUp() {
        ApplicationModel.getServiceRepository().registerService(DemoService.class);
    }

    @AfterAll
    public static void after() throws RemotingException {
        ApplicationModel.getServiceRepository().destroy();
    }

    @Test
    public void testDemoProtocol() throws Exception, RemotingException {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        Invoker<DemoService> invoker = proxy.getInvoker(service, DemoService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange"));
        Exporter<DemoService> exporter = protocol.export(invoker);
        Invoker<DemoService> serviceInvoker = protocol.refer(DemoService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange").addParameter("timeout",
                3000L));
        service = proxy.getProxy(serviceInvoker);
        assertEquals(service.getSize(new String[]{"", "", ""}), 3);
    }


    @Test
    public void testEcho() throws Exception, RemotingException {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(service, DemoService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + DemoService.class.getName())));
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < 1024 * 32 + 32; i++)
            buf.append('A');

        EchoService echo = proxy.getProxy(protocol.refer(EchoService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?client=netty").addParameter("timeout",
                3000L)));
        assertEquals(echo.$echo(buf.toString()), buf.toString());
        assertEquals(echo.$echo("test"), "test");
        assertEquals(echo.$echo("abcdefg"), "abcdefg");
        assertEquals(echo.$echo(1234), 1234);
    }
}

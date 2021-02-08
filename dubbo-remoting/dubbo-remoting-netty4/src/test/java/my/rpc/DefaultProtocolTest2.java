package my.rpc;

import my.common.extension.ExtensionLoader;
import my.common.rpc.model.ApplicationModel;
import my.common.service.EchoService;
import my.common.utils.NetUtils;
import my.rpc.support.*;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
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

    @Test
    public void testDefaultProtocolMultiService() throws Exception, RemotingException {
        RemoteService remote = new RemoteServiceImpl();

        ApplicationModel.getServiceRepository().registerService(RemoteService.class);

        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(remote, RemoteService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + RemoteService.class.getName())));
        remote = proxy.getProxy(protocol.refer(RemoteService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + RemoteService.class.getName()).addParameter("timeout",
                3000L)));
        assertEquals("hello world@" + RemoteServiceImpl.class.getName(), remote.sayHello("world"));

        //       can't find target service addresses
        // 这里可以强转的原因是remote是proxy0，其实现的接口列表有AbstractProxyFactory默认指定的这两个Destroyable.class, EchoService.class
        // 强转后依然可以调用$echo，因为服务端有一个EchoFilter进行了特殊处理，不会实际往深了走，即从exporterMap找invoker啥的然后调用啥的
        EchoService remoteEecho = (EchoService) remote;
        assertEquals(remoteEecho.$echo("ok"), "ok");
    }

    @Test
    public void testPerm() throws Exception, RemotingException {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(service, DemoService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange")));
        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange").addParameter("timeout",
                3000L)));
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++)
            service.getSize(new String[]{"", "", ""});
        System.out.println("take:" + (System.currentTimeMillis() - start));
    }

     @Test
    public void testNonSerializedParameter() throws Exception, RemotingException {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(service, DemoService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange")));
        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange").addParameter("timeout",
                3000L)));
        try {
            // 在DefaultCodec进行encodeRequestData时候进行out.writeAttachments(inv.getArguments())（该new NonSerialized()会
            // 保存到inv的属性中）的时候发现是实体类型/引用类型，非基本数据类型，却没有实现序列化接口，netty的encode就会抛异常
            service.nonSerializedParameter(new NonSerialized());
            Assertions.fail();
        } catch (RpcException e) {
            Assertions.assertTrue(e.getMessage().contains("my.rpc.support.NonSerialized must implement java.io.Serializable"));
        }
    }
    @Test
    public void testReturnNonSerialized() throws Exception, RemotingException {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(service, DemoService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange")));
        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("default://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange").addParameter("timeout",
                3000L)));
        try {
            service.returnNonSerialized(); // 服务端在DefaultCodec的encodeResponseData方法内进行writeObject的时候会抛异常
            Assertions.fail();
        } catch (RpcException e) { // 这里暂时不匹配，这段逻辑先不做，不过肯定是会抛异常的
            Assertions.assertTrue(e.getMessage().contains("my.rpc.support.NonSerialized must implement java.io.Serializable"));
        }
    }
    
}
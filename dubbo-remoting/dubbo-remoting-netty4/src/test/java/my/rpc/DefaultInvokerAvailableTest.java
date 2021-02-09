package my.rpc;

import my.common.extension.ExtensionLoader;
import my.common.rpc.model.ApplicationModel;
import my.common.utils.NetUtils;
import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.server.Client;
import my.server.InnerChannel;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author geyu
 * @date 2021/2/8 22:49
 */
public class DefaultInvokerAvailableTest {
    private static DefaultProtocol protocol;
    private static ProxyFactory proxy = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
    }

    @BeforeEach
    public void setUp() throws Exception {
        protocol = new DefaultProtocol();
    }

    @Test
    public void test_Normal_available() throws RemotingException {
        int port = NetUtils.getAvailablePort();
        URL url = URL.valueOf("dubbo://127.0.0.1:" + port + "/my.rpc.support.DemoService");
        protocol.export(proxy.getInvoker(new DemoServiceImpl(), DemoService.class, url));

        DefaultInvoker<?> invoker = (DefaultInvoker<?>) protocol.doRefer(DemoService.class, url);
        Assertions.assertTrue(invoker.isAvailable());
        invoker.destroy();
        Assertions.assertFalse(invoker.isAvailable());
    }

    @Test
    public void test_Normal_ChannelReadOnly() throws Exception, RemotingException {
        int port = NetUtils.getAvailablePort();
        URL url = URL.valueOf("dubbo://127.0.0.1:" + port + "/my.rpc.support.DemoService");
        protocol.export(proxy.getInvoker(new DemoServiceImpl(), DemoService.class, url));

        DefaultInvoker<?> invoker = (DefaultInvoker<?>) protocol.doRefer(DemoService.class, url);
        Assertions.assertTrue(invoker.isAvailable());

        getClientsChannels(invoker)[0].setAttribute("channel.readonly", Boolean.TRUE);

        Assertions.assertFalse(invoker.isAvailable());

        // reset status since connection is shared among invokers
        getClientsChannels(invoker)[0].removeAttribute("channel.readonly");
    }

    @Test
    public void test_NOClients() throws NoSuchFieldException, IllegalAccessException, RemotingException {
        int port = NetUtils.getAvailablePort();
        URL url = URL.valueOf("default://127.0.0.1:" + port + "/test?connections=1");
        protocol.export(proxy.getInvoker(new DemoServiceImpl(), DemoService.class, url));
        ApplicationModel.getServiceRepository().registerService("test", DemoService.class);

        DefaultInvoker<DemoService> refer = (DefaultInvoker<DemoService>) protocol.doRefer(DemoService.class, url);
        DemoService proxy = DefaultInvokerAvailableTest.proxy.getProxy(refer);
        Client[] clients = getClients(refer);
        clients[0].close(); // 一个引用计数，到0.直接关闭NettyClient
        Assertions.assertFalse(refer.isAvailable());

    }

    @Test
    public void test_lazyClient_setChannelReadOnly() throws RemotingException, NoSuchFieldException, IllegalAccessException {
        int port = NetUtils.getAvailablePort();
        URL url = URL.valueOf("default://127.0.0.1:" + port + "/my.rpc.support.DemoService?connections=1&lazy=true");
        // 注意lazy不是专门为RfClient而用的，也可以直接代理NettyClient
        protocol.export(proxy.getInvoker(new DemoServiceImpl(), DemoService.class, url));
        ApplicationModel.getServiceRepository().registerService("test", DemoService.class);

        Invoker<DemoService> refer = protocol.refer(DemoService.class, url);
        DemoService proxy = DefaultInvokerAvailableTest.proxy.getProxy(refer);


        InnerChannel[] clientsChannels = getClientsChannels((DefaultInvoker<?>) ((AsyncToSyncInvoker)refer).getInvoker());
        Assertions.assertThrows(IllegalStateException.class, () -> {
            clientsChannels[0].setAttribute("channel.readonly", true);
        });


        Assertions.assertEquals("ok", proxy.echo("ok"));
        Assertions.assertTrue(refer.isAvailable());
        clientsChannels[0].setAttribute("channel.readonly", true);
        Assertions.assertFalse(refer.isAvailable());


    }

    private InnerChannel[] getClientsChannels(DefaultInvoker<?> invoker) throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = invoker.getClass().getDeclaredField("clients");
        declaredField.setAccessible(true);
        Client[] clients = (Client[]) declaredField.get(invoker);
        List<InnerChannel> innerChannelList = Stream.of(clients).map(client -> client.getChannel()).collect(Collectors.toList());
        return innerChannelList.toArray(new InnerChannel[0]);
    }

    private Client[] getClients(DefaultInvoker<?> invoker) throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = invoker.getClass().getDeclaredField("clients");
        declaredField.setAccessible(true);
        Client[] clients = (Client[]) declaredField.get(invoker);
        return clients;
    }

}

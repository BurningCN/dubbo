package my.rpc;

import my.common.extension.ExtensionLoader;
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

        getClients(invoker)[0].setAttribute("channel.readonly", Boolean.TRUE);

        Assertions.assertFalse(invoker.isAvailable());

        // reset status since connection is shared among invokers
        getClients(invoker)[0].removeAttribute("channel.readonly");
    }

    private InnerChannel[] getClients(DefaultInvoker<?> invoker) throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = invoker.getClass().getDeclaredField("clients");
        declaredField.setAccessible(true);
        Client[] clients = (Client[]) declaredField.get(invoker);
        List<InnerChannel> innerChannelList = Stream.of(clients).map(client -> client.getChannel()).collect(Collectors.toList());
        return innerChannelList.toArray(new InnerChannel[0]);
    }

}

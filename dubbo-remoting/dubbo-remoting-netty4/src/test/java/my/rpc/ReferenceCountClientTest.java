package my.rpc;

import my.common.extension.ExtensionLoader;
import my.common.utils.Assert;
import my.common.utils.NetUtils;
import my.rpc.*;
import my.server.Client;
import my.server.NettyClient;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author geyu
 * @date 2021/2/7 15:46
 */
public class ReferenceCountClientTest {


    private static ProxyFactory proxy = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
    private static DefaultProtocol protocol = DefaultProtocol.getDefaultProtocol();
    private Exporter<IDemoService> demoServerExport;
    private Exporter<IHelloService> helloServerExport;
    private Invoker<IDemoService> demoClientInvoker;
    private IDemoService demoClientProxy;

    private IHelloService helloClientProxy;
    private Invoker<IHelloService> helloClientInvoker;
    private Client demoClient;
    private Client helloClient;

    @Test
    public void test_share_connect() throws RemotingException {
        init(0, 1);// 进去
        Assertions.assertEquals(demoClient.getChannel().getLocalAddress(), helloClient.getChannel().getLocalAddress());
        Assertions.assertEquals(demoClient, helloClient);
        ReferenceCountClient referenceCountClient = (ReferenceCountClient) demoClient;
        Assertions.assertEquals(referenceCountClient.getReferenceCount(), 2);
        destoy();
    }

    @Test
    public void test_not_share_connect() throws RemotingException {
        init(1, 1);// 进去
        Assertions.assertNotSame(demoClient.getChannel().getLocalAddress(), helloClient.getChannel().getLocalAddress());
        Assertions.assertNotSame(demoClient, helloClient);

        Assertions.assertFalse(demoClient instanceof ReferenceCountClient); // 不是ReferenceCountClient类型，不用共享连接，直接用的NettyClient
        Assertions.assertFalse(helloClient instanceof ReferenceCountClient);

        destoy();
    }

    @Test
    public void test_mult_share_connect() throws RemotingException {
        init(0, 3);
        List<ReferenceCountClient> referenceClientList_demoClientInvoker = getReferenceClientList(demoClientInvoker);
        List<ReferenceCountClient> referenceClientList_helloClientInvoker = getReferenceClientList(helloClientInvoker);

        Assertions.assertEquals(referenceClientList_demoClientInvoker.size(), 3);
        Assertions.assertEquals(referenceClientList_helloClientInvoker.size(), 3);

        Assertions.assertEquals(demoClient, helloClient);
        destoy();
    }

    @Test
    public void test_multi_destory() throws RemotingException {
        init(0, 1);
        demoClientInvoker.destroy();
        demoClientInvoker.destroy(); // 这里两次关闭已经将计数置为0了，所有共享的client已经全部close，下面的destroy进去看看会不会还会进close（当然不会）
        destoy();
    }

    @Test
    public void test_counter_error() throws RemotingException, NoSuchFieldException, IllegalAccessException {
        init(0, 1); // 一个client，引用计数为2

        List<ReferenceCountClient> referenceClientList = getReferenceClientList(demoClientInvoker);
        Assertions.assertEquals(referenceClientList.size(), 1);

        List<ReferenceCountClient> referenceClientList1 = getReferenceClientList(helloClientInvoker);
        Assertions.assertEquals(referenceClientList1.size(), 1);

        ReferenceCountClient referenceCountClient = referenceClientList.get(0);
        Field referenceCountField = referenceCountClient.getClass().getDeclaredField("referenceCount");
        referenceCountField.setAccessible(true);
        AtomicLong rc = (AtomicLong) referenceCountField.get(referenceCountClient);
        Assertions.assertEquals(rc.get(), 2);

        referenceCountClient.close();
        Assertions.assertEquals(rc.get(), 1);

        referenceCountClient.close();
        Assertions.assertEquals(rc.get(), 0); // lazy被创建

        Assertions.assertEquals(demoClientProxy.demo(), "demo");//lazy立即创建客户端连接发起调用
        referenceCountClient.close(); // 关闭lazy因上面调用创建的client
    }

    private void destoy() {
        // client invoker destroy
        demoClientInvoker.destroy();
        helloClientInvoker.destroy();

        // server invoker destroy
        demoServerExport.getInvoker().destroy();
        helloServerExport.getInvoker().destroy();
    }

    private void init(int connections, int shareConnections) throws RemotingException {
        Assertions.assertTrue(connections >= 0);
        Assertions.assertTrue(shareConnections >= 1);
        int port = NetUtils.getAvailablePort();
        URL demoURL = URL.valueOf("dubbo://localhost:" + port + "/demo?" +
                "connections=" + connections + "&shareconnections=" + shareConnections
                + "&heartbeat=600000" + "&timeout=600000");// 这里是我特地加的，避免调试出现心跳、请求响应超时
        URL helloURL = URL.valueOf("dubbo://localhost:" + port + "/hello?" +
                "connections=" + connections + "&shareconnections=" + shareConnections
                + "&heartbeat=600000" + "&timeout=600000");// 这里是我特地加的，避免调试出现心跳、请求响应超时;
        IDemoService demoService = new DemoServiceImpl();
        IHelloService helloService = new HelloServiceImpl();

        // ------ server export
        Invoker<IDemoService> demoServerInvoker = proxy.getInvoker(demoService, IDemoService.class, demoURL);
        this.demoServerExport = protocol.export(demoServerInvoker);

        Invoker<IHelloService> helloServerInvoker = proxy.getInvoker(helloService, IHelloService.class, helloURL);
        this.helloServerExport = protocol.export(helloServerInvoker);

        // ------ client refer
        this.demoClientInvoker = protocol.refer(IDemoService.class, demoURL);
        this.demoClientProxy = proxy.getProxy(demoClientInvoker);

        this.helloClientInvoker = protocol.refer(IHelloService.class, helloURL);
        this.helloClientProxy = proxy.getProxy(helloClientInvoker);

        // ------- client invoke
        Assertions.assertEquals("demo", demoClientProxy.demo());
        Assertions.assertEquals("hello", helloClientProxy.hello());

        this.demoClient = getClient(demoClientInvoker);
        this.helloClient = getClient(helloClientInvoker);
    }

    private Client getClient(Invoker<?> clientInvoker) {
        if (clientInvoker.getURL().getParameter("connections", 1) == 1) {
            return getInvokerClientList(clientInvoker).get(0);
        } else {
            return getReferenceClientList(clientInvoker).get(0);
        }
    }

    private List<Client> getInvokerClientList(Invoker<?> clientInvoker) {
        try {
            AsyncToSyncInvoker asyncToSyncInvoker = (AsyncToSyncInvoker) clientInvoker;
            Field defaultInvokerField = asyncToSyncInvoker.getClass().getDeclaredField("invoker");
            defaultInvokerField.setAccessible(true);
            DefaultInvoker defaultInvoker = (DefaultInvoker) defaultInvokerField.get(asyncToSyncInvoker);
            Field clientsField = defaultInvoker.getClass().getDeclaredField("clients");
            clientsField.setAccessible(true);
            Client[] clients = (Client[]) clientsField.get(defaultInvoker);
            List<Client> clientList = Arrays.asList(clients);
            clientList.sort(Comparator.comparing(client -> Integer.valueOf(client.hashCode())));
            return clientList;
        } catch (Exception e) {

        }
        return null;

    }

    private List<ReferenceCountClient> getReferenceClientList(Invoker<?> invoker) {
        List<Client> invokerClientList = getInvokerClientList(invoker);
        List<ReferenceCountClient> ret = new ArrayList<>();
        for (Client client : invokerClientList) {
            if (client instanceof ReferenceCountClient) {
                ret.add((ReferenceCountClient) client);
            }
        }
        return ret;
    }

    public interface IDemoService {
        String demo();

        int plus(int n1, int n2);
    }

    public interface IHelloService {
        String hello();

        int minus(int n1, int n2);
    }

    public class DemoServiceImpl implements IDemoService {
        public String demo() {
            return "demo";
        }

        @Override
        public int plus(int n1, int n2) {
            return n1 + n2;
        }
    }

    public class HelloServiceImpl implements IHelloService {
        public String hello() {
            return "hello";
        }

        @Override
        public int minus(int n1, int n2) {
            return n1 - n2;
        }
    }

}

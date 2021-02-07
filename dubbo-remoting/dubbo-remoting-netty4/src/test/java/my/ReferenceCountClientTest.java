package my;

import my.common.extension.ExtensionLoader;
import my.common.utils.NetUtils;
import my.rpc.*;
import my.server.Client;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
                "connections=" + connections + "&sharedconnections=" + shareConnections
                + "&heartbeat=600000" + "&timeout=600000");// 这里是我特地加的，避免调试出现心跳、请求响应超时
        URL helloURL = URL.valueOf("dubbo://localhost:" + port + "/hello?" +
                "connections=" + connections + "&sharedconnections=" + shareConnections
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

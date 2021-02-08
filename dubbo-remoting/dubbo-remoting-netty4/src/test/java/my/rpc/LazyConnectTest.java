package my.rpc;

import my.common.extension.ExtensionLoader;
import my.common.rpc.model.ApplicationModel;
import my.common.utils.NetUtils;
import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author geyu
 * @date 2021/2/8 16:29
 */
public class LazyConnectTest {

    Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    @Test
    public void testSticky1() {
        Assertions.assertThrows(RemotingException.class, () -> {
            int port = NetUtils.getAvailablePort();
            URL url = URL.valueOf("default://localhost:" + port + "/test");
            proxyFactory.getProxy(protocol.refer(DemoService.class, url));
        });
    }

    @Test
    public void testSticky2() {
        int port = NetUtils.getAvailablePort();
        URL url = URL.valueOf("default://localhost:" + port + "/test?lazy=true");
        proxyFactory.getProxy(protocol.refer(DemoService.class, url));
    }

    @Test
    public void testSticky3() {
        int port = NetUtils.getAvailablePort();
        URL url = URL.valueOf("default://localhost:" + port + "/test?lazy=true");
        DemoService proxy = proxyFactory.getProxy(protocol.refer(DemoService.class, url));
        Assertions.assertThrows(Exception.class, () -> {
            proxy.echo("haha");
        });
    }


    @Test
    public void testSticky4() throws RemotingException {
        int port = NetUtils.getAvailablePort();
        URL url = URL.valueOf("default://localhost:" + port + "/my.rpc.support.DemoService?lazy=true");
        Invoker<DemoService> invoker = proxyFactory.getInvoker(new DemoServiceImpl(), DemoService.class, url);
        protocol.export(invoker);
        ApplicationModel.getServiceRepository().registerService(DemoService.class);

        // 注意上面注册到仓库的key为接口全限定名，那么就要保证消费者url的path也得是全路径。不然找不到！或者注册的时候提供path

        DemoService proxy = proxyFactory.getProxy(protocol.refer(DemoService.class, url));
        proxy.echo("haha");
    }


}

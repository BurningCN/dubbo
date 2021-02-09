package my.rpc.api.filter;

import my.common.extension.ExtensionLoader;
import my.rpc.Protocol;
import my.rpc.ProxyFactory;
import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Test;

/**
 * @author geyu
 * @date 2021/2/9 18:01
 */
public class TimeoutFilterTest {
    // 原版写的不咋地 并不能体现超时特点。下面是我自己搞得

    @Test
    public void TestInvokeTimeout() throws RemotingException {
        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        URL url = URL.valueOf("default://localhost:9999/test?timeout=6000000");
        protocol.export(proxyFactory.getInvoker(new DemoServiceImpl(), DemoService.class,url));
        DemoService proxy = proxyFactory.getProxy(protocol.refer(DemoService.class, url));
        System.out.println(proxy.timestamp());
        System.out.println(Thread.currentThread().getName());
    }
}

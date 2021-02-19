package my.rpc.api.filter;

import my.common.extension.ExtensionLoader;
import my.rpc.Invocation;
import my.rpc.Protocol;
import my.rpc.ProxyFactory;
import my.rpc.RpcException;
import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.rpc.support.MockInvoker;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author geyu
 * @date 2021/2/19 12:06
 */
public class TpsLimitFilterTest {

    private TpsLimitFilter tpsLimitFilter = new TpsLimitFilter();

    @Test
    public void testInvoke() throws RemotingException, Exception {
        Assertions.assertThrows(RpcException.class, () -> {
            URL url = URL.valueOf("default://localhost:9999/testService?tps=5&tps.interval=10000");
            MockInvoker<DemoService> invoker = new MockInvoker<>(url, DemoService.class);
            for (int i = 0; i < 10; i++) {
                try {
                    tpsLimitFilter.invoke(invoker, Mockito.mock(Invocation.class));
                } catch (RpcException e) {
                    assertTrue(i >= 5);
                    throw e;
                }
            }
        });
    }

    @Test
    public void testByFilterChain() throws RemotingException {
        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        URL url = URL.valueOf("default://localhost:9999/test?timeout=60000&tps=5&tps.interval=10000");
        protocol.export(proxyFactory.getInvoker(new DemoServiceImpl(), DemoService.class, url));
        DemoService proxy = proxyFactory.getProxy(protocol.refer(DemoService.class, url));

        for (int i = 0; i < 10; i++) {
            try {
                proxy.getThreadName();
            } catch (Exception e) {
                assertTrue(i >= 5);
                //throw e;
            }
        }
    }
}

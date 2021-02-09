package my.rpc.api.filter;

import my.common.extension.ExtensionLoader;
import my.rpc.*;
import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author geyu
 * @date 2021/2/9 20:26
 */

public class TokenFilterTest {

    @Test
    public void testInvokeWithToken() throws RemotingException, Exception {
        TokenFilter tokenFilter = new TokenFilter();
        String token = "123123123123213123123212312";
        URL url = URL.valueOf("test://test:11/test?group=gggg&version=1.1&token=" + token);
        Invoker invoker = Mockito.mock(Invoker.class);
        when(invoker.getURL()).thenReturn(url);
        when(invoker.invoke(any(Invocation.class))).thenReturn(new AppResponse("nihao"));

        Invocation invocation = Mockito.mock(Invocation.class);
        when(invocation.getObjectAttachment("token")).thenReturn(token);
        Result result = tokenFilter.invoke(invoker, invocation);
        Assertions.assertTrue(result.getValue().equals("nihao"));

    }

    @Test
    public void testInvokeWithWrongToken() throws RemotingException, Exception {
        TokenFilter tokenFilter = new TokenFilter();
        String token = "123123123123213123123212312";
        URL url = URL.valueOf("test://test:11/test?group=gggg&version=1.1&token=" + token);
        Invoker invoker = Mockito.mock(Invoker.class);
        when(invoker.getURL()).thenReturn(url);
        when(invoker.invoke(any(Invocation.class))).thenReturn(new AppResponse("nihao"));

        Invocation invocation = Mockito.mock(Invocation.class);
        when(invocation.getObjectAttachment("token")).thenReturn(token + "error");
        Assertions.assertThrows(RpcException.class, () -> {
            tokenFilter.invoke(invoker, invocation);
        });
    }

    @Test
    public void testByFilterChain() throws RemotingException {
        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        URL url = URL.valueOf("default://localhost:9999/test?timeout=6000000&token=2131231232132132"); // 有token参数才会激活TokenFilter
        protocol.export(proxyFactory.getInvoker(new DemoServiceImpl(), DemoService.class,url));
        DemoService proxy = proxyFactory.getProxy(protocol.refer(DemoService.class, url));
        Assertions.assertThrows(RpcException.class, () -> {
            proxy.timestamp();
        });
    }


}

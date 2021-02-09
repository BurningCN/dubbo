package my.rpc;

import my.common.extension.ExtensionLoader;
import my.common.rpc.model.ApplicationModel;
import my.common.service.EchoService;
import my.common.utils.NetUtils;
import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author geyu
 * @date 2021/2/9 11:31
 */
public class TestEchoFilter {

     private Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    private ProxyFactory proxy = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    @Test
    public void testRpcFilter() throws Exception, RemotingException {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        URL url = URL.valueOf("default://127.0.0.1:" + port + "/my.rpc.support.DemoService?service.filter=echo");// 后面的参数对加不加无所谓
        ApplicationModel.getServiceRepository().registerService(DemoService.class);

        // 再说一下 比如 url = "xxxx://ip:port/test?..."如果注册的时候(即上面registerService)没有指定test这个path，会抛异常，因为如果发现"有参数"的调用，
        // 在DecodeableRpcInvocation内部会从ApplicationModel.getServiceRepository().lookUp(path)，而你没指定，默认的path就是接口全限定名称
        // 就抛异常了。如果非带有参数的调用，/xxx随便写，也不需要registerService，直接就能调用成功，因为不会走DecodeableRpcInvocation内部那个逻辑
        // 直接到requestHandler的reply....不说了



        protocol.export(proxy.getInvoker(service, DemoService.class, url));
        service = proxy.getProxy(protocol.refer(DemoService.class, url));
        Assertions.assertEquals("123", service.echo("123"));
        // cast to EchoService
        EchoService echo = proxy.getProxy(protocol.refer(EchoService.class, url));
        Assertions.assertEquals(echo.$echo("test"), "test");
        Assertions.assertEquals(echo.$echo("abcdefg"), "abcdefg");
        Assertions.assertEquals(echo.$echo(1234), 1234);
    }
}

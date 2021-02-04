package my.rpc;

import my.common.extension.Adaptive;
import my.common.extension.SPI;
import my.server.Constants;
import my.server.URL;
import org.apache.dubbo.rpc.RpcException;


/**
 * @author geyu
 * @date 2021/2/4 15:37
 */
@SPI("javassist")
public interface ProxyFactory {

    @Adaptive({Constants.PROXY_KEY})
    <T> T getProxy(Invoker<T> invoker);

    @Adaptive({Constants.PROXY_KEY})
    <T> T getProxy(Invoker<T> invoker, boolean generic) ;

    @Adaptive({Constants.PROXY_KEY})
    <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url);
}

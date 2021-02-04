package my.rpc;


import my.common.bytecode.Proxy;
import my.common.bytecode.Wrapper;
import my.server.URL;
import java.lang.reflect.InvocationTargetException;

/**
 * @author geyu
 * @date 2021/2/4 15:40
 */
public class JavassistProxyFactory extends AbstractProxyFactory {
    @Override
    protected <T> T doGetProxy(Invoker<T> invoker, Class[] toArray) {
        return (T) Proxy.getProxy(toArray).newInstance(new InvokeInvocationHandler(invoker));
    }

    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        Wrapper wrapper = Wrapper.getWrapper(proxy.getClass());
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(Object proxy, String methodName, Class[] parameterTypes, Object[] arguments) throws InvocationTargetException {
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }

}

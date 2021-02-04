package my.rpc;

import my.server.URL;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author geyu
 * @date 2021/2/4 18:19
 */
public class JdkProxyFactory extends AbstractProxyFactory {
    @Override
    protected <T> T doGetProxy(Invoker<T> invoker, Class[] toArray) {
        return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), toArray, new InvokeInvocationHandler(invoker));
    }

    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName, Class<?>[] parameterTypes, Object[] arguments) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
                Method method = proxy.getClass().getMethod(methodName);
                return method.invoke(parameterTypes, arguments);
            }
        };
    }
}

package my.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @author geyu
 * @date 2021/2/4 19:08
 */
public class InvokeInvocationHandler implements InvocationHandler {
    public <T> InvokeInvocationHandler(Invoker<T> invoker) {
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return null;
    }
}

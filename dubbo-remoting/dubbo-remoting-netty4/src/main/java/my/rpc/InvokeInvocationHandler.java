package my.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @author geyu
 * @date 2021/2/4 19:08
 */
public class InvokeInvocationHandler implements InvocationHandler {
    private final Invoker<?> invoker;
    private final String protocolServiceKey;
    private final String serviceKey;

    public InvokeInvocationHandler(Invoker<?> invoker) {
        this.invoker = invoker;
        this.serviceKey = GroupServiceKeyCache.serviceKey(invoker.getURL());
        this.protocolServiceKey = serviceKey + ":" + invoker.getURL().getProtocol();
        // todo myRPC consumerModel
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            method.invoke(invoker, args);
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0) {
            if ("toString".equals(method.getName())) {
                return invoker.toString();
            } else if ("hashCode".equals(method.getName())) {
                return invoker.hashCode();
            } else if ("$destroy".equals(method.getName())) {
                invoker.destroy();
            }
        } else if (parameterTypes.length == 1 && "equals".equals(method.getName())) {
            return invoker.equals(args[0]);
        }

        RpcInvocation rpcInvocation = new RpcInvocation
                (method, invoker.getInterface().getName(), protocolServiceKey, args);
        rpcInvocation.setTargetServiceUniqueName(serviceKey);

        // todo myRPC consumerModel
        RpcContext.setRpcContext(invoker.getURL());
        return invoker.invoke(rpcInvocation).recreate();
    }
}

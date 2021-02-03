package my.server.support;

import my.server.InnerChannel;
import my.server.Replier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author geyu
 * @date 2021/2/2 20:10
 */
public class RpcMessageReplier implements Replier<RpcMessage> {

    private static ServiceProvider serviceProvider = service -> {
        String impl = service + "Impl";
        try {
            Class<?> aClass = Thread.currentThread().getContextClassLoader().loadClass(impl);
            return aClass.newInstance();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    };


    @Override
    public Object reply(InnerChannel channel, RpcMessage request) {
        Object impl = serviceProvider.getImpl(request.getServiceName());
        // todo myRPC 这里需要改成Wrapper 暂时先用反射代替
        MockResult mockResult = null;
        try {
            Method method = impl.getClass().getMethod(request.getMethodName(), request.getParameterTypes());
            Object result = method.invoke(impl, request.getArguments());
            mockResult = new MockResult(result);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return mockResult;
    }

    interface ServiceProvider {
        Object getImpl(String service);
    }
}

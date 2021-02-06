package my.rpc;

import my.server.Constants;
import my.server.URL;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

import static my.rpc.Constants.RETURN_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * @author geyu
 * @date 2021/2/4 14:48
 */
public class RpcUtils {

    public static InvokeMode getInvokeMode(Invocation inv) {
        InvokeMode invokeMode = null;
        if (inv instanceof RpcInvocation) {
            RpcInvocation rpcInvocation = (RpcInvocation) inv;
            invokeMode = rpcInvocation.getInvokeMode();
            if (invokeMode != null) {
                return invokeMode;
            }
            Class<?> returnType = rpcInvocation.getReturnType();
            if (returnType != null && CompletableFuture.class.isAssignableFrom(returnType)) {
                invokeMode = InvokeMode.FUTURE;
            } else if (Boolean.TRUE.toString().equals(rpcInvocation.getAttachment(Constants.ASYNC_KEY))) {
                invokeMode = InvokeMode.ASYNC;
            } else {
                invokeMode = InvokeMode.SYNC;
            }
        }
        return invokeMode;
    }

    public static void attachInvocationIdIfAsync(URL url, RpcInvocation invocation) {
    }

    public static Type[] getReturnTypes(RpcInvocation rpcInvocation) {
        return null;
    }

    public static boolean isOneway(URL url, Invocation inv) {
        boolean isOneway;
        if (Boolean.FALSE.toString().equals(inv.getAttachment(RETURN_KEY))) {
            isOneway = true;
        } else {
            isOneway = !url.getMethodParameter(getMethodName(inv), RETURN_KEY, true);
        }
        return isOneway;
    }

    public static String getMethodName(Invocation invocation) {
        if ("$invoke".equals(invocation.getMethodName())
                && invocation.getArguments() != null
                && invocation.getArguments().length > 0
                // getArguments()[0]是方法名称:String
                // getArguments()[1]是传给方法的参数类型数组:String[]
                // getArguments()[2]是传给方法的参数值数组:Object[]
                // 这里取方法名，肯定是String类型
                && invocation.getArguments()[0] instanceof String) {
            return (String) invocation.getArguments()[0];
        }
        return invocation.getMethodName();
    }

    public static long getTimeout(URL url, String methodName, RpcContext context, long defaultTimeout) {
        long timeout = defaultTimeout;
        Object genericTimeout = context.getObjectAttachment(TIMEOUT_KEY);
        if (genericTimeout != null) {
            timeout = convertToLongNumber(genericTimeout);
        } else {
            timeout = url.getMethodPositiveParameter(methodName, TIMEOUT_KEY, defaultTimeout);
        }
        return timeout;
    }

    private static long convertToLongNumber(Object obj) {
        if (obj instanceof String) {
            return Long.parseLong((String) obj);
        } else if (obj instanceof Number) {
            return ((Number) obj).longValue();
        } else {
            return Long.parseLong(obj.toString());
        }
    }

    public static boolean isGenericCall(String desc, String methodName) {
        return false;
    }

    public static boolean isEcho(String desc, String methodName) {
        return false;
    }
}

package my.rpc;

import my.server.Constants;
import my.server.URL;

import java.util.concurrent.CompletableFuture;

/**
 * @author geyu
 * @date 2021/2/4 14:48
 */
public class RpcUtil {

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
}

package my.rpc;

import java.util.Map;

/**
 * @author geyu
 * @date 2021/2/4 14:37
 */
public class RpcContext {
    // todo myRPC InternalThreadLocal
    private static final ThreadLocal<RpcContext> LOCAL = ThreadLocal.withInitial(() -> new RpcContext());
    private static final ThreadLocal<RpcContext> SERVER_LOCAL = ThreadLocal.withInitial(() -> new RpcContext());
    private Map<String, Object> attachments;

    public static RpcContext getContext() {
        return LOCAL.get();
    }

    public static  RpcContext getServerContext() {
        return SERVER_LOCAL.get();
    }

    public Map<String, Object> getAttachments() {
        return attachments;
    }

    public boolean isAsyncStarted() {
        return false;
    }
}



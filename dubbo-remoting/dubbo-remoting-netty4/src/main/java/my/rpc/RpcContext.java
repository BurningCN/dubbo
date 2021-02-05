package my.rpc;

import my.server.URL;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author geyu
 * @date 2021/2/4 14:37
 */
public class RpcContext {
    // todo myRPC InternalThreadLocal
    private static final ThreadLocal<RpcContext> LOCAL = ThreadLocal.withInitial(() -> new RpcContext());
    private static final ThreadLocal<RpcContext> SERVER_LOCAL = ThreadLocal.withInitial(() -> new RpcContext());
    private Map<String, Object> attachments = new HashMap<>();
    private final Map<String, Object> values = new HashMap<String, Object>();

    private URL consumerUrl;

    public static RpcContext getContext() {
        return LOCAL.get();
    }

    public static RpcContext getServerContext() {
        return SERVER_LOCAL.get();
    }

    public Object get(String key) {
        return values.get(key);
    }

    public static void setRpcContext(URL url) {
        getContext().setConsumerUrl(url);
    }

    public void setConsumerUrl(URL consumerUrl) {
        this.consumerUrl = consumerUrl;
    }

    public Map<String, Object> getAttachments() {
        return attachments;
    }

    public boolean isAsyncStarted() {
        return false;
    }

    public void setFuture(CompletableFuture<?> future) {
        FutureContext.getContext().setFuture(future);
    }

    public <T> Future<T> getFuture() {
        return FutureContext.getContext().getFuture();
    }

    public Object getObjectAttachment(String key) {
        return attachments.get(key);
    }
}



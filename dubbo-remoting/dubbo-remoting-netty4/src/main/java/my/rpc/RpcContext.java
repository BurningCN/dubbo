package my.rpc;

import my.rpc.api.AsyncContext;
import my.rpc.api.AsyncContextImpl;
import my.server.URL;
import org.apache.dubbo.rpc.RpcException;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.apache.dubbo.rpc.Constants.ASYNC_KEY;

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
    private boolean remove = true;
    private URL consumerUrl;
    private InetSocketAddress localAddress;
    private InetSocketAddress remoteAddress;
    private URL url;
    private Map<? extends String, ?> attachment;
    private AsyncContext asyncContext;

    public static RpcContext getContext() {
        return LOCAL.get();
    }

    public static RpcContext getServerContext() {
        return SERVER_LOCAL.get();
    }

    public static void removeContext() {
        removeContext(false);
    }

    private static void removeContext(boolean checkCanRemove) {
        if (LOCAL.get().canRemove()) {
            LOCAL.remove();
        }
    }

    public static void removeServerContext() {
        SERVER_LOCAL.remove();
    }

    private boolean canRemove() {
        return remove;
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
        if (asyncContext == null) {
            return false;
        }
        return asyncContext.isAsyncStarted();
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

    public RpcContext setLocalAddress(String host, int port) {
        if (port < 0) {
            port = 0;
        }
        this.localAddress = InetSocketAddress.createUnresolved(host, port);
        return this;
    }

    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    public RpcContext setRemoteAddress(String host, int port) {
        if (port < 0) {
            port = 0;
        }
        this.remoteAddress = new InetSocketAddress(host, port);// 两种创建，和上面的createUnresolved
        return this;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }

    public boolean isConsumerSide() {
        return getUrl().getParameter("side", "provider").equals("consumer");
    }

    public boolean isProviderSide() {
        return !isConsumerSide();
    }

    public RpcContext setObjectAttachments(Map<String, Object> attachment) {
        this.attachments.clear();
        if (attachment != null && attachment.size() > 0) {
            this.attachments.putAll(attachment);
        }
        return this;
    }

    private void setObjectAttachment(String key, String val) {
        if (attachments == null) {
            attachments = new HashMap<>();
        }
        this.attachments.put(key, val);
    }

    private void removeObjectAttachment(String key) {
        if (attachments == null) return;
        this.attachments.remove(key);
    }


    public Map<? extends String, ?> getAttachment() {
        return attachment;
    }

    public void clearObjectAttachments() {
        this.attachments.clear();
    }

    public RpcContext set(String key, Object value) {
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
        return this;
    }

    public RpcContext remove(String key) {
        values.remove(key);
        return this;
    }

    public Object get(String key) {
        return values.get(key);
    }

    public Map<String, Object> get() {
        return values;
    }

    public static AsyncContext startAsync() {
        RpcContext context = getContext();
        if (context.asyncContext == null) {
            context.asyncContext = new AsyncContextImpl();
        }
        context.asyncContext.start();
        return context.asyncContext;
    }

    public static boolean stopAsync() {
        RpcContext context = getContext();
        if (context.asyncContext == null) {
            return true;
        }
        return context.asyncContext.stop();
    }

    public <T> CompletableFuture<T> asyncCall(Callable<T> callable) {
        try {
            try {
                setObjectAttachment("async", Boolean.TRUE.toString());
                T call = callable.call();
                if (call != null) {
                    if (call instanceof CompletableFuture) {
                        return (CompletableFuture) call;
                    }
                    return CompletableFuture.completedFuture(call);
                }
                setObjectAttachment("async", Boolean.FALSE.toString());
            } catch (Exception e) {
                throw new RpcException(e);
            } finally {
                removeObjectAttachment(ASYNC_KEY);
            }
        } catch (RpcException e) {
            CompletableFuture<T> exceptionFuture = new CompletableFuture<>();
            exceptionFuture.completeExceptionally(e);
            return exceptionFuture;
        }
        return (CompletableFuture<T>) getContext().getFuture();
    }


}



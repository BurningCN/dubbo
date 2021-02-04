package my.rpc;

import my.common.utils.ArrayUtils;
import my.common.utils.NetUtils;
import my.server.URL;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author geyu
 * @date 2021/2/4 13:56
 */
public abstract class AbstractInvoker<T> implements Invoker<T> {

    private final Class<T> type;
    private final URL url;
    private Map<String, Object> attachments;
    private AtomicBoolean destroyed = new AtomicBoolean(false);
    private volatile boolean available = true;

    public AbstractInvoker(Class<T> type, URL url) {
        this(type, url, (Map<String, Object>) null);
    }

    public AbstractInvoker(Class<T> type, URL url, Map<String, Object> attachments) {
        this.type = type;
        this.url = url;
        this.attachments = Collections.unmodifiableMap(attachments);
    }

    public AbstractInvoker(Class<T> type, URL url, String[] keys) {
        this.type = type;
        this.url = url;
        if (!ArrayUtils.isNotEmpty(keys)) {
            attachments = new HashMap<>();
            for (String key : keys) {
                String value = url.getParameter(key);
                if (value != null && value.length() > 0) {
                    attachments.put(key, value);
                }
            }
        }
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public void destroy() { // 防止并发关闭
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }
        setAvailable(false);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    protected void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public Class<T> getInterface() {
        return type;
    }

    @Override
    public Result invoke(Invocation inv) {
        if (destroyed.get()) {
            System.out.println("Invoker for service " + this + " on consumer " + NetUtils.getLocalHost() + " is destroyed,  this invoker should not be used any longer");
        }
        RpcInvocation invocation = (RpcInvocation) inv;
        invocation.setInvoker(this); // 1
        if (CollectionUtils.isNotEmptyMap(attachments)) {
            invocation.addAttachmentsIfAbsent(attachments); // 2
        }
        Map<String, Object> rpcAttachments = RpcContext.getContext().getAttachments();
        if (CollectionUtils.isNotEmptyMap(rpcAttachments)) {
            invocation.addAttachments(rpcAttachments); // 2
        }
        invocation.setInvokeMode(RpcUtil.getInvokeMode(invocation)); // 3

        return null;
    }
}

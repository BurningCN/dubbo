package my.rpc;

import my.common.utils.ArrayUtils;
import my.common.utils.NetUtils;
import my.server.RemotingException;
import my.server.URL;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static my.common.constants.CommonConstants.*;
import static my.common.constants.CommonConstants.TIMEOUT_KEY;

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
    public Result invoke(Invocation inv) throws RemotingException {
        if (destroyed.get()) {
            System.out.println("Invoker for service " + this + " on consumer " + NetUtils.getLocalHost() + " is destroyed,  this invoker should not be used any longer");
        }
        RpcInvocation invocation = (RpcInvocation) inv;
        invocation.setInvoker(this); // 1
        if (CollectionUtils.isNotEmptyMap(attachments)) {
            invocation.addObjectAttachmentsIfAbsent(attachments); // 2
        }
        Map<String, Object> rpcAttachments = RpcContext.getContext().getAttachments();
        if (CollectionUtils.isNotEmptyMap(rpcAttachments)) {
            invocation.addObjectAttachments(rpcAttachments); // 2
        }
        invocation.setInvokeMode(RpcUtils.getInvokeMode(invocation)); // 3

        RpcUtils.attachInvocationIdIfAsync(url, invocation);

        inv.setAttachment(PATH_KEY, getURL().getPath()); // 这部分内容本来是子类的， 我挪到这里了，为了统一在一处处理
        inv.setAttachment(VERSION_KEY, getURL().getParameter(VERSION_KEY, "0.0.0"));
        String methodName = RpcUtils.getMethodName(invocation);
        int timeout = calculateTimeout(invocation, methodName);
        invocation.setObjectAttachment(TIMEOUT_ATTACHMENT_KEY, timeout);
        invocation.put(TIMEOUT_KEY, timeout);

        /// todo myRPC 异常体系

        AsyncRpcResult asyncResult = (AsyncRpcResult) doInvoke(invocation);
        RpcContext.getContext().setFuture(new FutureAdapter(asyncResult.getResponseFuture()));
        return asyncResult;
    }

    protected abstract Result doInvoke(Invocation invocation) throws RemotingException;

    protected ExecutorService getCallbackExecutor(URL url, Invocation inv) {
//        ExecutorService sharedExecutor = ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension().getExecutor(url);
//        if (InvokeMode.SYNC == RpcUtils.getInvokeMode(inv)) {
//            return new ThreadlessExecutor(sharedExecutor);
//        } else {
//            return sharedExecutor;
//        }
        return null;
    }

    private int calculateTimeout(Invocation invocation, String methodName) {
        Object countdown = RpcContext.getContext().get(TIME_COUNTDOWN_KEY);
        int timeout = DEFAULT_TIMEOUT;
        if (countdown == null) {
            timeout = (int) RpcUtils.getTimeout(getURL(), methodName, RpcContext.getContext(), DEFAULT_TIMEOUT);
            if (getURL().getParameter(ENABLE_TIMEOUT_COUNTDOWN_KEY, false)) {
                invocation.setObjectAttachment(TIMEOUT_ATTACHMENT_KEY, timeout); // pass timeout to remote server
            }
        } else {
            TimeoutCountDown timeoutCountDown = (TimeoutCountDown) countdown;
            timeout = (int) timeoutCountDown.timeRemaining(TimeUnit.MILLISECONDS);
            invocation.setObjectAttachment(TIMEOUT_ATTACHMENT_KEY, timeout);// pass timeout to remote server
        }
        return timeout;
    }
}

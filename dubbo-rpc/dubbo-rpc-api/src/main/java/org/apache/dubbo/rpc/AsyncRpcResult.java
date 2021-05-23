/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.ThreadlessExecutor;
import org.apache.dubbo.rpc.model.ConsumerMethodModel;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.apache.dubbo.common.utils.ReflectUtils.defaultReturn;

/**
 * This class represents an unfinished RPC call, it will hold some context information for this call, for example RpcContext and Invocation,
 * so that when the call finishes and the result returns, it can guarantee all the contexts being recovered as the same as when the call was made
 * before any callback is invoked.
 * <p>
 * TODO if it's reasonable or even right to keep a reference to Invocation?
 * <p>
 * As {@link Result} implements CompletionStage, {@link AsyncRpcResult} allows you to easily build a async filter chain whose status will be
 * driven entirely by the state of the underlying RPC call.
 * <p>
 * AsyncRpcResult does not contain any concrete value (except the underlying value bring by CompletableFuture), consider it as a status transfer node.
 * {@link #getValue()} and {@link #getException()} are all inherited from {@link Result} interface, implementing them are mainly
 * for compatibility consideration. Because many legacy {@link Filter} implementation are most possibly to call getValue directly.
 * <p>
 * 这个类代表一个未完成的RPC调用，它将为这个调用保留一些上下文信息，例如RpcContext和Invocation，当
 * 这样，调用结束并返回结果时，就可以保证在任何回调调用之前恢复的所有上下文与调用时一样
 * <p>
 * 如果保持对调用的引用是合理的，甚至是正确的，那么应该做什么呢?
 * <p>
 * 由于{@link Result}实现了CompletionStage， {@link AsyncRpcResult}允许你轻松地构建一个状态为的异步过滤链
 * 完全由底层RPC调用的状态驱动。
 * <p>
 * AsyncRpcResult不包含任何具体的值(除了CompletableFuture带来的底层值)，将其视为状态转移节点。
 * {@link #getValue()}和{@link #getException()}都继承自{@link Result}接口，它们的实现主要是
 * 为了兼容性的考虑。因为许多遗留的{@link Filter}实现最有可能直接调用getValue。
 * /
 */
// OK
public class AsyncRpcResult implements Result {
    private static final Logger logger = LoggerFactory.getLogger(AsyncRpcResult.class);

    /**
     * RpcContext may already have been changed when callback happens, it happens when the same thread is used to execute another RPC call.
     * So we should keep the reference of current RpcContext instance and restore it before callback being executed.
     * <p>
     * 当回调发生时，RpcContext可能已经被改变，它发生在同一个线程被用来执行另一个RPC调用。
     * 因此，我们应该保留当前RpcContext实例的引用，并在回调执行之前恢复它。
     */
    private RpcContext storedContext;
    private RpcContext storedServerContext;
    private Executor executor;

    private Invocation invocation;

    private CompletableFuture<AppResponse> responseFuture;

    public AsyncRpcResult(CompletableFuture<AppResponse> future, Invocation invocation) {
        this.responseFuture = future;
        this.invocation = invocation;
        this.storedContext = RpcContext.getContext();
        this.storedServerContext = RpcContext.getServerContext();
    }

    /**
     * Notice the return type of {@link #getValue} is the actual type of the RPC method, not {@link AppResponse}
     *
     * @return
     */
    @Override
    public Object getValue() {
        return getAppResponse().getValue();
    }

    /**
     * CompletableFuture can only be completed once, so try to update the result of one completed CompletableFuture will
     * has no effect. To avoid this problem, we check the complete status of this future before update it's value.
     * <p>
     * But notice that trying to give an uncompleted CompletableFuture a new specified value may face a race condition,
     * because the background thread watching the real result will also change the status of this CompletableFuture.
     * The result is you may lose the value you expected to set.
     * <p>
     * CompletableFuture只能完成一次，所以尝试更新一个completed CompletableFuture will的结果
     * 没有效果。为了避免这个问题，我们在更新它的值之前检查这个future的完整状态。
     * <p>
     * 但是请注意，尝试给一个未完成的CompletableFuture一个新的指定值可能会面临一个竞争条件，
     * 因为后台线程观看真实的结果也会改变这个完整的未来的状态。
     * 结果是您可能会失去期望设置的值。
     *
     * @param value
     */
    @Override
    public void setValue(Object value) {
        try {
            // 下两个分支对应上面两段话

            if (responseFuture.isDone()) {
                responseFuture.get().setValue(value);
            } else {
                AppResponse appResponse = new AppResponse();
                appResponse.setValue(value);
                responseFuture.complete(appResponse);
            }
        } catch (Exception e) {
            // This should not happen in normal request process;
            logger.error("Got exception when trying to fetch the underlying result from AsyncRpcResult.");
            throw new RpcException(e);
        }
    }

    @Override
    public Throwable getException() {
        return getAppResponse().getException();
    }

    // 参考前面setValue
    @Override
    public void setException(Throwable t) {
        try {
            if (responseFuture.isDone()) {
                responseFuture.get().setException(t);
            } else {
                AppResponse appResponse = new AppResponse();
                appResponse.setException(t); // 这里是setException
                responseFuture.complete(appResponse);
            }
        } catch (Exception e) {
            // This should not happen in normal request process;
            logger.error("Got exception when trying to fetch the underlying result from AsyncRpcResult.");
            throw new RpcException(e);
        }
    }

    @Override
    public boolean hasException() {
        return getAppResponse().hasException();
    }

    public CompletableFuture<AppResponse> getResponseFuture() {
        return responseFuture;
    }

    public void setResponseFuture(CompletableFuture<AppResponse> responseFuture) {
        this.responseFuture = responseFuture;
    }

    public Result getAppResponse() {
        try {
            // easy
            if (responseFuture.isDone()) {
                // 返回AppResponse
                return responseFuture.get();
            }
        } catch (Exception e) {
            // This should not happen in normal request process;
            logger.error("Got exception when trying to fetch the underlying result from AsyncRpcResult.");
            throw new RpcException(e);
        }

        // 进去
        return createDefaultValue(invocation);
    }

    /**
     * This method will always return after a maximum 'timeout' waiting:
     * 1. if value returns before timeout, return normally.
     * 2. if no value returns after timeout, throw TimeoutException.
     * 此方法将始终在最大'timeout'等待之后返回:
     * 1. 如果value在超时前返回，则正常返回。
     * 2. 如果timeout之后没有返回值，则抛出TimeoutException。
     *
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    // ThreadlessExecutor 注意去看下官方md文档，消费端拿到结果后旧版本是交给消费端的线程池，但是对线程池有影响，所以有了这个，相比于老的线程池模型，由业务线程自己负责监测并解析返回结果，免去了额外的消费端线程池开销。
    @Override
    public Result get() throws InterruptedException, ExecutionException {
        if (executor != null && executor instanceof ThreadlessExecutor) {
            ThreadlessExecutor threadlessExecutor = (ThreadlessExecutor) executor;
            // 进去
            threadlessExecutor.waitAndDrain();
        }
        return responseFuture.get();
    }

    @Override
    public Result get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (executor != null && executor instanceof ThreadlessExecutor) {
            ThreadlessExecutor threadlessExecutor = (ThreadlessExecutor) executor;
            threadlessExecutor.waitAndDrain();
        }
        return responseFuture.get(timeout, unit);
    }

    @Override
    public Object recreate() throws Throwable {
        // invocation在invoker的invoke方法通过该类的构造方法传参过来赋值的
        RpcInvocation rpcInvocation = (RpcInvocation) invocation;
        // 注意RpcContext.getContext().setFuture()的调用点
        if (InvokeMode.FUTURE == rpcInvocation.getInvokeMode()) {
            return RpcContext.getContext().getFuture();
        }

        // 两个方法都进去，recreate是AppResponse的方法
        return getAppResponse().recreate();
    }

    public Result whenCompleteWithContext(BiConsumer<Result, Throwable> fn) {
        // whenComplete在结果完成的时候会触发内部逻辑（v是responseFuture的值，去看下
        this.responseFuture = this.responseFuture.whenComplete((v, t) -> {
            // 去看下beforeContext、afterContext
            beforeContext.accept(v, t);
            fn.accept(v, t);
            afterContext.accept(v, t);
        });
        return this;
    }


    @Override
    public <U> CompletableFuture<U> thenApply(Function<Result, ? extends U> fn) {
        return this.responseFuture.thenApply(fn);
    }

    @Override
    @Deprecated
    public Map<String, String> getAttachments() {
        return getAppResponse().getAttachments();
    }

    @Override
    public Map<String, Object> getObjectAttachments() {
        return getAppResponse().getObjectAttachments();
    }

    @Override
    public void setAttachments(Map<String, String> map) {
        getAppResponse().setAttachments(map);
    }

    @Override
    public void setObjectAttachments(Map<String, Object> map) {
        getAppResponse().setObjectAttachments(map);
    }

    @Deprecated
    @Override
    public void addAttachments(Map<String, String> map) {
        getAppResponse().addAttachments(map);
    }

    @Override
    public void addObjectAttachments(Map<String, Object> map) {
        getAppResponse().addObjectAttachments(map);
    }

    @Override
    public String getAttachment(String key) {
        return getAppResponse().getAttachment(key);
    }

    @Override
    public Object getObjectAttachment(String key) {
        return getAppResponse().getObjectAttachment(key);
    }

    @Override
    public String getAttachment(String key, String defaultValue) {
        return getAppResponse().getAttachment(key, defaultValue);
    }

    @Override
    public Object getObjectAttachment(String key, Object defaultValue) {
        return getAppResponse().getObjectAttachment(key, defaultValue);
    }

    @Override
    public void setAttachment(String key, String value) {
        setObjectAttachment(key, value);
    }

    @Override
    public void setAttachment(String key, Object value) {
        setObjectAttachment(key, value);
    }

    @Override
    public void setObjectAttachment(String key, Object value) {
        getAppResponse().setAttachment(key, value);
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /**
     * tmp context to use when the thread switch to Dubbo thread.
     */
    private RpcContext tmpContext;
    private RpcContext tmpServerContext;

    private BiConsumer<Result, Throwable> beforeContext = (appResponse, t) -> {
        // 取出来保存到临时变量
        tmpContext = RpcContext.getContext();
        tmpServerContext = RpcContext.getServerContext();
        // 设置新的
        RpcContext.restoreContext(storedContext);
        RpcContext.restoreServerContext(storedServerContext);
    };

    private BiConsumer<Result, Throwable> afterContext = (appResponse, t) -> {
        // 还原
        RpcContext.restoreContext(tmpContext);
        RpcContext.restoreServerContext(tmpServerContext);
    };

    /**
     * Some utility methods used to quickly generate default AsyncRpcResult instance.
     */
    public static AsyncRpcResult newDefaultAsyncResult(AppResponse appResponse, Invocation invocation) {
        return new AsyncRpcResult(CompletableFuture.completedFuture(appResponse), invocation);
    }

    public static AsyncRpcResult newDefaultAsyncResult(Invocation invocation) {
        return newDefaultAsyncResult(null, null, invocation);
    }

    public static AsyncRpcResult newDefaultAsyncResult(Object value, Invocation invocation) {
        // 进去
        return newDefaultAsyncResult(value, null, invocation);
    }

    public static AsyncRpcResult newDefaultAsyncResult(Throwable t, Invocation invocation) {
        return newDefaultAsyncResult(null, t, invocation);
    }

    public static AsyncRpcResult newDefaultAsyncResult(Object value, Throwable t, Invocation invocation) {
        CompletableFuture<AppResponse> future = new CompletableFuture<>();
        AppResponse result = new AppResponse();
        if (t != null) {
            result.setException(t);
        } else {
            result.setValue(value);
        }
        future.complete(result);
        // 一定要注意这里，AsyncRpcResult 包装了 AppResponse（通过CompletableFuture的方式）
        return new AsyncRpcResult(future, invocation);
    }

    private static Result createDefaultValue(Invocation invocation) {
        ConsumerMethodModel method = (ConsumerMethodModel) invocation.get(Constants.METHOD_MODEL);
        return method != null ? new AppResponse(defaultReturn(method.getReturnClass())) : new AppResponse();
    }
}


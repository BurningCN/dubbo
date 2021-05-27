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
package org.apache.dubbo.rpc.proxy;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.AsyncContextImpl;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * This Invoker works on provider side, delegates RPC to interface implementation.
 */
// OK
// 看上面的描述，非常重要！！！这个是服务端的某个接口实现类的靠根源的地方了，而AbstractInvoker是给消费端使用的，注意这两个类区别
public abstract class AbstractProxyInvoker<T> implements Invoker<T> {
    Logger logger = LoggerFactory.getLogger(AbstractProxyInvoker.class);

    private final T proxy;

    private final Class<T> type;

    private final URL url;

    // gx
    public AbstractProxyInvoker(T proxy, Class<T> type, URL url) {
        if (proxy == null) {
            throw new IllegalArgumentException("proxy == null");
        }
        if (type == null) {
            throw new IllegalArgumentException("interface == null");
        }
        if (!type.isInstance(proxy)) {
            throw new IllegalArgumentException(proxy.getClass().getName() + " not implement interface " + type);
        }
        // 实现类，比如DemoServiceImpl
        this.proxy = proxy;
        // 接口，比如DemoService
        this.type = type;
        this.url = url;
    }

    @Override
    public Class<T> getInterface() {
        return type;
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void destroy() {
    }

    @Override
    public Result invoke(Invocation invocation) throws RpcException {
        try {
            // 这里的proxy其实是目标对象，根据invocation的信息调用目标方法
            Object value = doInvoke(proxy, invocation.getMethodName(), invocation.getParameterTypes(), invocation.getArguments());
			CompletableFuture<Object> future = wrapWithFuture(value);
			//whenComplete接收的是BiConsumer,handler接收的是BiFunction;
            //顾名思义,BiConsumer是直接消费的,而BiFunction是有返回值的
            CompletableFuture<AppResponse> appResponseFuture = future.handle((obj, t) -> {
                AppResponse result = new AppResponse();
                if (t != null) {
                    if (t instanceof CompletionException) {
                        result.setException(t.getCause());
                    } else {
                        result.setException(t);
                    }
                } else {
                    result.setValue(obj);
                }
                return result;
            });
            return new AsyncRpcResult(appResponseFuture, invocation);
        } catch (InvocationTargetException e) {
            if (RpcContext.getContext().isAsyncStarted() && !RpcContext.getContext().stopAsync()) {
                logger.error("Provider async started, but got an exception from the original method, cannot write the exception back to consumer because an async result may have returned the new thread.", e);
            }
            return AsyncRpcResult.newDefaultAsyncResult(null, e.getTargetException(), invocation);
        } catch (Throwable e) {
            throw new RpcException("Failed to invoke remote proxy method " + invocation.getMethodName() + " to " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

	private CompletableFuture<Object> wrapWithFuture(Object value) {
        if (RpcContext.getContext().isAsyncStarted()) {
            // 如果是异步(开始)的，那么直接返回先前的AsyncContext实例
            // 这个异步适用于provider，怎么应用的，请看dubbo-samples的async-provider测试用例 截取部分如下
            // @Override
            //    public String sayHello(String name) {
            //        AsyncContext asyncContext = RpcContext.startAsync();
            //        logger.info("sayHello start");
            //
            //        new Thread(() -> {
            //            asyncContext.signalContextSwitch();
            //            logger.info("Attachment from consumer: " + RpcContext.getContext().getAttachment("consumer-key1"));
            //            logger.info("async start");
            //            try {
            //                Thread.sleep(5000);
            //            } catch (InterruptedException e) {
            //                e.printStackTrace();
            //            }
            //            asyncContext.write("Hello " + name + ", response from provider.");
            //            logger.info("async end");
            //        }).start();
            //
            //        logger.info("sayHello end");
            //        return "hello, " + name;
            //    }
            return ((AsyncContextImpl)(RpcContext.getContext().getAsyncContext())).getInternalFuture();
        } else if (value instanceof CompletableFuture) {
            return (CompletableFuture<Object>) value;
        }
        // 一般是这个
        return CompletableFuture.completedFuture(value);
    }

    // 一般在new AbstractProxyInvoker的时候重写该方法
    protected abstract Object doInvoke(T proxy, String methodName, Class<?>[] parameterTypes, Object[] arguments) throws Throwable;

    @Override
    public String toString() {
        return getInterface() + " -> " + (getUrl() == null ? " " : getUrl().toString());
    }


}

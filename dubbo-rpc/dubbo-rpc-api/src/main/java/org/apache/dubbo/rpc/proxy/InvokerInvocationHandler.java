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
import org.apache.dubbo.rpc.Constants;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ConsumerModel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * InvokerHandler
 */
// OK
// 接口就是JDK原生的InvocationHandler
public class InvokerInvocationHandler implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(InvokerInvocationHandler.class);
    private final Invoker<?> invoker;
    private ConsumerModel consumerModel;
    private URL url;
    private String protocolServiceKey;

    public InvokerInvocationHandler(Invoker<?> handler) {
        // 被代理对象、目标对象（惯用法都是这样通过构造函数传入被代理对象）
        this.invoker = handler;
        // eg:test://test:11/test?group=dubbo&version=1.1
        this.url = invoker.getUrl();
        // eg:dubbo/test:1.1  由{group}/{interfaceName}:{version}组成 进去
        String serviceKey = this.url.getServiceKey();
        // eg:dubbo/test:1.1:test 由serviceKey+Protocol组成，进去
        this.protocolServiceKey = this.url.getProtocolServiceKey();
        if (serviceKey != null) {
            this.consumerModel = ApplicationModel.getConsumerModel(serviceKey);
        }
    }

    // 调用代理对象的方法（代理对象本身和目标对象一样都是实现接口，基于接口代理）都会转到这里，内部会调用目标对象对应的方法
    // 第一个参数就是代理类对象
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 拦截定义在 Object 类中的方法（未被子类重写），比如 wait/notify
        if (method.getDeclaringClass() == Object.class) {
            // 如果是Object方法直接调用即可
            return method.invoke(invoker, args);
        }
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 如果方法没有参数
        if (parameterTypes.length == 0) {
            // 调用invoker相关方法  --- > 和以往method.invoke(invoker,args)不同，这里是直接对象.方法
            if ("toString".equals(methodName)) {
                return invoker.toString();
            } else if ("$destroy".equals(methodName)) { // 因为客户端的代理类的生成是实现了内置的AbstractProxyFactory.Destroyable。这个是有该方法的
                invoker.destroy();
                return null;
            } else if ("hashCode".equals(methodName)) {
                return invoker.hashCode();
            }
            // 一个参数且方法名是equals
        } else if (parameterTypes.length == 1 && "equals".equals(methodName)) {
            // 调用invoker相关方法
            return invoker.equals(args[0]);
        }
        // 构建RpcInvocation
        RpcInvocation rpcInvocation = new RpcInvocation(method,
                invoker.getInterface().getName(), protocolServiceKey, args); // 这里没有设置Invoker实例（比如DubboInvoker），在AbstractInvoker里面设置了
        // {group}/{interfaceName}:{version}
        String serviceKey = invoker.getUrl().getServiceKey();
        // 设置目标service的唯一名称的值为serviceKey
        rpcInvocation.setTargetServiceUniqueName(serviceKey);
        // invoker.getUrl() returns consumer url. // 进去
        RpcContext.setRpcContext(invoker.getUrl());

        if (consumerModel != null) {
            rpcInvocation.put(Constants.CONSUMER_MODEL, consumerModel);
            rpcInvocation.put(Constants.METHOD_MODEL, consumerModel.getMethodModel(method));
        }

        // 调用目标方法
        // 以往我们的写法都是method.invoke(invoker,args);下面是纯目标对象.方法的调用，而且只是invoke方法
        // invoke方法返回AsyncRpcResult，调用其recreate 进去

        // InvokerInvocationHandler 中的 invoker 成员变量类型为 MockClusterInvoker，MockClusterInvoker 内部封装了服务降级逻辑。下面简单看一下
        return invoker.invoke(rpcInvocation).recreate(); // dubbo-rpc模块的测试类的话，此时invoker为 AsyncToSyncInvoker，进去
    }
}

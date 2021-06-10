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

import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.rpc.Constants;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.Destroyable;
import org.apache.dubbo.rpc.service.GenericService;

import com.alibaba.dubbo.rpc.service.EchoService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.rpc.Constants.INTERFACES;

/**
 * AbstractProxyFactory
 */
// 两个子类：jdkProxyFactory+JavassistProxyFactory
public abstract class AbstractProxyFactory implements ProxyFactory {
    private static final Class<?>[] INTERNAL_INTERFACES = new Class<?>[]{
            EchoService.class, Destroyable.class
    };

    @Override
    public <T> T getProxy(Invoker<T> invoker) throws RpcException {
        // 调用下面的方法
        return getProxy(invoker, false);
    }

    // 第二个参数表示是否是泛化的
    @Override
    public <T> T getProxy(Invoker<T> invoker, boolean generic) throws RpcException {
        Set<Class<?>> interfaces = new HashSet<>();

        String config = invoker.getUrl().getParameter(INTERFACES);
        if (config != null && config.length() > 0) {
            String[] types = COMMA_SPLIT_PATTERN.split(config);
            for (String type : types) {
                // TODO can we load successfully for a different classloader?.
                interfaces.add(ReflectUtils.forName(type));
            }
        }

        if (generic) {
            // 一般是满足的，一般接口不会去实现GenericService接口
            if (!GenericService.class.isAssignableFrom(invoker.getInterface())) {
                // 添加阿里巴巴的
                interfaces.add(com.alibaba.dubbo.rpc.service.GenericService.class);
            }

            try {
                // find the real interface from url
                String realInterface = invoker.getUrl().getParameter(Constants.INTERFACE);
                interfaces.add(ReflectUtils.forName(realInterface));
            } catch (Throwable e) {
                // ignore
            }
        }

        interfaces.add(invoker.getInterface());
        interfaces.addAll(Arrays.asList(INTERNAL_INTERFACES));

        // 抽象方法被两个子类重写
        //interfaces = {HashSet@2709}  size = 4
        // 0 = {Class@2733} "interface org.apache.dubbo.rpc.service.Destroyable"
        // 1 = {Class@2734} "interface org.apache.dubbo.rpc.service.EchoService"
        // 2 = {Class@1596} "interface org.apache.dubbo.service.DemoService"
        // 3 = {Class@2735} "interface com.alibaba.dubbo.rpc.service.GenericService" --- 这个可选。，上三个必须有
        return getProxy(invoker, interfaces.toArray(new Class<?>[0]));
    }

    // 其中getProxy是实现抽象类AbstractProxyFactory中的抽象方法。AbstractProxyFactory抽象类实现了ProxyFactory接口中getProxy方法，
    // JdkProxyFactory也实现了抽象类AbstractProxyFactory中的getProxy抽象方法。Javassist与Jdk动态代理的共同部分被封装在父类AbstractProxyFactory中，
    // 具体的实现类只需负责实现代理生成过程的差异化部分。
    public abstract <T> T getProxy(Invoker<T> invoker, Class<?>[] types);

}

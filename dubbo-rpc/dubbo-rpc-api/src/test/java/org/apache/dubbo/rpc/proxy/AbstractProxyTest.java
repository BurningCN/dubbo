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
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.support.DemoService;
import org.apache.dubbo.rpc.support.DemoServiceImpl;
import org.apache.dubbo.rpc.support.MyInvoker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;


// OK
public abstract class AbstractProxyTest {

    // Factory的实例化在两个子类
    public static ProxyFactory factory;

    @Test
    public void testGetProxy() throws Exception {
        // 进去
        URL url = URL.valueOf("test://test:11/test?group=dubbo&version=1.1");

        // 这个是手动创建的，一般是调用ProxyFactory.getInvoker创建的，进去看看
        Invoker<DemoService> invoker = new MyInvoker<>(url);

        // 为invoker生成动态代理类对象
        DemoService proxy = factory.getProxy(invoker);

        Assertions.assertNotNull(proxy);
        // 对于JavassistProxyFactoryTest 肯定包含这个接口，当然还有另三个接口：DC、EchoService、Destroyable，proxy就是基于这四个接口生成的代理类，并且实现了所有接口的方法
        // 可以全局搜proxy0.class
        Assertions.assertTrue(Arrays.asList(proxy.getClass().getInterfaces()).contains(DemoService.class));

        // Not equal
        //Assertions.assertEquals(proxy.toString(), invoker.toString());
        //Assertions.assertEquals(proxy.hashCode(), invoker.hashCode());

        Assertions.assertEquals(invoker.invoke(
                // RpcInvocation rpc调用 指定调用的方法、参数等信息
                new RpcInvocation("echo",
                        DemoService.class.getName(),
                        DemoService.class.getName() + ":dubbo",
                        new Class[]{String.class}, new Object[]{"aa"})
                ).getValue()
                // 内部指定进InvokerInvocationHandler的invoke方法
                , proxy.echo("aa"));
    }

    @Test
    public void testGetInvoker() throws Exception {
        URL url = URL.valueOf("test://test:11/test?group=dubbo&version=1.1");

        DemoService origin = new org.apache.dubbo.rpc.support.DemoServiceImpl();

        // 创建invoker，进去
        Invoker<DemoService> invoker = factory.getInvoker(new DemoServiceImpl(), DemoService.class, url);
        // 会进入AbstractProxyInvoker的getInterface方法，返回type，即上面getInvoker方法的第二个参数
        Assertions.assertEquals(invoker.getInterface(), DemoService.class);

        //  会进入AbstractProxyInvoker的invoke方法，其invoke又会调用doInvoke方法（该方法在getInvoker重写了）
        Assertions.assertEquals(invoker.invoke(new RpcInvocation("echo", DemoService.class.getName(), DemoService.class.getName() + ":dubbo", new Class[]{String.class}, new Object[]{"aa"})).getValue(),
                origin.echo("aa"));

    }

}

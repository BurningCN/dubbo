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
package org.apache.dubbo.common.bytecode;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ProxyTest {

    @Test
    public void testMain() throws Exception {
        Proxy proxy = Proxy.getProxy(ITest.class, ITest.class);
        // 传入的参数是InvocationHandle，里面就封装了我们实际要做的代理(拦截)逻辑，第一个参数proxy1就是动态代理生成的代理类对象，
        // 具体去看Proxy#getProxy方法的最后一段反编译代码示例
        ITest instance = (ITest) proxy.newInstance((proxy1, method, args) -> {
            if ("getName".equals(method.getName())) {
                assertEquals(args.length, 0);
            } else if ("setName".equals(method.getName())) {
                assertEquals(args.length, 2);
                assertEquals(args[0], "qianlei");
                assertEquals(args[1], "hello");
            }
            return null;
        });

        assertNull(instance.getName());
        instance.setName("qianlei", "hello");
    }

    @Test
    public void testCglibProxy() throws Exception {
        // Proxy.getProxy生成的类为 Proxy0 extends Proxy implements DC
        // Proxy.getProxy().newInstance 返回的是 proxy0 implements DC, ITest，所以可以强转为ITest
        ITest test = (ITest) Proxy.getProxy(ITest.class).newInstance((proxy, method, args) -> {
            System.out.println(method.getName());
            return null;
        });

        // cglib 可以代理那些 目标类实现接口也可以不实现接口的类，上面返回的test是代理类，本身是一个类，所以下面setSuperclass(test.getClass());
        // 将这个动态代理类声明为父类
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(test.getClass());
        enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> null);
        try {
            // 会创建子类，cglib的核心就是子类扩展父类来实现代理
            enhancer.create();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            Assertions.fail();
        }
    }

    public interface ITest {
        String getName();

        void setName(String name, String name2);

        // 静态方法不会被代理
        static String sayBye() {
            return "Bye!";
        }
    }
}
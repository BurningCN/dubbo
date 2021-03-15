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

package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.support.DemoService;
import org.apache.dubbo.rpc.support.MyInvoker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URLClassLoader;

// OK
public class ClassLoaderFilterTest {

    private ClassLoaderFilter classLoaderFilter = new ClassLoaderFilter();

    @Test
    public void testInvoke() throws Exception {
        URL url = URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1");

        // /Users/gy821075/IdeaProjects/dubbo/dubbo-rpc/dubbo-rpc-api/target/test-classes/
        String path = DemoService.class.getResource("/").getPath();
        // 自定义加载器，URLClassLoader
        final URLClassLoader cl = new URLClassLoader(new java.net.URL[]{new java.net.URL("file:" + path)}) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                try {
                    // 自定义类加载器一定是重写loadClass，并且！！要调用这个方法findClass，因为该方法内部会抛异常，这样我们补货异常，然后让上层加载器去加载
                    return findClass(name);
                } catch (ClassNotFoundException e) {
                    // 比如下面要加载DemoService的时候，肯定是需要先加载父类Object，但是这个我们的自定义URLClassLoader是无法加载的，就需要走这里的逻辑
                    return super.loadClass(name);
                }
            }
        };
        // 注意实际在loadClass的时候还是利用AppClassLoader加载的，不过会传递给URLClassLoader
        // 如果对aClass和DemoService.class分别调用getClassLoader,发现返回的分别是URLClassLoader和AppClassLoader
        final Class<?> clazz = cl.loadClass(DemoService.class.getCanonicalName());
        Invoker invoker = new MyInvoker(url) {
            // 该方法会在ClassLoaderFilter调用，使用该class的加载器(就是上面的URLClassLoader)作为线程上下文加载器
            @Override
            public Class getInterface() {// 注意一定要重写这个方法，因为我们是要URLClassLoader
                return clazz;
            }

            @Override
            public Result invoke(Invocation invocation) throws RpcException {
                Assertions.assertEquals(cl, Thread.currentThread().getContextClassLoader());
                return null;
            }
        };
        Invocation invocation = Mockito.mock(Invocation.class);

        // 进去
        classLoaderFilter.invoke(invoker, invocation);
    }
}

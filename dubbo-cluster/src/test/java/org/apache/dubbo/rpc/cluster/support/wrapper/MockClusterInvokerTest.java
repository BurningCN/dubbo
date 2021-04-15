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
package org.apache.dubbo.rpc.cluster.support.wrapper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.apache.dubbo.rpc.cluster.support.AbstractClusterInvoker;
import org.apache.dubbo.rpc.support.MockProtocol;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.rpc.Constants.MOCK_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;

public class MockClusterInvokerTest {

    List<Invoker<IHelloService>> invokers = new ArrayList<Invoker<IHelloService>>();

    @BeforeEach
    public void beforeMethod() {
        invokers.clear();
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    @Test
    public void testMockInvokerInvoke_normal() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName());
        url = url.addParameter(MOCK_KEY, "fail")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()));

        Invoker<IHelloService> cluster = getClusterInvoker(url);// 进去

        URL mockUrl = URL.valueOf("mock://localhost/" + IHelloService.class.getName()
                + "?getSomething.mock=return aa");
        Protocol protocol = new MockProtocol();
        // 内部生成MockInvoker
        Invoker<IHelloService> mInvoker1 = protocol.refer(IHelloService.class, mockUrl);
        invokers.add(mInvoker1);

        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        // 这里的cluster就是 MockClusterInvoker（注意mock值的来源为 MockClusterInvoker 内部调用dic.getUrl，dic的url在前面getClusterInvoker方法内部构造的）
        // 且注意invoke能正常调用，不会触发moke
        Result ret = cluster.invoke(invocation);
        // HelloService#getSomething的返回值就是something
        Assertions.assertEquals("something", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");
        // HelloService#sayHello没有返回值
        ret = cluster.invoke(invocation);
        Assertions.assertNull(ret.getValue());
    }

    /**
     * Test if mock policy works fine: fail-mock
     */

    /*

    (1) 首先注意url和mockUrl，url在getClusterInvokerMock中会作为dir的url，且注意两个url的mock参数值是不一样的，getClusterInvokerMock往
    dir填充了两个invoker，分别是根据两个url生成的，一个是普通的提供者invoker，一个是MockInvoker

    (2) 发起getSomething调用的时候，MockClusterInvoker内部getUrl(dir.getUrl)获取到的mock值为fail:return null，表示先调用，调用失败return null
    而url是含有invoke_return_error参数的，在getClusterInvokerMock生成的AbstractClusterInvoker#doInvoke会抛异常，这样就会走
    MockClusterInvoker#doMockInvoke的逻辑，首先给inv添加一个 "invocation.need.mock"参数，这样在dir.list的时候，有一个MockInvokerSelector
    的router就会从invokers集合中拿到mockUrl生成的MockInvoker实例，其invoke方法，首先从mockUrl拿到getSomething.mock的参数值，是能拿到的，值为
    return aa，然后截取return后面的aa，且判定getSomething的返回类型为string，所以会转化为String "aa" 作为mock的返回结果。

    (3) 发起getSomething2调用的时候。。。。。逻辑和2一致。。。走到MockInvoker#invoke的时候，mockUrl.getMethodParameter("getSomething2.mock")，
    url内部处理发现是没有getSomething2.mock参数值的，url内部会再次从url直接获取mock参数值，是有的（注意mockUrl最后addParameters(url.getParameters())），
    参数值为fail:return null ，经过normalize后 return null
    */
    @Test
    public void testMockInvokerInvoke_failmock() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter(MOCK_KEY, "fail:return null")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                // 注意这个参数。该url在 后续调用的getClusterInvokerMock 方法中，会根据此url生成AbstractClusterInvoker，其doInvoke方法判断invoker.geturl含有该参数的话会抛异常
                .addParameter("invoke_return_error", "true");
        URL mockUrl = URL.valueOf("mock://localhost/" + IHelloService.class.getName()
                + "?getSomething.mock=return aa").addParameters(url.getParameters());

        Protocol protocol = new MockProtocol();
        Invoker<IHelloService> mInvoker1 = protocol.refer(IHelloService.class, mockUrl);
        Invoker<IHelloService> cluster = getClusterInvokerMock(url, mInvoker1);

        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("aa", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething2");
        ret = cluster.invoke(invocation);
        Assertions.assertNull(ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");
        ret = cluster.invoke(invocation);
        Assertions.assertNull(ret.getValue());
    }


    /**
     * Test if mock policy works fine: force-mock
     */
    // 大部分逻辑参考第二段测试程序上的注释
    @Test
    public void testMockInvokerInvoke_forcemock() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName());
        url = url.addParameter(MOCK_KEY, "force:return null")// 注意
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()));

        URL mockUrl = URL.valueOf("mock://localhost/" + IHelloService.class.getName()
                + "?getSomething.mock=return aa&getSomething3xx.mock=return xx")
                .addParameters(url.getParameters());// 注意url.getParameters()，重点是想要force:return null

        Protocol protocol = new MockProtocol();
        Invoker<IHelloService> mInvoker1 = protocol.refer(IHelloService.class, mockUrl);
        Invoker<IHelloService> cluster = getClusterInvokerMock(url, mInvoker1);

        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        // 前面url的mock为force:xx，则直接走mock逻辑
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("aa", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething2");
        ret = cluster.invoke(invocation);
        Assertions.assertNull(ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");
        ret = cluster.invoke(invocation);
        Assertions.assertNull(ret.getValue());
    }

    @Test
    public void testMockInvokerInvoke_forcemock_defaultreturn() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName());
        url = url.addParameter(MOCK_KEY, "force")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()));

        Invoker<IHelloService> cluster = getClusterInvoker(url);
        URL mockUrl = URL.valueOf("mock://localhost/" + IHelloService.class.getName()
                + "?getSomething.mock=return aa&getSomething3xx.mock=return xx&sayHello.mock=return ")// 注意这里的sayHello.mock=return
                .addParameters(url.getParameters());

        Protocol protocol = new MockProtocol();
        Invoker<IHelloService> mInvoker1 = protocol.refer(IHelloService.class, mockUrl);
        invokers.add(mInvoker1);

        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");
        Result ret = cluster.invoke(invocation);
        Assertions.assertNull(ret.getValue());
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    @Test
    public void testMockInvokerFromOverride_Invoke_Fock_someMethods() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getSomething.mock", "fail:return x")
                .addParameter("getSomething2.mock", "force:return y")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()));
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        // 内部会获取getSomething.mock的参数值，如果没有则获取mock参数值，显然是有getSomething.mock参数值的，为"fail:return x"
        // fail表示目标invoker调用失败才会走mock，但是这里调用是成功的。
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("something", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething2");
        // getSomething2.mock为force:xx，表示直接走mock，但是MockClusterInvoker内部dir.list返回的invoker为空（因为前面getClusterInvoker只添加了一个提供者invoker）
        // 没有像前几个测试程序手动加了MockInvoker，那么MockClusterInvoker内部发现dir.list返回null之后会现场创建一个MockInvoker....
        // 结果肯定就是y了
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("y", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething3");
        // invoke方法内部没有发现mock参数值，直接调用目标invoker的方法，正常调用，返回something3
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("something3", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");
        ret = cluster.invoke(invocation);
        Assertions.assertNull(ret.getValue());
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    // 这个测试名称叫做 xxxxWithOutDefault，这句话是啥意思，可以先看下一个测试用例的第一处出现的中文注释
    @Test
    public void testMockInvokerFromOverride_Invoke_Fock_WithOutDefault() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getSomething.mock", "fail:return x")
                .addParameter("getSomething2.mock", "force:return y")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                // 注意，有这个参数，所有提供者invoker#invoke都会抛异常
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("x", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething2");
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("y", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething3");
        try {
            // getSomething3无mock参数值，直接调用目标invoker 的invoke方法，抛异常
            ret = cluster.invoke(invocation);
            Assertions.fail();
        } catch (RpcException e) {

        }
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    @Test
    public void testMockInvokerFromOverride_Invoke_Fock_WithDefault() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                // 注意 ，这就是方法名称所说的 WithDefault，除了getSomething、getSomething2方法，其他任何方法的mock策略都是 "fail:return null"
                .addParameter("mock", "fail:return null")
                .addParameter("getSomething.mock", "fail:return x")
                .addParameter("getSomething2.mock", "force:return y")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("x", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething2");
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("y", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething3");
        ret = cluster.invoke(invocation);
        Assertions.assertNull(ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");
        ret = cluster.invoke(invocation);
        Assertions.assertNull(ret.getValue());
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    @Test
    public void testMockInvokerFromOverride_Invoke_Fock_WithFailDefault() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                // 和前一个测试用例一样，WithFailDefault的，策略如下，fail:return z
                .addParameter("mock", "fail:return z")
                .addParameter("getSomething.mock", "fail:return x")
                .addParameter("getSomething2.mock", "force:return y")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("x", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething2");
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("y", ret.getValue());

        // If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething3");
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("z", ret.getValue());

        //If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("z", ret.getValue());
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    @Test
    public void testMockInvokerFromOverride_Invoke_Fock_WithForceDefault() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                // 默认的策略为 "force:return z"
                .addParameter("mock", "force:return z")
                .addParameter("getSomething.mock", "fail:return x")
                .addParameter("getSomething2.mock", "force:return y")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("x", ret.getValue());

        //If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething2");
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("y", ret.getValue());

        //If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething3");
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("z", ret.getValue());

        //If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("z", ret.getValue());
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    // 这个不看
    @Test
    public void testMockInvokerFromOverride_Invoke_Fock_Default() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("mock", "fail:return x")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("x", ret.getValue());

        //If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething2");
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("x", ret.getValue());

        //If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");
        ret = cluster.invoke(invocation);
        Assertions.assertEquals("x", ret.getValue());
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    @Test
    public void testMockInvokerFromOverride_Invoke_checkCompatible_return() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getSomething.mock", "return x")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("x", ret.getValue());

        //If no mock was configured, return null directly
        invocation = new RpcInvocation();
        invocation.setMethodName("getSomething3");
        try {
            // 前面url没有配置mock参数值，直接调用invoker#invoke，抛异常
            ret = cluster.invoke(invocation);
            Assertions.fail("fail invoke");
        } catch (RpcException e) {

        }
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    // 重要！！
    @Test
    public void testMockInvokerFromOverride_Invoke_checkCompatible_ImplMock() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                // 注意值为true
                .addParameter("mock", "true")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        // 因为mock参数值不是null，也不是force:xx，属于 MockClusterInvoker#invoke 的 else逻辑处理部分，最后走MockInvoker#invoke逻辑，发现mock值不是return 开头、不是throw开头，那么就是实现类全限定名称
        // 且true被normalize为default，MockInvoker#getMockObject会加载 IHelloServiceMock 类 ，正好测试程序是有的，然后调用其getSomething方法，返回somethingmock
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("somethingmock", ret.getValue());
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    @Test
    public void testMockInvokerFromOverride_Invoke_checkCompatible_ImplMock2() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                // 和上面同理，fail 、true 、force 都会在MockInvoker的normalize转化为default，而default就会调用 （加载了的）ServiceType.getName + "Mock" 类 的 xx方法
                .addParameter("mock", "fail")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("somethingmock", ret.getValue());
    }

    /**
     * Test if mock policy works fine: fail-mock
     */
    @Test
    public void testMockInvokerFromOverride_Invoke_checkCompatible_ImplMock3() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                // 同上
                .addParameter("mock", "force");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals("somethingmock", ret.getValue());
    }

    // easy
    @Test
    public void testMockInvokerFromOverride_Invoke_check_String() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                // 注意
                .addParameter("getSomething.mock", "force:return 1688")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Result ret = cluster.invoke(invocation);
        Assertions.assertTrue(ret.getValue() instanceof String, "result type must be String but was : " + ret.getValue().getClass());
        // 注意
        Assertions.assertEquals("1688", (String) ret.getValue());
    }

    // 和上面一样，只是检测能否返回值类型为int
    @Test
    public void testMockInvokerFromOverride_Invoke_check_int() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("getInt1.mock", "force:return 1688")
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getInt1");
        // 因为getInt1方法的返回值为int，MockInvoker则会将1688转化为int（parseMockValue方法中StringUtils.isNumeric(mock, false) + JSON.parse(mock);）
        Result ret = cluster.invoke(invocation);
        Assertions.assertTrue(ret.getValue() instanceof Integer, "result type must be integer but was : " + ret.getValue().getClass());
        Assertions.assertEquals(new Integer(1688), (Integer) ret.getValue());
    }

    @Test
    public void testMockInvokerFromOverride_Invoke_check_boolean() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("getBoolean1.mock", "force:return true")
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        // getBoolean1 和 getBoolean2（下面的测试程序） 分别返回的类型为boolean 和 Boolean，
        // 不过 MokeInvoker#parseMockValue不会搭理这些，在判定值为"true"或"false"，直接就返回了true或false
        invocation.setMethodName("getBoolean1");
        Result ret = cluster.invoke(invocation);
        Assertions.assertTrue(ret.getValue() instanceof Boolean, "result type must be Boolean but was : " + ret.getValue().getClass());
        Assertions.assertTrue(Boolean.parseBoolean(ret.getValue().toString()));
    }

    @Test
    public void testMockInvokerFromOverride_Invoke_check_Boolean() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("getBoolean2.mock", "force:return true")
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getBoolean2");
        Result ret = cluster.invoke(invocation);
        Assertions.assertTrue(Boolean.parseBoolean(ret.getValue().toString()));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMockInvokerFromOverride_Invoke_check_ListString_empty() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getListString.mock", "force:return empty")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        // 看 parseMockValue 对   if ("empty".equals(mock)) {的处理
        invocation.setMethodName("getListString");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals(0, ((List<String>) ret.getValue()).size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMockInvokerFromOverride_Invoke_check_ListString() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getListString.mock", "force:return [\"hi\",\"hi2\"]")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getListString");
        Result ret = cluster.invoke(invocation);
        List<String> rl = (List<String>) ret.getValue();
        Assertions.assertEquals(2, rl.size());
        Assertions.assertEquals("hi", rl.get(0));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMockInvokerFromOverride_Invoke_check_ListPojo_empty() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getUsers.mock", "force:return empty")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getUsers");
        Result ret = cluster.invoke(invocation);
        Assertions.assertEquals(0, ((List<User>) ret.getValue()).size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMockInvokerFromOverride_Invoke_check_ListPojo() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getUsers.mock", "force:return [{id:1, name:\"hi1\"}, {id:2, name:\"hi2\"}]")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getUsers");
        Result ret = cluster.invoke(invocation);
        List<User> rl = (List<User>) ret.getValue();
        System.out.println(rl);
        Assertions.assertEquals(2, rl.size());
        Assertions.assertEquals("hi1", ((User) rl.get(0)).getName());
    }

    @Test
    public void testMockInvokerFromOverride_Invoke_check_ListPojo_error() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getUsers.mock", "force:return [{id:x, name:\"hi1\"}]")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getUsers");
        try {
            cluster.invoke(invocation);
        } catch (RpcException e) {
        }
    }

    @Test
    public void testMockInvokerFromOverride_Invoke_force_throw() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getBoolean2.mock", "force:throw ")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getBoolean2");
        try {
            cluster.invoke(invocation);
            Assertions.fail();
        } catch (RpcException e) {
            Assertions.assertFalse(e.isBiz(), "not custem exception");
        }
    }

    @Test
    public void testMockInvokerFromOverride_Invoke_force_throwCustemException() throws Throwable {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getBoolean2.mock", "force:throw org.apache.dubbo.rpc.cluster.support.wrapper.MyMockException")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getBoolean2");
        try {
            cluster.invoke(invocation).recreate();
            Assertions.fail();
        } catch (MyMockException e) {

        }
    }

    @Test
    public void testMockInvokerFromOverride_Invoke_force_throwCustemExceptionNotFound() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("getBoolean2.mock", "force:throw java.lang.RuntimeException2")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getBoolean2");
        try {
            cluster.invoke(invocation);
            Assertions.fail();
        } catch (Exception e) {
            Assertions.assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void testMockInvokerFromOverride_Invoke_mock_false() {
        URL url = URL.valueOf("remote://1.2.3.4/" + IHelloService.class.getName())
                .addParameter("mock", "false")
                .addParameter(REFER_KEY, URL.encode(PATH_KEY + "=" + IHelloService.class.getName()))
                .addParameter("invoke_return_error", "true");
        Invoker<IHelloService> cluster = getClusterInvoker(url);
        //Configured with mock
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getBoolean2");
        try {
            cluster.invoke(invocation);
            Assertions.fail();
        } catch (RpcException e) {
            Assertions.assertTrue(e.isTimeout());
        }
    }

    private Invoker<IHelloService> getClusterInvokerMock(URL url, Invoker<IHelloService> mockInvoker) {
        // As `javassist` have a strict restriction of argument types, request will fail if Invocation do not contains complete parameter type information
        // 由于“javassist”对参数类型有严格的限制，如果调用没有包含完整的参数类型信息，请求将会失败
        final URL durl = url.addParameter("proxy", "jdk");

        invokers.clear();
        ProxyFactory proxy = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getExtension("jdk");
        Invoker<IHelloService> invoker1 = proxy.getInvoker(new HelloService(), IHelloService.class, durl);
        invokers.add(invoker1);

        if (mockInvoker != null) {
            invokers.add(mockInvoker);
        }

        // dic依赖提供者和routerChain
        StaticDirectory<IHelloService> dic = new StaticDirectory<>(durl, invokers, null);
        dic.buildRouterChain();

        // AbstractClusterInvoker依赖dic
        // 这里的AbstractClusterInvoker就是简单的，不像Failover那些比较复杂，其doInvoke策略也很简单，就是两个逻辑（if-else），第二个逻辑筛选第一个提供者发起调用
        AbstractClusterInvoker<IHelloService> cluster = new AbstractClusterInvoker(dic) {
            @Override
            protected Result doInvoke(Invocation invocation, List invokers, LoadBalance loadbalance)
                    throws RpcException {
                if (durl.getParameter("invoke_return_error", false)) {
                    throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "test rpc exception");
                } else {
                    return ((Invoker<?>) invokers.get(0)).invoke(invocation);
                }
            }
        };
        // MockClusterInvoker依赖dic和其他的AbstractClusterInvoker实例（比如FailoverClusterInvoker）
        return new MockClusterInvoker<IHelloService>(dic, cluster);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Invoker<IHelloService> getClusterInvoker(URL url) {
        return getClusterInvokerMock(url, null);
    }

    public interface IHelloService {
        String getSomething();

        String getSomething2();

        String getSomething3();

        String getSomething4();

        int getInt1();

        boolean getBoolean1();

        Boolean getBoolean2();

        List<String> getListString();

        List<User> getUsers();

        void sayHello();
    }

    public static class HelloService implements IHelloService {
        public String getSomething() {
            return "something";
        }

        public String getSomething2() {
            return "something2";
        }

        public String getSomething3() {
            return "something3";
        }

        public String getSomething4() {
            throw new RpcException("getSomething4|RpcException");
        }

        public int getInt1() {
            return 1;
        }

        public boolean getBoolean1() {
            return false;
        }

        public Boolean getBoolean2() {
            return Boolean.FALSE;
        }

        public List<String> getListString() {
            return Arrays.asList(new String[]{"Tom", "Jerry"});
        }

        public List<User> getUsers() {
            return Arrays.asList(new User[]{new User(1, "Tom"), new User(2, "Jerry")});
        }

        public void sayHello() {
            System.out.println("hello prety");
        }
    }

    public static class IHelloServiceMock implements IHelloService {
        public IHelloServiceMock() {

        }

        public String getSomething() {
            return "somethingmock";
        }

        public String getSomething2() {
            return "something2mock";
        }

        public String getSomething3() {
            return "something3mock";
        }

        public String getSomething4() {
            return "something4mock";
        }

        public List<String> getListString() {
            return Arrays.asList(new String[]{"Tommock", "Jerrymock"});
        }

        public List<User> getUsers() {
            return Arrays.asList(new User[]{new User(1, "Tommock"), new User(2, "Jerrymock")});
        }

        public int getInt1() {
            return 1;
        }

        public boolean getBoolean1() {
            return false;
        }

        public Boolean getBoolean2() {
            return Boolean.FALSE;
        }

        public void sayHello() {
            System.out.println("hello prety");
        }
    }

    public static class User {
        private int id;
        private String name;

        public User() {
        }

        public User(int id, String name) {
            super();
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }
}

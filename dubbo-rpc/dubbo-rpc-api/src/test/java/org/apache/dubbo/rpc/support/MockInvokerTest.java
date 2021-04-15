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
package org.apache.dubbo.rpc.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;

import static org.apache.dubbo.rpc.Constants.MOCK_KEY;

// OK
public class MockInvokerTest {

    @Test
    public void testParseMockValue() throws Exception {
        // 以下parseMockValue都进去

        Assertions.assertNull(MockInvoker.parseMockValue("null"));
        Assertions.assertNull(MockInvoker.parseMockValue("empty"));

        Assertions.assertTrue((Boolean) MockInvoker.parseMockValue("true"));
        Assertions.assertFalse((Boolean) MockInvoker.parseMockValue("false"));

        Assertions.assertEquals(123, MockInvoker.parseMockValue("123"));
        Assertions.assertEquals("foo", MockInvoker.parseMockValue("foo"));
        Assertions.assertEquals("foo", MockInvoker.parseMockValue("\"foo\""));
        Assertions.assertEquals("foo", MockInvoker.parseMockValue("\'foo\'"));

        Assertions.assertEquals(
                 // map
                new HashMap<>(), MockInvoker.parseMockValue("{}"));
        Assertions.assertEquals(
                // list
                new ArrayList<>(), MockInvoker.parseMockValue("[]"));
        Assertions.assertEquals("foo",
                MockInvoker.parseMockValue("foo", new Type[]{String.class}));
    }

    @Test
    public void testInvoke() {
        URL url = URL.valueOf("remote://1.2.3.4/" + String.class.getName());
        url = url.addParameter(MOCK_KEY, "return ");
        // 进去
        MockInvoker mockInvoker = new MockInvoker(url, String.class);

        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Assertions.assertEquals(new HashMap<>(),
                // 进去
                mockInvoker.invoke(invocation).getObjectAttachments());
    }

    @Test
    public void testInvokeThrowsRpcException1() {
        URL url = URL.valueOf("remote://1.2.3.4/" + String.class.getName());
        MockInvoker mockInvoker = new MockInvoker(url, null);

        Assertions.assertThrows(RpcException.class,
                // 进去，内部mock值不能为null，否则抛异常
                () -> mockInvoker.invoke(new RpcInvocation()));
    }

    @Test
    public void testInvokeThrowsRpcException2() {
        URL url = URL.valueOf("remote://1.2.3.4/" + String.class.getName());
        url = url.addParameter(MOCK_KEY, "fail");
        MockInvoker mockInvoker = new MockInvoker(url, String.class);

        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Assertions.assertThrows(RpcException.class,
                // 进去，mock为fail的话，内部会标准化为default，并加载type.getName+"Mock"类（type就是传给MockInvoker的第二个参数），
                // 加载不到就抛异常，String.getName()+"Mock"肯定是没这个类的
                () -> mockInvoker.invoke(invocation));
    }

    @Test
    public void testInvokeThrowsRpcException3() {
        URL url = URL.valueOf("remote://1.2.3.4/" + String.class.getName());
        url = url.addParameter(MOCK_KEY, "throw"); // 注意
        MockInvoker mockInvoker = new MockInvoker(url, String.class);

        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getSomething");
        Assertions.assertThrows(RpcException.class,
                // 进去 抛异常是因为mock值只是throw，后面没有异常的全限定名称，内部判断为空直接抛异常
                () -> mockInvoker.invoke(invocation));
    }

    @Test
    public void testGetThrowable() {
        Assertions.assertThrows(RpcException.class,
                // 进去，内部加载这个throwstr加载不到，抛异常，把这个变成"java.lang.Exception"就没有问题了
                () -> MockInvoker.getThrowable("Exception.class"));



    }

    @Test
    public void testGetMockObject() {
        // getMockObject方法的第一个参数为mock值，代表的是某个类的全兴定名称（第二个参数的子类）
        Assertions.assertEquals("",
                // 进去
                MockInvoker.getMockObject("java.lang.String", String.class));

        Assertions.assertThrows(IllegalStateException.class, () -> MockInvoker
                .getMockObject("true", String.class)); // true在内部识别为default，不过没有java.lang.StringMock子类

        Assertions.assertThrows(IllegalStateException.class, () -> MockInvoker
                .getMockObject("default", String.class));// 同上

        Assertions.assertThrows(IllegalStateException.class, () -> MockInvoker
                .getMockObject("java.lang.String", Integer.class));// 不满足isAssignableFrom

        Assertions.assertThrows(IllegalStateException.class, () -> MockInvoker
                .getMockObject("java.io.Serializable", Serializable.class)); // 满足isAssignableFrom，不过这个接口没有构造方法，mockClass.newInstance();失败
    }

    @Test
    public void testNormalizeMock() {
        Assertions.assertNull(MockInvoker.normalizeMock(null));

        Assertions.assertEquals("", MockInvoker.normalizeMock(""));
        Assertions.assertEquals("", MockInvoker.normalizeMock("fail:"));
        Assertions.assertEquals("", MockInvoker.normalizeMock("force:"));
        Assertions.assertEquals("throw", MockInvoker.normalizeMock("throw"));
        Assertions.assertEquals("default", MockInvoker.normalizeMock("fail"));
        Assertions.assertEquals("default", MockInvoker.normalizeMock("force"));
        Assertions.assertEquals("default", MockInvoker.normalizeMock("true"));
        Assertions.assertEquals("default",
                MockInvoker.normalizeMock("default"));
        Assertions.assertEquals("return null",
                MockInvoker.normalizeMock("return"));
        Assertions.assertEquals("return null",
                MockInvoker.normalizeMock("return null"));
    }
}

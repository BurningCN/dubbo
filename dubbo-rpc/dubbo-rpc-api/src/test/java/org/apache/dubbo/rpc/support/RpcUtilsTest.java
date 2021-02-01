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
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcInvocation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.rpc.Constants.AUTO_ATTACH_INVOCATIONID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// OK
public class RpcUtilsTest {

    /**
     * regular scenario（ [səˈnɑːriəʊ]）: async invocation in URL
     * verify: 1. whether invocationId is set correctly, 2. idempotent or not
     *
     * 常规场景:URL异步调用
     * 验证:1.invocationId设置是否正确。2.幂等不
     */
    @Test
    public void testAttachInvocationIdIfAsync_normal() {
        // 注意test.async=true的test填充到methodParameters（<methodName:<key,val>>），进去看toMethodParameters方法
        URL url = URL.valueOf("dubbo://localhost/?test.async=true");
        Map<String, Object> attachments = new HashMap<>();
        attachments.put("aa", "bb");
        // 进去（这里的test和url?后面test是对应的，因为attachInvocationIdIfAsync内部有这个相关的逻辑）
        Invocation inv = new RpcInvocation("test", "DemoService", "", new Class[]{}, new String[]{}, attachments);
        // 进去（前面url是带有async=true的，肯定能被识别为异步，并给inv添加id）
        //
        // 注： 判断是否async的几种情况满足之一即可
        // (1)url含有invocationid.autoattach=true
        // (2)url含有async=true
        // (3)rpcInvocation.getInvokeMode() == InvokeMode.ASYNC;
        // (4)inv.getAttachment(ASYNC_KEY) = true
        RpcUtils.attachInvocationIdIfAsync(url, inv);
        // 进去
        long id1 = RpcUtils.getInvocationId(inv);
        // 再次添加，进去
        RpcUtils.attachInvocationIdIfAsync(url, inv);
        long id2 = RpcUtils.getInvocationId(inv);
        // 肯定相等，即幂等的
        assertEquals(id1, id2); // verify if it's idempotent
        // id1 = 0 。从0自增
        assertTrue(id1 >= 0);
        // 进去
        assertEquals("bb", attachments.get("aa"));
    }

    /**
     * scenario: sync invocation, no attachment added by default
     * verify: no id attribute added in attachment
     *
     * 场景:同步调用，默认不添加attachment
     * 验证:附件中没有添加id属性
     */
    @Test
    public void testAttachInvocationIdIfAsync_sync() {
        URL url = URL.valueOf("dubbo://localhost/");
        Invocation inv = new RpcInvocation("test", "DemoService", "", new Class[]{}, new String[]{});
        // 进去
        RpcUtils.attachInvocationIdIfAsync(url, inv);
        assertNull(RpcUtils.getInvocationId(inv));
    }

    /**
     * scenario: async invocation, add attachment by default
     * verify: no error report when the original attachment is null
     */
    // easy 还是记住判断是否async的那种情况
    @Test
    public void testAttachInvocationIdIfAsync_nullAttachments() {
        URL url = URL.valueOf("dubbo://localhost/?test.async=true");
        Invocation inv = new RpcInvocation("test", "DemoService", "", new Class[]{}, new String[]{});
        RpcUtils.attachInvocationIdIfAsync(url, inv);
        assertTrue(RpcUtils.getInvocationId(inv) >= 0L);
    }

    /**
     * scenario: explicitly configure to not add attachment
     * verify: no id attribute added in attachment
     */
    @Test
    public void testAttachInvocationIdIfAsync_forceNotAttache() {
        // 内部判断逻辑优先考虑AUTO_ATTACH_INVOCATIONID_KEY的值
        URL url = URL.valueOf("dubbo://localhost/?test.async=true&" + AUTO_ATTACH_INVOCATIONID_KEY + "=false");
        Invocation inv = new RpcInvocation("test", "DemoService", "", new Class[]{}, new String[]{});
        RpcUtils.attachInvocationIdIfAsync(url, inv);
        // AUTO_ATTACH_INVOCATIONID_KEY为false，所以没有id
        assertNull(RpcUtils.getInvocationId(inv));
    }

    /**
     * scenario: explicitly configure to add attachment
     * verify: id attribute added in attachment
     */
    // easy
    @Test
    public void testAttachInvocationIdIfAsync_forceAttache() {
        URL url = URL.valueOf("dubbo://localhost/?" + AUTO_ATTACH_INVOCATIONID_KEY + "=true");
        Invocation inv = new RpcInvocation("test", "DemoService", "", new Class[]{}, new String[]{});
        RpcUtils.attachInvocationIdIfAsync(url, inv);
        assertNotNull(RpcUtils.getInvocationId(inv));
    }

    @Test
    public void testGetReturnType() {
        Class<?> demoServiceClass = DemoService.class;
        String serviceName = demoServiceClass.getName();
        Invoker invoker = mock(Invoker.class);
        // /和 ?之间的在url内部被填充到属性path，?之后的被填充到 parameters（key为interface。value为org.apache.dubbo.rpc.support.DemoService）
        given(invoker.getUrl()).willReturn(URL.valueOf("test://127.0.0.1:1/org.apache.dubbo.rpc.support.DemoService?interface=org.apache.dubbo.rpc.support.DemoService"));

        // void sayHello(String name);
        RpcInvocation inv = new RpcInvocation("sayHello", serviceName, "", new Class<?>[]{String.class}, null,
                // invoker作为inv的属性，结合前面的方法名、参数等信息表明这个inv是想要调用invoker的url表示的接口的对应方法（不过通常是invoker.invoke(inv)这种调用，这里没触发）
                null, invoker, null);
        // 进去，内部会用到上面 url的parameters属性
        Class<?> returnType = RpcUtils.getReturnType(inv);
        Assertions.assertNull(returnType);

        //String echo(String text);
        RpcInvocation inv1 = new RpcInvocation("echo", serviceName, "", new Class<?>[]{String.class}, null, null, invoker, null);
        Class<?> returnType1 = RpcUtils.getReturnType(inv1);
        Assertions.assertNotNull(returnType1);
        Assertions.assertEquals(String.class, returnType1);

        //int getSize(String[] strs);
        RpcInvocation inv2 = new RpcInvocation("getSize", serviceName, "", new Class<?>[]{String[].class}, null, null, invoker, null);
        Class<?> returnType2 = RpcUtils.getReturnType(inv2);
        Assertions.assertNotNull(returnType2);
        Assertions.assertEquals(int.class, returnType2);

        //Person getPerson(Person person);
        RpcInvocation inv3 = new RpcInvocation("getPerson", serviceName, "", new Class<?>[]{Person.class}, null, null, invoker, null);
        Class<?> returnType3 = RpcUtils.getReturnType(inv3);
        Assertions.assertNotNull(returnType3);
        Assertions.assertEquals(Person.class, returnType3);

        //List<String> testReturnType1(String str);
        RpcInvocation inv4 = new RpcInvocation("testReturnType1", serviceName, "", new Class<?>[]{String.class}, null, null, invoker, null);
        Class<?> returnType4 = RpcUtils.getReturnType(inv4);
        Assertions.assertNotNull(returnType4);
        Assertions.assertEquals(List.class, returnType4);

    }

    @Test
    public void testGetReturnTypes() throws Exception {
        Class<?> demoServiceClass = DemoService.class;
        String serviceName = demoServiceClass.getName();
        Invoker invoker = mock(Invoker.class);
        given(invoker.getUrl()).willReturn(URL.valueOf("test://127.0.0.1:1/org.apache.dubbo.rpc.support.DemoService?interface=org.apache.dubbo.rpc.support.DemoService"));

        RpcInvocation inv = new RpcInvocation("testReturnType", serviceName, "", new Class<?>[]{String.class}, null, null, invoker, null);
        // 进去
        Type[] types = RpcUtils.getReturnTypes(inv);
        Assertions.assertNotNull(types);
        Assertions.assertEquals(2, types.length);
        Assertions.assertEquals(String.class, types[0]);
        Assertions.assertEquals(String.class, types[1]);
        Assertions.assertArrayEquals(types, inv.getReturnTypes());

        RpcInvocation inv1 = new RpcInvocation("testReturnType1", serviceName, "", new Class<?>[]{String.class}, null, null, invoker, null);

        // 进去
        java.lang.reflect.Type[] types1 = RpcUtils.getReturnTypes(inv1);
        // types1 = {Type[2]@2919}
        // 0 = {Class@222} "interface java.util.List"
        // 1 = {ParameterizedTypeImpl@2920} "java.util.List<java.lang.String>"
        Assertions.assertNotNull(types1);
        Assertions.assertEquals(2, types1.length);
        Assertions.assertEquals(List.class, types1[0]);
        Assertions.assertEquals(demoServiceClass.getMethod("testReturnType1", String.class).getGenericReturnType(), types1[1]);
        Assertions.assertArrayEquals(types1, inv1.getReturnTypes());

        RpcInvocation inv2 = new RpcInvocation("testReturnType2", serviceName, "", new Class<?>[]{String.class}, null, null, invoker, null);
        // 进去
        java.lang.reflect.Type[] types2 = RpcUtils.getReturnTypes(inv2);
        // types2 = {Type[2]@2922}
        // 0 = {Class@324} "class java.lang.String"
        // 1 = {Class@324} "class java.lang.String"
        Assertions.assertNotNull(types2);
        Assertions.assertEquals(2, types2.length);
        Assertions.assertEquals(String.class, types2[0]);
        Assertions.assertEquals(String.class, types2[1]);
        Assertions.assertArrayEquals(types2, inv2.getReturnTypes());

        RpcInvocation inv3 = new RpcInvocation("testReturnType3", serviceName, "", new Class<?>[]{String.class}, null, null, invoker, null);
        java.lang.reflect.Type[] types3 = RpcUtils.getReturnTypes(inv3);
        //types3 = {Type[2]@2924}
        // 0 = {Class@222} "interface java.util.List"
        // 1 = {ParameterizedTypeImpl@2926} "java.util.List<java.lang.String>"
        Assertions.assertNotNull(types3);
        Assertions.assertEquals(2, types3.length);
        Assertions.assertEquals(List.class, types3[0]);
        java.lang.reflect.Type genericReturnType3 = demoServiceClass.getMethod("testReturnType3", String.class).getGenericReturnType();
        Assertions.assertEquals(((ParameterizedType) genericReturnType3).getActualTypeArguments()[0], types3[1]);
        Assertions.assertArrayEquals(types3, inv3.getReturnTypes());

        RpcInvocation inv4 = new RpcInvocation("testReturnType4", serviceName, "", new Class<?>[]{String.class}, null, null, invoker, null);
        java.lang.reflect.Type[] types4 = RpcUtils.getReturnTypes(inv4);
        Assertions.assertNotNull(types4);
        Assertions.assertEquals(2, types4.length);
        Assertions.assertNull(types4[0]);
        Assertions.assertNull(types4[1]);
        Assertions.assertArrayEquals(types4, inv4.getReturnTypes());

        RpcInvocation inv5 = new RpcInvocation("testReturnType5", serviceName, "", new Class<?>[]{String.class}, null, null, invoker, null);
        // 进去
        java.lang.reflect.Type[] types5 = RpcUtils.getReturnTypes(inv5);
        Assertions.assertNotNull(types5);
        // types5 = {Type[2]@2930}
        // 0 = {Class@291} "interface java.util.Map"
        // 1 = {ParameterizedTypeImpl@2932} "java.util.Map<java.lang.String, java.lang.String>"
        Assertions.assertEquals(2, types5.length);
        Assertions.assertEquals(Map.class, types5[0]);
        java.lang.reflect.Type genericReturnType5 = demoServiceClass.getMethod("testReturnType5", String.class).getGenericReturnType();
        Assertions.assertEquals(((ParameterizedType) genericReturnType5).getActualTypeArguments()[0], types5[1]);
        Assertions.assertArrayEquals(types5, inv5.getReturnTypes());
    }

    @Test
    public void testGetParameterTypes() {
        Class<?> demoServiceClass = DemoService.class;
        String serviceName = demoServiceClass.getName();
        Invoker invoker = mock(Invoker.class);

        // void sayHello(String name);
        RpcInvocation inv1 = new RpcInvocation("sayHello", serviceName, "",
                new Class<?>[]{String.class}, null, null, invoker, null);
        // 进去
        Class<?>[] parameterTypes1 = RpcUtils.getParameterTypes(inv1);
        Assertions.assertNotNull(parameterTypes1);
        Assertions.assertEquals(1, parameterTypes1.length);
        Assertions.assertEquals(String.class, parameterTypes1[0]);

        //long timestamp();
        RpcInvocation inv2 = new RpcInvocation("timestamp", serviceName, "", null, null, null, invoker, null);
        Class<?>[] parameterTypes2 = RpcUtils.getParameterTypes(inv2);
        Assertions.assertEquals(0, parameterTypes2.length);

        //Type enumlength(Type... types);
        RpcInvocation inv3 = new RpcInvocation("enumlength", serviceName, "",
                new Class<?>[]{Type.class, Type.class}, null, null, invoker, null);
        Class<?>[] parameterTypes3 = RpcUtils.getParameterTypes(inv3);
        Assertions.assertNotNull(parameterTypes3);
        Assertions.assertEquals(2, parameterTypes3.length);
        Assertions.assertEquals(Type.class, parameterTypes3[0]);
        Assertions.assertEquals(Type.class, parameterTypes3[1]);

        //byte getbyte(byte arg);
        RpcInvocation inv4 = new RpcInvocation("getbyte", serviceName, "",
                new Class<?>[]{byte.class}, null, null, invoker, null);
        Class<?>[] parameterTypes4 = RpcUtils.getParameterTypes(inv4);
        Assertions.assertNotNull(parameterTypes4);
        Assertions.assertEquals(1, parameterTypes4.length);
        Assertions.assertEquals(byte.class, parameterTypes4[0]);

        //void $invoke(String s1, String s2);
        RpcInvocation inv5 = new RpcInvocation("$invoke", serviceName, "",
                // RpcInvocation第一个方法名称叫做$invoke方法，这就不同了！！此时原来这两个索引(下两个)位置参数意义就不是"方法参数类型数组"和"方法参数值数组"

                // 其实这里完整的是new Class<?>[]{String.class, String[].class,Object[].class}分别表示方法名、参数类型数组、参数值数组
                new Class<?>[]{String.class, String[].class},
                //             ↑                ↑
                new Object[]{"method", new String[]{"java.lang.String", "void", "java.lang.Object"}},
                null, invoker, null);
        // 进去
        Class<?>[] parameterTypes5 = RpcUtils.getParameterTypes(inv5);
        Assertions.assertNotNull(parameterTypes5);
        Assertions.assertEquals(3, parameterTypes5.length);
        Assertions.assertEquals(String.class, parameterTypes5[0]);
        Assertions.assertEquals(String.class, parameterTypes5[1]);
        Assertions.assertEquals(String.class, parameterTypes5[2]);
    }

    @ParameterizedTest
    @CsvSource({
            "echo",
            "stringLength",
            "testReturnType"
    })
    // 上面这个注解会循环三次调用
    public void testGetMethodName(String methodName) {
        Class<?> demoServiceClass = DemoService.class;
        String serviceName = demoServiceClass.getName();
        Invoker invoker = mock(Invoker.class);

        RpcInvocation inv1 = new RpcInvocation(methodName, serviceName, "",
                new Class<?>[]{String.class}, null, null, invoker, null);
        // 进去（和testGet_$invoke_Arguments的最后一个案例结合）
        String actual = RpcUtils.getMethodName(inv1);
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(methodName, actual);
    }

    @ParameterizedTest
    @CsvSource({
            "hello",
            "apache",
            "dubbo"
    })
    public void testGet_$invoke_MethodName(String method) {
        Class<?> demoServiceClass = DemoService.class;
        String serviceName = demoServiceClass.getName();
        Invoker invoker = mock(Invoker.class);

        RpcInvocation inv = new RpcInvocation("$invoke", serviceName, "",
                new Class<?>[]{String.class, String[].class},
                // method就是hello、apache、dubbo（循环三次）
                new Object[]{method, new String[]{"java.lang.String", "void", "java.lang.Object"}},
                null, invoker, null);
        // 返回的也是hello、apache、dubbo （和testGet_$invoke_Arguments的最后一个案例结合）
        String actual = RpcUtils.getMethodName(inv);
        Assertions.assertNotNull(actual);
        // 肯定相等
        Assertions.assertEquals(method, actual);

    }

    @Test
    public void testGet_$invoke_Arguments() {
        Object[] args = new Object[]{"hello", "dubbo", 520};
        Class<?> demoServiceClass = DemoService.class;
        String serviceName = demoServiceClass.getName();
        Invoker invoker = mock(Invoker.class);

        RpcInvocation inv = new RpcInvocation("$invoke", serviceName, "",
                new Class<?>[]{String.class, String[].class, Object[].class},
                new Object[]{"method", new String[]{}, args},
                null, invoker, null);

        // 进去（和testGet_$invoke_Arguments的最后一个案例结合）
        Object[] arguments = RpcUtils.getArguments(inv);
        for (int i = 0; i < args.length; i++) {
            Assertions.assertNotNull(arguments[i]);
            Assertions.assertEquals(args[i].getClass().getName(), arguments[i].getClass().getName());
            Assertions.assertEquals(args[i], arguments[i]);
        }
    }

}

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

package org.apache.dubbo.generic;


import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.metadata.definition.ServiceDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.definition.model.MethodDefinition;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.service.ComplexObject;
import org.apache.dubbo.service.DemoService;
import org.apache.dubbo.service.DemoServiceImpl;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenericServiceTest {

    public class ProxyFactory$Adaptive implements org.apache.dubbo.rpc.ProxyFactory {
        public org.apache.dubbo.rpc.Invoker getInvoker(java.lang.Object arg0, java.lang.Class arg1, org.apache.dubbo.common.URL arg2) throws org.apache.dubbo.rpc.RpcException {
            if (arg2 == null) throw new IllegalArgumentException("url == null");
            org.apache.dubbo.common.URL url = arg2;
            // 从url获取proxy参数的值，如果没有用默认javassist作为extName
            String extName = url.getParameter("proxy", "javassist");
            if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
            // 获取extName（比如javassist）扩展类实例 ----> ☆ 这点看出自适应扩展类的主要作用其实就是从url获取一些信息创建其他扩展类实例并调用目标方法
            org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
            // 调用扩展类的同名方法（自适应扩展类有点代理模式的意思）
            return extension.getInvoker(arg0, arg1, arg2);
        }
        public java.lang.Object getProxy(org.apache.dubbo.rpc.Invoker arg0, boolean arg1) throws org.apache.dubbo.rpc.RpcException {
            if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
            if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
            org.apache.dubbo.common.URL url = arg0.getUrl();
            String extName = url.getParameter("proxy", "javassist");
            if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
            org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
            return extension.getProxy(arg0, arg1);
        }
        public java.lang.Object getProxy(org.apache.dubbo.rpc.Invoker arg0) throws org.apache.dubbo.rpc.RpcException {
            if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
            if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
            org.apache.dubbo.common.URL url = arg0.getUrl();
            String extName = url.getParameter("proxy", "javassist");
            if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
            org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
            return extension.getProxy(arg0);
        }
    }

    public class Protocol$Adaptive implements org.apache.dubbo.rpc.Protocol {
        public java.util.List getServers()  {
            throw new UnsupportedOperationException("The method public default java.util.List org.apache.dubbo.rpc.Protocol.getServers() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
        }
        public void destroy()  {
            throw new UnsupportedOperationException("The method public abstract void org.apache.dubbo.rpc.Protocol.destroy() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
        }
        public int getDefaultPort()  {
            throw new UnsupportedOperationException("The method public abstract int org.apache.dubbo.rpc.Protocol.getDefaultPort() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
        }
        public org.apache.dubbo.rpc.Exporter export(org.apache.dubbo.rpc.Invoker arg0) throws org.apache.dubbo.rpc.RpcException {
            if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
            if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
            org.apache.dubbo.common.URL url = arg0.getUrl();
            String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
            if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
            org.apache.dubbo.rpc.Protocol extension = (org.apache.dubbo.rpc.Protocol)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension(extName);
            return extension.export(arg0);
        }
        public org.apache.dubbo.rpc.Invoker refer(java.lang.Class arg0, org.apache.dubbo.common.URL arg1) throws org.apache.dubbo.rpc.RpcException {
            if (arg1 == null) throw new IllegalArgumentException("url == null");
            org.apache.dubbo.common.URL url = arg1;
            String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
            if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
            org.apache.dubbo.rpc.Protocol extension = (org.apache.dubbo.rpc.Protocol)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension(extName);
            return extension.refer(arg0, arg1);
        }
    }
    @Test
    public void testGeneric() {
        DemoService server = new DemoServiceImpl();
        // 两个自适应类的源码我粘出来放到上面了，对后面的调用很关键，注意一下
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

        URL url = URL.valueOf("dubbo://127.0.0.1:5342/" + DemoService.class.getName() + "?version=1.0.0");
        // 去看下上面ProxyFactory$Adaptive的getInvoker方法，最后进入Javassist的getInvoker方法，进去
        Invoker<DemoService> invokerTemp = proxyFactory.getInvoker(server, DemoService.class, url);

        // 去看下上面Protocol$Adaptive的export方法，最后进入DubboProtocol的export方法，但是注意了！！！getExtension最后返回的其实是
        // QosProtocolWrapper，原因是因为在getExtension内部处理会调用createExtension(String name, boolean wrap)，且默认wrap是ture
        // 即扩展类实例（比如DubboProtocol）需要被包装，对于Protocol来说在loadClass的时候有三个WrapperClass（根据是否含有拷贝构造函数），
        // 分别是QosProtocolWrapper、ProtocolFilterWrapper、ProtocolListenerWrapper，按照@Activate(order=xx)的值以及WrapperComparator.COMPARATOR
        // 进行排序，然后千层饼一样包装（其实是责任链模式），最后就是这样的QosProtocolWrapper（ProtocolFilterWrapper（ProtocolListenerWrapper（DubboProtocol））））
        // 然后export一层层深入调用，每层加了自己的逻辑。 ----> 前面说的不对，getInvoker会进入StubProxyFactoryWrapper的getInvoker
        // 所以QosProtocolWrapper#export进去
        Exporter<DemoService> exporter = protocol.export(invokerTemp);
        Invoker<DemoService> invoker = protocol.refer(DemoService.class, url);

        GenericService client = (GenericService) proxyFactory.getProxy(invoker, true);
        Object result = client.$invoke("sayHello", new String[]{"java.lang.String"}, new Object[]{"haha"});
        Assertions.assertEquals("hello haha", result);

        org.apache.dubbo.rpc.service.GenericService newClient = (org.apache.dubbo.rpc.service.GenericService) proxyFactory.getProxy(invoker, true);
        Object res = newClient.$invoke("sayHello", new String[]{"java.lang.String"}, new Object[]{"hehe"});
        Assertions.assertEquals("hello hehe", res);
        invoker.destroy();
        exporter.unexport();
    }

    @Test
    public void testGeneric2() {
        DemoService server = new DemoServiceImpl();
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
        URL url = URL.valueOf("dubbo://127.0.0.1:5342/" + DemoService.class.getName() + "?version=1.0.0&generic=true$timeout=3000");
        Exporter<DemoService> exporter = protocol.export(proxyFactory.getInvoker(server, DemoService.class, url));
        Invoker<GenericService> invoker = protocol.refer(GenericService.class, url);

        GenericService client = proxyFactory.getProxy(invoker, true);
        Object result = client.$invoke("sayHello", new String[]{"java.lang.String"}, new Object[]{"haha"});
        Assertions.assertEquals("hello haha", result);

        Invoker<DemoService> invoker2 = protocol.refer(DemoService.class, url);

        GenericService client2 = (GenericService) proxyFactory.getProxy(invoker2, true);
        Object result2 = client2.$invoke("sayHello", new String[]{"java.lang.String"}, new Object[]{"haha"});
        Assertions.assertEquals("hello haha", result2);

        invoker.destroy();
        exporter.unexport();
    }

    @Test
    public void testGenericComplexCompute4FullServiceMetadata() {
        DemoService server = new DemoServiceImpl();
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
        URL url = URL.valueOf("dubbo://127.0.0.1:5342/" + DemoService.class.getName() + "?version=1.0.0&generic=true$timeout=3000");
        Exporter<DemoService> exporter = protocol.export(proxyFactory.getInvoker(server, DemoService.class, url));


        String var1 = "v1";
        int var2 = 234;
        long l = 555;
        String[] var3 = {"var31", "var32"};
        List<Integer> var4 = Arrays.asList(2, 4, 8);
        ComplexObject.TestEnum testEnum = ComplexObject.TestEnum.VALUE2;

        FullServiceDefinition fullServiceDefinition = ServiceDefinitionBuilder.buildFullDefinition(DemoService.class);
        MethodDefinition methodDefinition = getMethod("complexCompute", fullServiceDefinition.getMethods());
        Map mapObject = createComplexObject(fullServiceDefinition,var1, var2, l, var3, var4, testEnum);
        ComplexObject complexObject = map2bean(mapObject);

        Invoker<GenericService> invoker = protocol.refer(GenericService.class, url);


        GenericService client = proxyFactory.getProxy(invoker, true);
        Object result = client.$invoke(methodDefinition.getName(), methodDefinition.getParameterTypes(), new Object[]{"haha", mapObject});
        Assertions.assertEquals("haha###" + complexObject.toString(), result);


        Invoker<DemoService> invoker2 = protocol.refer(DemoService.class, url);
        GenericService client2 = (GenericService) proxyFactory.getProxy(invoker2, true);
        Object result2 = client2.$invoke("complexCompute", methodDefinition.getParameterTypes(), new Object[]{"haha2", mapObject});
        Assertions.assertEquals("haha2###" + complexObject.toString(), result2);

        invoker.destroy();
        exporter.unexport();
    }

    @Test
    public void testGenericFindComplexObject4FullServiceMetadata() {
        DemoService server = new DemoServiceImpl();
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
        URL url = URL.valueOf("dubbo://127.0.0.1:5342/" + DemoService.class.getName() + "?version=1.0.0&generic=true$timeout=3000");
        Exporter<DemoService> exporter = protocol.export(proxyFactory.getInvoker(server, DemoService.class, url));


        String var1 = "v1";
        int var2 = 234;
        long l = 555;
        String[] var3 = {"var31", "var32"};
        List<Integer> var4 = Arrays.asList(2, 4, 8);
        ComplexObject.TestEnum testEnum = ComplexObject.TestEnum.VALUE2;
        //ComplexObject complexObject = createComplexObject(var1, var2, l, var3, var4, testEnum);

        Invoker<GenericService> invoker = protocol.refer(GenericService.class, url);

        GenericService client = proxyFactory.getProxy(invoker, true);
        Object result = client.$invoke("findComplexObject", new String[]{"java.lang.String", "int", "long", "java.lang.String[]", "java.util.List", "org.apache.dubbo.service.ComplexObject$TestEnum"},
                new Object[]{var1, var2, l, var3, var4, testEnum});
        Assertions.assertNotNull(result);
        ComplexObject r = map2bean((Map) result);
        Assertions.assertEquals(r, createComplexObject(var1, var2, l, var3, var4, testEnum));

        invoker.destroy();
        exporter.unexport();
    }

    MethodDefinition getMethod(String methodName, List<MethodDefinition> list) {
        for (MethodDefinition methodDefinition : list) {
            if (methodDefinition.getName().equals(methodName)) {
                return methodDefinition;
            }
        }
        return null;
    }

    Map<String, Object> createComplexObject(FullServiceDefinition fullServiceDefinition, String var1, int var2, long l, String[] var3, List<Integer> var4, ComplexObject.TestEnum testEnum) {
        List<TypeDefinition> typeDefinitions = fullServiceDefinition.getTypes();
        TypeDefinition topTypeDefinition = null;
        TypeDefinition innerTypeDefinition = null;
        TypeDefinition inner2TypeDefinition = null;
        TypeDefinition inner3TypeDefinition = null;
        for (TypeDefinition typeDefinition : typeDefinitions) {
            if (typeDefinition.getType().equals(ComplexObject.class.getName())) {
                topTypeDefinition = typeDefinition;
            } else if (typeDefinition.getType().equals(ComplexObject.InnerObject.class.getName())) {
                innerTypeDefinition = typeDefinition;
            } else if (typeDefinition.getType().contains(ComplexObject.InnerObject2.class.getName())) {
                inner2TypeDefinition = typeDefinition;
            } else if (typeDefinition.getType().equals(ComplexObject.InnerObject3.class.getName())) {
                inner3TypeDefinition = typeDefinition;
            }
        }
        Assertions.assertEquals(topTypeDefinition.getProperties().get("v").getType(), "long");
        Assertions.assertEquals(topTypeDefinition.getProperties().get("maps").getType(), "java.util.Map<java.lang.String,java.lang.String>");
        Assertions.assertEquals(topTypeDefinition.getProperties().get("innerObject").getType(), "org.apache.dubbo.service.ComplexObject$InnerObject");
        Assertions.assertEquals(topTypeDefinition.getProperties().get("intList").getType(), "java.util.List<java.lang.Integer>");
        Assertions.assertEquals(topTypeDefinition.getProperties().get("strArrays").getType(), "java.lang.String[]");
        Assertions.assertEquals(topTypeDefinition.getProperties().get("innerObject3").getType(), "org.apache.dubbo.service.ComplexObject.InnerObject3[]");
        Assertions.assertEquals(topTypeDefinition.getProperties().get("testEnum").getType(), "org.apache.dubbo.service.ComplexObject.TestEnum");
        Assertions.assertEquals(topTypeDefinition.getProperties().get("innerObject2").getType(), "java.util.List<org.apache.dubbo.service.ComplexObject$InnerObject2>");

        Assertions.assertSame(innerTypeDefinition.getProperties().get("innerA").getType(), "java.lang.String");
        Assertions.assertSame(innerTypeDefinition.getProperties().get("innerB").getType(), "int");

        Assertions.assertSame(inner2TypeDefinition.getProperties().get("innerA2").getType(), "java.lang.String");
        Assertions.assertSame(inner2TypeDefinition.getProperties().get("innerB2").getType(), "int");

        Assertions.assertSame(inner3TypeDefinition.getProperties().get("innerA3").getType(), "java.lang.String");

        Map<String, Object> result = new HashMap<>();
        result.put("v", l);
        Map maps = new HashMap<>(4);
        maps.put(var1 + "_k1", var1 + "_v1");
        maps.put(var1 + "_k2", var1 + "_v2");
        result.put("maps", maps);
        result.put("intList", var4);
        result.put("strArrays", var3);
        result.put("testEnum", testEnum.name());

        Map innerObjectMap = new HashMap<>(4);
        result.put("innerObject", innerObjectMap);
        innerObjectMap.put("innerA", var1);
        innerObjectMap.put("innerB", var2);

        List<Map> innerObject2List = new ArrayList<>();
        result.put("innerObject2", innerObject2List);
        Map innerObject2Tmp1 = new HashMap<>(4);
        innerObject2Tmp1.put("innerA2", var1 + "_21");
        innerObject2Tmp1.put("innerB2", var2 + 100000);
        Map innerObject2Tmp2 = new HashMap<>(4);
        innerObject2Tmp2.put("innerA2", var1 + "_22");
        innerObject2Tmp2.put("innerB2", var2 + 200000);
        innerObject2List.add(innerObject2Tmp1);
        innerObject2List.add(innerObject2Tmp2);

        Map innerObject3Tmp1 = new HashMap<>(4);
        innerObject3Tmp1.put("innerA3", var1 + "_31");
        Map innerObject3Tmp2 = new HashMap<>(4);
        innerObject3Tmp2.put("innerA3", var1 + "_32");
        Map innerObject3Tmp3 = new HashMap<>(4);
        innerObject3Tmp3.put("innerA3", var1 + "_32");
        result.put("innerObject3", new Map[]{innerObject3Tmp1, innerObject3Tmp2, innerObject3Tmp3});

        return result;
    }

    Map<String, Object> bean2Map(ComplexObject complexObject) {
        return JSON.parseObject(JSON.toJSONString(complexObject), Map.class);
    }

    ComplexObject map2bean(Map<String, Object> map) {
        return JSON.parseObject(JSON.toJSONString(map), ComplexObject.class);
    }

    ComplexObject createComplexObject(String var1, int var2, long l, String[] var3, List<Integer> var4, ComplexObject.TestEnum testEnum) {
        return new ComplexObject(var1, var2, l, var3, var4, testEnum);
    }

}

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
package org.apache.dubbo.rpc.protocol.dubbo;


import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.protocol.dubbo.support.DemoService;
import org.apache.dubbo.rpc.protocol.dubbo.support.DemoServiceImpl;
import org.apache.dubbo.rpc.protocol.dubbo.support.NonSerialized;
import org.apache.dubbo.rpc.protocol.dubbo.support.ProtocolUtils;
import org.apache.dubbo.rpc.protocol.dubbo.support.RemoteService;
import org.apache.dubbo.rpc.protocol.dubbo.support.RemoteServiceImpl;
import org.apache.dubbo.rpc.protocol.dubbo.support.Type;
import org.apache.dubbo.rpc.service.EchoService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <code>ProxiesTest</code>
 */

public class DubboProtocolTest {
    private Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    private ProxyFactory proxy = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();


    @BeforeAll
    public static void setup() {
        // 进去
        ApplicationModel.getServiceRepository().registerService(DemoService.class);
    }

    @AfterAll
    public static void after() {
        ProtocolUtils.closeAll();// 进去
        ApplicationModel.getServiceRepository().unregisterService(DemoService.class); // 进去
    }

    @Test
    public void testDemoProtocol() throws Exception {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        // 这里看出了ProxyFactory、Protocol、Invoker怎么交互的，非常重要！！！

        // export
        Invoker<DemoService> invoker = proxy.getInvoker(service, DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange"));
        Exporter<DemoService> exporter = protocol.export(invoker);

        // refer
        Invoker<DemoService> serviceInvoker = protocol.refer(DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange").addParameter("timeout",
                3000L));
        service = proxy.getProxy(serviceInvoker);

        // 调用方法
        assertEquals(service.getSize(new String[]{"", "", ""}), 3);
    }

    @Test
    public void testDubboProtocol() throws Exception {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(service, DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName())));
        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName()).addParameter("timeout",
                3000*10000)));
        assertEquals(service.enumlength(new Type[]{}), Type.Lower);
        assertEquals(service.getSize(null), -1);
        assertEquals(service.getSize(new String[]{"", "", ""}), 3);
        Map<String, String> map = new HashMap<String, String>();
        map.put("aa", "bb");
        Set<String> set = service.keys(map);
        assertEquals(set.size(), 1);
        assertEquals(set.iterator().next(), "aa");
        service.invoke("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "", "invoke");

        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?client=netty").addParameter("timeout",
                3000*10000)));
        // test netty client
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < 1024 * 32 + 32; i++)
            buf.append('A');
        System.out.println(service.stringLength(buf.toString()));

        // cast to EchoService
        EchoService echo = proxy.getProxy(protocol.refer(EchoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?client=netty").addParameter("timeout",
                3000*10000)));
        assertEquals(echo.$echo(buf.toString()), buf.toString());
        assertEquals(echo.$echo("test"), "test");
        assertEquals(echo.$echo("abcdefg"), "abcdefg");
        assertEquals(echo.$echo(1234), 1234);
    }

    @Test
    public void testDubboProtocolWithMina() throws Exception {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(service, DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName()).addParameter(Constants.SERVER_KEY, "mina")));
        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName()).addParameter(Constants.CLIENT_KEY, "mina").addParameter("timeout",
                3000L)));
        for (int i = 0; i < 10; i++) {
            assertEquals(service.enumlength(new Type[]{}), Type.Lower);
            assertEquals(service.getSize(null), -1);
            assertEquals(service.getSize(new String[]{"", "", ""}), 3);
        }
        Map<String, String> map = new HashMap<String, String>();
        map.put("aa", "bb");
        for (int i = 0; i < 10; i++) {
            Set<String> set = service.keys(map);
            assertEquals(set.size(), 1);
            assertEquals(set.iterator().next(), "aa");
            service.invoke("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "", "invoke");
        }

        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?client=mina").addParameter("timeout",
                3000L)));
        // test netty client
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < 1024 * 32 + 32; i++)
            buf.append('A');
        System.out.println(service.stringLength(buf.toString()));

        // cast to EchoService
        EchoService echo = proxy.getProxy(protocol.refer(EchoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?client=mina").addParameter("timeout",
                3000L)));
        for (int i = 0; i < 10; i++) { // 注意服务端的EchoFilter对$echo方法调用的特殊处理，直接不走invoker.invoke了
            assertEquals(echo.$echo(buf.toString()), buf.toString());
            assertEquals(echo.$echo("test"), "test");
            assertEquals(echo.$echo("abcdefg"), "abcdefg");
            assertEquals(echo.$echo(1234), 1234);
        }
    }

    @Test
    public void testDubboProtocolMultiService() throws Exception {
//        DemoService service = new DemoServiceImpl();
//        protocol.export(proxy.getInvoker(service, DemoService.class, URL.valueOf("dubbo://127.0.0.1:9010/" + DemoService.class.getName())));
//        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("dubbo://127.0.0.1:9010/" + DemoService.class.getName()).addParameter("timeout",
//                3000L)));

        RemoteService remote = new RemoteServiceImpl();

        ApplicationModel.getServiceRepository().registerService(RemoteService.class);

        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(remote, RemoteService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + RemoteService.class.getName())));
        remote = proxy.getProxy(protocol.refer(RemoteService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + RemoteService.class.getName()).addParameter("timeout",
                3000L)));

//        service.sayHello("world");

        // test netty client
//        assertEquals("world", service.echo("world"));
        assertEquals("hello world@" + RemoteServiceImpl.class.getName(), remote.sayHello("world"));

//       can't find target service addresses
        // 这里可以强转的原因是remote是proxy0，其实现的接口列表有AbstractProxyFactory默认指定的这两个Destroyable.class, EchoService.class
        // 强转后依然可以调用$echo，因为服务端有一个EchoFilter进行了特殊处理，不会实际往深了走，即从exporterMap找invoker啥的然后调用啥的
        EchoService remoteEecho = (EchoService) remote;
        assertEquals(remoteEecho.$echo("ok"), "ok");
    }

    @Test
    public void testPerm() throws Exception {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(service, DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange")));
        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange").addParameter("timeout",
                3000L)));
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++)
            service.getSize(new String[]{"", "", ""});
        System.out.println("take:" + (System.currentTimeMillis() - start));
    }

    @Test
    public void testNonSerializedParameter() throws Exception {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(service, DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange")));
        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange").addParameter("timeout",
                3000L)));
        try {
            // 在DubboCodec进行encodeRequestData时候进行out.writeAttachments(inv.getArguments())（该new NonSerialized()会
            // 保存到inv的属性中）的时候发现是实体类型/引用类型，非基本数据类型，却没有实现序列化接口，netty的encode就会抛异常
            service.nonSerializedParameter(new NonSerialized());
            Assertions.fail();
        } catch (RpcException e) {
            Assertions.assertTrue(e.getMessage().contains("org.apache.dubbo.rpc.protocol.dubbo.support.NonSerialized must implement java.io.Serializable"));
        }
    }

    @Test
    public void testReturnNonSerialized() throws Exception {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        protocol.export(proxy.getInvoker(service, DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange")));
        service = proxy.getProxy(protocol.refer(DemoService.class, URL.valueOf("dubbo://127.0.0.1:" + port + "/" + DemoService.class.getName() + "?codec=exchange").addParameter("timeout",
                3000L)));
        try {
            service.returnNonSerialized();
            Assertions.fail();
        } catch (RpcException e) {
            Assertions.assertTrue(e.getMessage().contains("org.apache.dubbo.rpc.protocol.dubbo.support.NonSerialized must implement java.io.Serializable"));
        }
    }

    @Test
    public void testRemoteApplicationName() throws Exception {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        URL url = URL.valueOf("dubbo://127.0.0.1:" + 9999 + "/" + DemoService.class.getName() + "?codec=exchange").addParameter("timeout",
                100*3000L).addParameter("application", "consumer");
        protocol.export(proxy.getInvoker(service, DemoService.class, url));
        service = proxy.getProxy(protocol.refer(DemoService.class, url));
        assertEquals(service.getRemoteApplicationName(), "consumer");
    }
}

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
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.protocol.dubbo.support.DemoService;
import org.apache.dubbo.rpc.protocol.dubbo.support.DemoServiceImpl;
import org.apache.dubbo.rpc.protocol.dubbo.support.ProtocolUtils;
import org.apache.dubbo.rpc.service.EchoService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RpcFilterTest {
    private Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    private ProxyFactory proxy = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    @AfterEach
    public void after() {
        ProtocolUtils.closeAll();
    }

    @Test
    public void testRpcFilter() throws Exception {
        DemoService service = new DemoServiceImpl();
        int port = NetUtils.getAvailablePort();
        URL url = URL.valueOf("dubbo://127.0.0.1:" + port + "/org.apache.dubbo.rpc.protocol.dubbo.support.DemoService?service.filter=echo");
        ApplicationModel.getServiceRepository().registerService(DemoService.class);
        // 再说一下 比如 url = "xxxx://ip:port/test?..."如果注册的时候(即上面registerService)没有指定test这个path，会抛异常，因为如果发现"有参数"的调用，
        // 在DecodeableRpcInvocation内部会从ApplicationModel.getServiceRepository().lookUp(path)，而你没指定，默认的path就是接口全限定名称
        // 就抛异常了。如果非带有参数的调用，/xxx随便写，也不需要registerService，直接就能调用成功，因为不会走DecodeableRpcInvocation内部那个逻辑
        // 直接到requestHandler的reply....不说了
        protocol.export(proxy.getInvoker(service, DemoService.class, url));
        service = proxy.getProxy(protocol.refer(DemoService.class, url));
        Assertions.assertEquals("123", service.echo("123"));
        // cast to EchoService
        EchoService echo = proxy.getProxy(protocol.refer(EchoService.class, url));
        Assertions.assertEquals(echo.$echo("test"), "test");
        Assertions.assertEquals(echo.$echo("abcdefg"), "abcdefg");
        Assertions.assertEquals(echo.$echo(1234), 1234);
    }

}
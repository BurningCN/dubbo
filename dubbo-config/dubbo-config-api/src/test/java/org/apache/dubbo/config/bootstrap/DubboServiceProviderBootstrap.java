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
package org.apache.dubbo.config.bootstrap;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.rest.UserService;
import org.apache.dubbo.config.bootstrap.rest.UserServiceImpl;

import java.util.Arrays;

/**
 * Dubbo Provider Bootstrap
 *
 * @since 2.7.5
 */
public class DubboServiceProviderBootstrap {

    public static void main(String[] args) {
        multipleRegistries();
    }

    private static void multipleRegistries() {
        ProtocolConfig restProtocol = new ProtocolConfig();
        restProtocol.setName("rest");
        restProtocol.setId("rest");
        restProtocol.setPort(-1);

        RegistryConfig interfaceRegistry = new RegistryConfig();
        interfaceRegistry.setId("interfaceRegistry");
        interfaceRegistry.setAddress("zookeeper://127.0.0.1:2181");

        RegistryConfig serviceRegistry = new RegistryConfig();
        serviceRegistry.setId("serviceRegistry");
        serviceRegistry.setAddress("zookeeper://127.0.0.1:2181?registry-type=service");

        ServiceConfig<EchoService> echoService = new ServiceConfig<>();
        echoService.setInterface(EchoService.class.getName());
        echoService.setRef(new EchoServiceImpl());
//        echoService.setRegistries(Arrays.asList(interfaceRegistry, serviceRegistry));

        ServiceConfig<UserService> userService = new ServiceConfig<>();
        userService.setInterface(UserService.class.getName());
        userService.setRef(new UserServiceImpl());
        userService.setProtocol(restProtocol);
//        userService.setRegistries(Arrays.asList(interfaceRegistry, serviceRegistry));

        ApplicationConfig applicationConfig = new ApplicationConfig("dubbo-provider-demo");
        // 注意getMetadataType的调用点
        applicationConfig.setMetadataType("remote");
        DubboBootstrap.getInstance()
                .application(applicationConfig)
                // 指定了app级别的registry，前面两个service没有指定了自己的registry，那么都会用这个（在loadRegistries方法）
                .registries(Arrays.asList(interfaceRegistry, serviceRegistry))
                // 指定了app级别的protocol，前面 userService.setProtocol(restProtocol); 指定了 Service级别的protocol，注意两个service的export过程，
                // 再结合前面两个registry（ConfigurableMetadataServiceExporter 暴露两次，他的protocol
                // 不在乎用户配置的，而是dubbo协议，详见generateMetadataProtocol）
                // echoService的protocol使用的是下面的（详见convertProtocolIdsToProtocols 第三行代码）， userService 使用的是restProtocol
                .protocol(builder -> builder.port(-1).name("dubbo"))
                // 和上面 applicationConfig.setMetadataType("remote");
                .metadataReport(new MetadataReportConfig("zookeeper://127.0.0.1:2181"))
                .service(echoService)
                .service(userService)
                .start()
                .await();
        // 进去看程序的时候，重点关注的就是两个registry 和两个 protocol
    }

    private static void testSCCallDubbo() {

    }

    private static void testDubboCallSC() {

    }

    private static void testDubboTansormation() {

    }

}

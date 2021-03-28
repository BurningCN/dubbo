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
package org.apache.dubbo.config.spring.context.annotation;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ModuleConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;

/**
 * {@link DubboConfigConfiguration} Test
 *
 * @since 2.5.8
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DubboConfigConfigurationTest {

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    public void before() throws IOException {

        context = new AnnotationConfigApplicationContext();
        ResourcePropertySource propertySource = new ResourcePropertySource("META-INF/config.properties");
        context.getEnvironment().getPropertySources().addFirst(propertySource);

    }

    @AfterEach
    public void after() {
        context.close();
    }

    @Test
    public void testSingle() throws IOException {

        context.register(DubboConfigConfiguration.Single.class);
        context.refresh();

        // application 一个疑问，不知道在properties配置的dubbo.application.id是怎么就变成了bean name，就是根据prefix后的id值！！！同时这个id还会赋值给AbstractConfig的id
        // 如果我把dubbo.application.id去掉，那么就是org.apache.dubbo.config.ApplicationConfig#0"
        /*
            0 = "org.springframework.context.annotation.internalConfigurationAnnotationProcessor"
            1 = "org.springframework.context.annotation.internalAutowiredAnnotationProcessor"
            2 = "org.springframework.context.annotation.internalCommonAnnotationProcessor"
            3 = "org.springframework.context.event.internalEventListenerProcessor"
            4 = "org.springframework.context.event.internalEventListenerFactory"
            5 = "dubboConfigConfiguration.Single"

            6 = "applicationBean"
            7 = "configurationBeanBindingPostProcessor"
            8 = "moduleBean"
            9 = "org.apache.dubbo.config.RegistryConfig#0"
            10 = "org.apache.dubbo.config.ProtocolConfig#0"
            11 = "org.apache.dubbo.config.MonitorConfig#0"
            12 = "org.apache.dubbo.config.ProviderConfig#0"
            13 = "org.apache.dubbo.config.ConsumerConfig#0"
        */
        ApplicationConfig applicationConfig = context.getBean("applicationBean", ApplicationConfig.class);
        Assertions.assertEquals("dubbo-demo-application", applicationConfig.getName());
        System.out.println(applicationConfig.getId());
        System.out.println( applicationConfig.getPrefix());


        // module
        ModuleConfig moduleConfig = context.getBean("moduleBean", ModuleConfig.class);
        Assertions.assertEquals("dubbo-demo-module", moduleConfig.getName());

        // registry
        RegistryConfig registryConfig = context.getBean(RegistryConfig.class);
        Assertions.assertEquals("zookeeper://192.168.99.100:32770", registryConfig.getAddress());

        // protocol
        ProtocolConfig protocolConfig = context.getBean(ProtocolConfig.class);
        Assertions.assertEquals("dubbo", protocolConfig.getName());
        Assertions.assertEquals(Integer.valueOf(20880), protocolConfig.getPort());
    }

    @Test
    public void testMultiple() {

        context.register(DubboConfigConfiguration.Multiple.class);
        context.refresh();
        // 这里的bean的name就是prefix后的
        /*
                1 = "org.springframework.context.annotation.internalAutowiredAnnotationProcessor"
                2 = "org.springframework.context.annotation.internalCommonAnnotationProcessor"
                3 = "org.springframework.context.event.internalEventListenerProcessor"
                4 = "org.springframework.context.event.internalEventListenerFactory"
                5 = "dubboConfigConfiguration.Multiple"
                6 = "applicationBean3"
                7 = "applicationBean"
                8 = "applicationBean2"
                9 = "configurationBeanBindingPostProcessor"
                10 = "thrift"
                11 = "rest"
          */
        // application
        ApplicationConfig applicationConfig = context.getBean("applicationBean", ApplicationConfig.class);
        Assertions.assertEquals("dubbo-demo-application", applicationConfig.getName());

        ApplicationConfig applicationBean2 = context.getBean("applicationBean2", ApplicationConfig.class);
        Assertions.assertEquals("dubbo-demo-application2", applicationBean2.getName());

        ApplicationConfig applicationBean3 = context.getBean("applicationBean3", ApplicationConfig.class);
        Assertions.assertEquals("dubbo-demo-application3", applicationBean3.getName());

    }

}

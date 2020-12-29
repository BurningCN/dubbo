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
package org.apache.dubbo.config.spring.extension;

import org.apache.dubbo.config.spring.api.DemoService;
import org.apache.dubbo.config.spring.api.HelloService;
import org.apache.dubbo.config.spring.impl.DemoServiceImpl;
import org.apache.dubbo.config.spring.impl.HelloServiceImpl;
import org.apache.dubbo.rpc.Protocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;

@Configuration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SpringExtensionFactoryTest {

    private SpringExtensionFactory springExtensionFactory = new SpringExtensionFactory();

    // 两个IOC容器
    private AnnotationConfigApplicationContext context1;
    private AnnotationConfigApplicationContext context2;

    @Bean("bean1") // 这个bean1在@Bean注解里面描述的是name，一个ioc容器里面的bean的name值必须唯一
    public DemoService bean1() {
        return new DemoServiceImpl();
    }

    @Bean("bean2")
    public DemoService bean2() {
        return new DemoServiceImpl();
    }

    @Bean("hello")
    public HelloService helloService() {
        return new HelloServiceImpl();
    }
    @BeforeEach
    public void init() {
        // 清除所有缓存的ioc容器，进去
        SpringExtensionFactory.clearContexts();

        context1 = new AnnotationConfigApplicationContext();
        // 注册当前Test类的三个bean到ioc容器（@Configuration+3个@Bean）
        context1.register(getClass());
        context1.refresh();

        context2 = new AnnotationConfigApplicationContext();
        // 注意BeanForContext2内部注册了和本Test类中一个同名的bean，即"bean1"的DemoServiceImpl实例bean，但是这两个同名的bean是在两个ioc容器中的，
        // 所以不会被覆盖掉（同一个容器的@Bean(xx)的xx值要唯一），后面测试方法取的时候在springExtensionFactory循环遍历ioc容器取的，肯定第一个ioc容器先获取成功
        context2.register(BeanForContext2.class);
        context2.refresh();

        // 添加ioc容器到缓冲，进去
        SpringExtensionFactory.addApplicationContext(context1);
        SpringExtensionFactory.addApplicationContext(context2);
    }

    @Test
    public void testGetExtensionBySPI() {
        // 内部返回null，因为SPI接口扩展类要从SpiExtensionFactory获取，进去
        Protocol protocol = springExtensionFactory.getExtension(Protocol.class, "protocol");
        Assertions.assertNull(protocol);
    }

    @Test
    public void testGetExtensionByName() {
        // 根据接口和beanName获取Bean，前面@Bean注册了DemoServiceImpl。
        DemoService bean = springExtensionFactory.getExtension(DemoService.class, "bean1");
        Assertions.assertNotNull(bean);
        HelloService hello = springExtensionFactory.getExtension(HelloService.class, "hello");
        Assertions.assertNotNull(hello);
    }

    @AfterEach

    public void destroy() {
        // 清除缓存
        SpringExtensionFactory.clearContexts();
        // 内部会执行shutdownook，在before方法里面注册了hook，进去
        context1.close();
        context2.close();
    }


}

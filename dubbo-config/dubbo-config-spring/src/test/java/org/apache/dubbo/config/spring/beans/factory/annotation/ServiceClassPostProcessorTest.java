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
package org.apache.dubbo.config.spring.beans.factory.annotation;

import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.api.HelloService;
import org.apache.dubbo.rpc.model.ApplicationModel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

/**
 * {@link ServiceClassPostProcessor} Test
 *
 * @since 2.7.7
 */
// @ContextConfiguration 主要是加载配置文件或者加载配置类，这里我们是加载配置类。这两个配置类的里面@Bean注解都会注册相关bean到spring容器。
//
//@TestPropertySource，主要是给spring context添加了两个kv配置属性（就像以往application.properties那样），注意packagesToScan属性值为${provider.package}，Environment能解析出来。
    // 有一个ConfigurableListableBeanFactory属性，这个就是spring bean容器，后面的test测试程序需要通过这个来（根据bean类型）拿到相关的bean。
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = {
                ServiceAnnotationTestConfiguration2.class, // 注意
                ServiceClassPostProcessorTest.class
        })
@TestPropertySource(properties = {
        "provider.package = org.apache.dubbo.config.spring.context.annotation.provider",
        "packagesToScan = ${provider.package}",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ServiceClassPostProcessorTest {

    @BeforeEach
    public void setUp() {
        ApplicationModel.reset();
    }

    @BeforeEach
    public void tearDown() {
        ApplicationModel.reset();
    }

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Bean
    public ServiceClassPostProcessor serviceClassPostProcessor2
            (@Value("${packagesToScan}") String... packagesToScan) {
        return new ServiceClassPostProcessor(packagesToScan);
    }

    @Test
    public void test() {
        Map<String, HelloService> helloServicesMap = beanFactory.getBeansOfType(HelloService.class);

        Assertions.assertEquals(2, helloServicesMap.size());

        Map<String, ServiceBean> serviceBeansMap = beanFactory.getBeansOfType(ServiceBean.class);

        Assertions.assertEquals(2, serviceBeansMap.size());

        Map<String, ServiceClassPostProcessor> beanPostProcessorsMap =
                beanFactory.getBeansOfType(ServiceClassPostProcessor.class);

        Assertions.assertEquals(2, beanPostProcessorsMap.size());

        Assertions.assertTrue(beanPostProcessorsMap.containsKey("serviceClassPostProcessor"));
        Assertions.assertTrue(beanPostProcessorsMap.containsKey("serviceClassPostProcessor2"));

    }

    @Test
    public void testMethodAnnotation() {

        Map<String, ServiceBean> serviceBeansMap = beanFactory.getBeansOfType(ServiceBean.class);

        // ServiceBean类型的实例bean只有两个，是根据DefaultHelloService和DemoServiceImpl生成的，而 HelloServiceImpl 和DefaultHelloService
        // 都是实现HelloService接口，且他们都没有配置group+version，而且interfaceClassName还是一致的，所以HelloServiceImpl的ServiceBean不会注册成功。
        // 除非你去加一个group、version区分一下
        Assertions.assertEquals(2, serviceBeansMap.size());

        ServiceBean demoServiceBean = serviceBeansMap.get("ServiceBean:org.apache.dubbo.config.spring.api.DemoService:2.5.7");

        Assertions.assertNotNull(demoServiceBean.getMethods());

    }

}

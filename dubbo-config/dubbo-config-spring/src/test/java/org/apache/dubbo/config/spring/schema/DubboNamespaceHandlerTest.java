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
package org.apache.dubbo.config.spring.schema;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ModuleConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.spring.ConfigTest;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.api.DemoService;
import org.apache.dubbo.config.spring.impl.DemoServiceImpl;
import org.apache.dubbo.rpc.model.ApplicationModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DubboNamespaceHandlerTest {
    @BeforeEach
    public void setUp() {
        ApplicationModel.reset();
    }

    @AfterEach
    public void tearDown() {
        ApplicationModel.reset();
    }

    // 三个注解的合用法，引入xml文件，xml相关的bean会注入到ioc容器
    @Configuration
    @PropertySource("classpath:/META-INF/demo-provider.properties") // properties文件的值会替换demo-provider.xml占位符的值
    @ImportResource(locations = "classpath:/org/apache/dubbo/config/spring/demo-provider.xml")
    static class XmlConfiguration {

    }

    @Test
    public void testProviderXmlOnConfigurationClass() {
        // 三步骤 就能把XmlConfiguration配置类对应的xml内容相关的bean注入到容器
        // AnnotationConfigApplicationContext是IOC容器ApplicationContext的一种实现，处理注解式的，就是上面的XmlConfiguration
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(XmlConfiguration.class);
        applicationContext.refresh();

        // 上面执行后demo-provider.xml相关的bean就注入了IOC，下面测试方法进去
        testProviderXml(applicationContext);
        applicationContext.close();
    }

    @Test
    public void testProviderXml() {
        // 和上面AnnotationConfigApplicationContext类比，ClassPathXmlApplicationContext也是ApplicationContext的一种实现

        // org/apache/dubbo/config/spring/demo-provider.xml
        // org/apache/dubbo/config/spring/demo-provider-properties.xml
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(
                ConfigTest.class.getPackage().getName().replace('.', '/') + "/demo-provider.xml",
                ConfigTest.class.getPackage().getName().replace('.', '/') + "/demo-provider-properties.xml"
        );
        ctx.start();
        testProviderXml(ctx);
        ctx.close();
    }

    // 被前面两个调用
    private void testProviderXml(ApplicationContext context) {
        // 从容器中获取根据Class获取bean实例
        ProtocolConfig protocolConfig = context.getBean(ProtocolConfig.class);
        assertThat(protocolConfig, not(nullValue()));
        assertThat(protocolConfig.getName(), is("dubbo"));
        assertThat(protocolConfig.getPort(), is(20813));

        ApplicationConfig applicationConfig = context.getBean(ApplicationConfig.class);
        assertThat(applicationConfig, not(nullValue()));
        assertThat(applicationConfig.getName(), is("demo-provider"));

        DemoService service = context.getBean(DemoService.class);
        assertThat(service, not(nullValue()));
    }

    @Test
    public void testMultiProtocol() {
        // org/apache/dubbo/config/spring/multi-protocol.xml
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(ConfigTest.class.getPackage().getName().replace('.', '/') + "/multi-protocol.xml");
        ctx.start();

        // 注意getBeansOfType api（还有getBean），获取ctx ioc容器类型为ProtocolConfig的所有Bean，返回的map k为beanId,v为具体实例
        // 返回的两个，因为multi-protocol.xml里面有两个Protocol，会对应生成两个ProtoConfig，详见DubboBeanDefinitionParser
        Map<String, ProtocolConfig> protocolConfigMap = ctx.getBeansOfType(ProtocolConfig.class);
        assertThat(protocolConfigMap.size(), is(2));

        ProtocolConfig rmiProtocolConfig = protocolConfigMap.get("rmi");
        assertThat(rmiProtocolConfig.getPort(), is(10991));

        ProtocolConfig dubboProtocolConfig = protocolConfigMap.get("dubbo");
        assertThat(dubboProtocolConfig.getPort(), is(20881));
        ctx.close();
    }

    @Test
    public void testDefaultProtocol() {
        // testDefaultProtocol override-protocol.xml的Protocol标签没有name属性
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(ConfigTest.class.getPackage().getName().replace('.', '/') + "/override-protocol.xml");
        ctx.start();

        ProtocolConfig protocolConfig = ctx.getBean(ProtocolConfig.class);
        protocolConfig.refresh();
        // 默认是dubbo
        assertThat(protocolConfig.getName(), is("dubbo"));
        ctx.close();
    }

    @Test
    public void testCustomParameter() {
        // 测试用户自定义属性，看看override-protocol.xml

        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(ConfigTest.class.getPackage().getName().replace('.', '/') + "/customize-parameter.xml");
        ctx.start();

        ProtocolConfig protocolConfig = ctx.getBean(ProtocolConfig.class);
        assertThat(protocolConfig.getParameters().size(), is(1));
        // 获取自定义属性kv
        assertThat(protocolConfig.getParameters().get("protocol-paramA"), is("protocol-paramA"));

        ServiceBean serviceBean = ctx.getBean(ServiceBean.class);
        assertThat(serviceBean.getParameters().size(), is(1));
        assertThat(serviceBean.getParameters().get("service-paramA"), is("service-paramA"));
        ctx.close();
    }


    @Test
    public void testDelayFixedTime() {
        // delay-fixed-time.xml 里面service标签多了一个delay属性

        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("classpath:/" +
                ConfigTest.class.getPackage().getName().replace('.', '/') + "/delay-fixed-time.xml");
        ctx.start();

        // 在内部的if (isPrimitive(type)) 得到赋值
        assertThat(ctx.getBean(ServiceBean.class).getDelay(), is(300));
        ctx.close();
    }

    @Test
    public void testTimeoutConfig() {
        // provider-nested-service.xml 看下，注意两个provider
        // 本来可以直接dubbo:service不需要provider，加provider的作用就是可以多添加一些控制属性
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(ConfigTest.class.getPackage().getName().replace('.', '/') +
                "/provider-nested-service.xml");
        ctx.start();

        Map<String, ProviderConfig> providerConfigMap = ctx.getBeansOfType(ProviderConfig.class);

        // 多个provider标签，没有指定id的话，默认是对应beanClass名称，这里两个id分别是：
        // org.apache.dubbo.config.ProviderConfig
        // org.apache.dubbo.config.ProviderConfig2
        assertThat(providerConfigMap.get("org.apache.dubbo.config.ProviderConfig").getTimeout(), is(2000));
        ctx.close();
    }

    @Test
    public void testMonitor() {
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(ConfigTest.class.getPackage().getName().replace('.', '/') +
                "/provider-with-monitor.xml");
        ctx.start();
        MonitorConfig bean = ctx.getBean(MonitorConfig.class);
        assertThat(bean, not(nullValue()));
        assertEquals(bean.isDefault(),true); // provider-with-monitor.xml里面给monitor标签配置了default=true属性
        ctx.close();
    }

//    @Test
//    public void testMultiMonitor() {
//        Assertions.assertThrows(BeanCreationException.class, () -> {
//            ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(ConfigTest.class.getPackage().getName().replace('.', '/') + "/multi-monitor.xml");
//            ctx.start();
//        });
//    }
//
//    @Test
//    public void testMultiProviderConfig() {
//        Assertions.assertThrows(BeanCreationException.class, () -> {
//            ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(ConfigTest.class.getPackage().getName().replace('.', '/') + "/provider-multi.xml");
//            ctx.start();
//        });
//    }

    @Test
    public void testModuleInfo() {
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(ConfigTest.class.getPackage().getName().replace('.', '/') +
                "/provider-with-module.xml");
        ctx.start();

        ModuleConfig moduleConfig = ctx.getBean(ModuleConfig.class);
        // Object bean = ctx.getBean("test-module"); 取出来的bean一定是ModuleConfig，因为dubbo:xx标签里面name会作为beanId（当然如果配置id的话，优先取id）
        assertThat(moduleConfig.getName(), is("test-module"));// 这两个属性值在xml配置了
        assertThat(moduleConfig.getVersion(),is("1.1"));
        ctx.close();
    }

    // 测试错误的bean
    @Test
    public void testNotificationWithWrongBean() {
        // 创建bean异常
        Assertions.assertThrows(BeanCreationException.class, () -> {
            ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(ConfigTest.class.getPackage().getName().replace('.', '/')
                    + "/consumer-notification.xml");
            ctx.start();
        });
    }

    @Test
    public void testProperty() {
        // xml里面有自定义的property标签属性,内部的class一般都是ref=xx，如果用class也是注意在DubboBeanDefinitionParser是怎么处理的
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(ConfigTest.class.getPackage().getName().replace('.', '/') +
                "/service-class.xml");
        ctx.start();

        ServiceBean serviceBean = ctx.getBean(ServiceBean.class);

        // 这里getRef、getPrefix
        String prefix = ((DemoServiceImpl) serviceBean.getRef()).getPrefix();
        assertThat(prefix, is("welcome:"));
        ctx.close();
    }


}
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

import org.apache.dubbo.config.annotation.Method;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.api.DemoService;
import org.apache.dubbo.config.spring.api.HelloService;
import org.apache.dubbo.config.utils.ReferenceConfigCache;
import org.apache.dubbo.rpc.model.ApplicationModel;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collection;
import java.util.Map;

import static org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor.BEAN_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReferenceAnnotationBeanPostProcessor} Test
 *
 * @since 2.5.7
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = {
                ServiceAnnotationTestConfiguration.class, // 1.注意这个是第一步，会进 ServiceBeanPostProcessor 的 构造方法逻辑 以及postProcessBeanDefinitionRegistry逻辑
                ReferenceAnnotationBeanPostProcessorTest.class,
                ReferenceAnnotationBeanPostProcessorTest.TestAspect.class
        })
@TestPropertySource(properties = {
        "packagesToScan = org.apache.dubbo.config.spring.context.annotation.provider",
        "consumer.version = ${demo.service.version}",
        "consumer.url = dubbo://127.0.0.1:12345?version=2.5.7",
})
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
// 一些关键调用触发顺序，我用数字标记了，自己注意下 1.~n.
// 以及注意#1~#n，这几个标记了@Reference注解的触发实际，因为其中注解里面的属性值都一致，断点进doGetInjectedBean不是很能区分到底是哪个位置注解触发的，可以在加一个属性值比如owner="x"。x指定为1 2 3 等
public class ReferenceAnnotationBeanPostProcessorTest {

    @BeforeAll
    public static void setUp() {
        ApplicationModel.reset();
    }

    @AfterAll
    public static void tearDown() {
        ApplicationModel.reset();
    }

    private static final String AOP_SUFFIX = "(based on AOP)";

    // 学一下注解方式的切面配置
    @Aspect
    @Component
    public static class TestAspect {

        @Around("execution(* org.apache.dubbo.config.spring.context.annotation.provider.DemoServiceImpl.*(..))")
        public Object aroundApi(ProceedingJoinPoint pjp) throws Throwable {
            // 执行目标方法，虽然是环绕通知，但是并没有做环绕处理，直接调用目标方法了
            return pjp.proceed() + AOP_SUFFIX;
        }
    }

    @Bean
    public TestBean testBean() {
        return new TestBean();
    }

    // 2.进ReferenceAnnotationBeanPostProcessor的构造方法。注意这个构造完成之后不会立即调用这个类里的
    // doGetInjectedBean方法，而是遇到构造函数指定的那几个注解才会触发该方法。可以见3. ，上面在注入TestBean bean的时候，其父类的父类AncestorBean
    // 我特地加了一个static，说明先进的这个，即验证了前面说的"ReferenceAnnotationBeanPostProcessor构造完成之后不会立即调用这个类里的doGetInjectedBean方法"
    // （区别于ServiceBeanPostProcessor，其 是构造完成之后立马调用的 postProcessBeanDefinitionRegistry ）,再去看 3.
    @Bean(BEAN_NAME)
    public ReferenceAnnotationBeanPostProcessor referenceAnnotationBeanPostProcessor() {
        return new ReferenceAnnotationBeanPostProcessor();
    }

    @Autowired
    private ConfigurableApplicationContext context;

    // 注意这里Qualifier的用法，provider包下有两个HelloService的实现类，而@@Autowired 是根据类型注入的，所以spring不知道你要哪个实现类实例bean
    // 所以这里使用@Qualifier注解来指定beanName，这样类型+名称就唯一确定了要注入的bean（或者可以在实现类上加一个@Primary注解，该注解可以看@Primary-在spring中的使用.md）
    @Autowired
    @Qualifier("defaultHelloService")
    private HelloService defaultHelloService;

    @Autowired
    @Qualifier("helloServiceImpl")
    private HelloService helloServiceImpl;

    // #4 ReferenceBean (Field Injection #2)
    @Reference(id = "helloService", methods = @Method(name = "sayHello", timeout = 100))
    private HelloService helloService;

    // #5 ReferenceBean (Field Injection #3)
    @Reference
    private HelloService helloService2;

    @Test
    public void test() throws Exception {

        /*
        context的beanName 如下：
        0 = "org.springframework.context.annotation.internalConfigurationAnnotationProcessor"
        1 = "org.springframework.context.annotation.internalAutowiredAnnotationProcessor"
        2 = "org.springframework.context.annotation.internalCommonAnnotationProcessor"
        3 = "org.springframework.context.event.internalEventListenerProcessor"
        4 = "org.springframework.context.event.internalEventListenerFactory"
        5 = "serviceAnnotationTestConfiguration"
        6 = "referenceAnnotationBeanPostProcessorTest"
        7 = "referenceAnnotationBeanPostProcessorTest.TestAspect"
        8 = "dubbo-demo-application"
        9 = "my-registry"
        10 = "dubbo"
        11 = "platformTransactionManager"
        12 = "serviceAnnotationBeanPostProcessor"
        13 = "testBean"
        14 = "referenceAnnotationBeanPostProcessor"
        15 = "org.springframework.aop.config.internalAutoProxyCreator"
        16 = "dubboBootstrapApplicationListener"
        17 = "defaultHelloService"
        18 = "demoServiceImpl"
        19 = "helloServiceImpl"
        20 = "ServiceBean:org.apache.dubbo.config.spring.api.HelloService"
        21 = "ServiceBean:org.apache.dubbo.config.spring.api.DemoService:2.5.7"
                 */
        // 看到上面并没有这个helloService，为啥能通过呢？肯定是别名的原因！！在 registerReferenceBean 方法中，有一个 registerAlias 逻辑，
        // 针对 #5 ，他给provider下的beanName为 demoServiceImpl 的起了别名为 helloService

        assertTrue(context.containsBean("helloService"));

        TestBean testBean = context.getBean(TestBean.class);

        DemoService demoService = testBean.getDemoService();
        Map<String, DemoService> demoServicesMap = context.getBeansOfType(DemoService.class);

        Assertions.assertNotNull(testBean.getDemoServiceFromAncestor());
        Assertions.assertNotNull(testBean.getDemoServiceFromParent());
        Assertions.assertNotNull(testBean.getDemoService());
        Assertions.assertNotNull(testBean.autowiredDemoService);
        Assertions.assertEquals(1, demoServicesMap.size());

        String expectedResult = "Hello,Mercy" + AOP_SUFFIX;

        Assertions.assertEquals(expectedResult, testBean.autowiredDemoService.sayName("Mercy"));
        Assertions.assertEquals(expectedResult, demoService.sayName("Mercy"));
        Assertions.assertEquals("Greeting, Mercy", defaultHelloService.sayHello("Mercy"));
        Assertions.assertEquals("Hello, Mercy", helloServiceImpl.sayHello("Mercy"));
        Assertions.assertEquals("Greeting, Mercy", helloService.sayHello("Mercy"));


        Assertions.assertEquals(expectedResult, testBean.getDemoServiceFromAncestor().sayName("Mercy"));
        Assertions.assertEquals(expectedResult, testBean.getDemoServiceFromParent().sayName("Mercy"));
        Assertions.assertEquals(expectedResult, testBean.getDemoService().sayName("Mercy"));

        DemoService myDemoService = context.getBean("my-reference-bean", DemoService.class);

        Assertions.assertEquals(expectedResult, myDemoService.sayName("Mercy"));


        for (DemoService demoService1 : demoServicesMap.values()) {

            Assertions.assertEquals(myDemoService, demoService1);

            Assertions.assertEquals(expectedResult, demoService1.sayName("Mercy"));
        }

    }

    /**
     * Test on {@link ReferenceAnnotationBeanPostProcessor#getReferenceBeans()}
     */
    @Test
    public void testGetReferenceBeans() {

        ReferenceAnnotationBeanPostProcessor beanPostProcessor = context.getBean(BEAN_NAME,
                ReferenceAnnotationBeanPostProcessor.class);

        Collection<ReferenceBean<?>> referenceBeans = beanPostProcessor.getReferenceBeans();

        Assertions.assertEquals(4, referenceBeans.size());

        ReferenceBean<?> referenceBean = referenceBeans.iterator().next();

        Assertions.assertNotNull(ReferenceConfigCache.getCache().get(referenceBean));

    }

    @Test
    public void testGetInjectedFieldReferenceBeanMap() {

        ReferenceAnnotationBeanPostProcessor beanPostProcessor = context.getBean(BEAN_NAME,
                ReferenceAnnotationBeanPostProcessor.class);

        Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> referenceBeanMap =
                beanPostProcessor.getInjectedFieldReferenceBeanMap();

        Assertions.assertEquals(3, referenceBeanMap.size());

        for (Map.Entry<InjectionMetadata.InjectedElement, ReferenceBean<?>> entry : referenceBeanMap.entrySet()) {

            InjectionMetadata.InjectedElement injectedElement = entry.getKey();

            Assertions.assertEquals("com.alibaba.spring.beans.factory.annotation.AbstractAnnotationBeanPostProcessor$AnnotatedFieldElement",
                    injectedElement.getClass().getName());

        }

    }

    @Test
    public void testGetInjectedMethodReferenceBeanMap() {

        ReferenceAnnotationBeanPostProcessor beanPostProcessor = context.getBean(BEAN_NAME,
                ReferenceAnnotationBeanPostProcessor.class);

        Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> referenceBeanMap =
                beanPostProcessor.getInjectedMethodReferenceBeanMap();

        Assertions.assertEquals(2, referenceBeanMap.size());

        for (Map.Entry<InjectionMetadata.InjectedElement, ReferenceBean<?>> entry : referenceBeanMap.entrySet()) {

            InjectionMetadata.InjectedElement injectedElement = entry.getKey();

            Assertions.assertEquals("com.alibaba.spring.beans.factory.annotation.AbstractAnnotationBeanPostProcessor$AnnotatedMethodElement",
                    injectedElement.getClass().getName());

        }

    }

    //    @Test
    //    public void testModuleInfo() {
    //
    //        ReferenceAnnotationBeanPostProcessor beanPostProcessor = context.getBean(BEAN_NAME,
    //                ReferenceAnnotationBeanPostProcessor.class);
    //
    //
    //        Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> referenceBeanMap =
    //                beanPostProcessor.getInjectedMethodReferenceBeanMap();
    //
    //        for (Map.Entry<InjectionMetadata.InjectedElement, ReferenceBean<?>> entry : referenceBeanMap.entrySet()) {
    //            ReferenceBean<?> referenceBean = entry.getValue();
    //
    //            assertThat(referenceBean.getModule().getName(), is("defaultModule"));
    //            assertThat(referenceBean.getMonitor(), not(nullValue()));
    //        }
    //    }

    private static class AncestorBean {// 祖先

        static {
            int i=0;
        }
        private DemoService demoServiceFromAncestor;

        @Autowired
        private ApplicationContext applicationContext;

        public DemoService getDemoServiceFromAncestor() {
            return demoServiceFromAncestor;
        }

        // #3 ReferenceBean (Method Injection #1)
        @Reference(id = "my-reference-bean", version = "2.5.7", url = "dubbo://127.0.0.1:12345?version=2.5.7")
        public void setDemoServiceFromAncestor(DemoService demoServiceFromAncestor) {
            this.demoServiceFromAncestor = demoServiceFromAncestor;
        }

        public ApplicationContext getApplicationContext() {
            return applicationContext;
        }

    }

    private static class ParentBean extends AncestorBean {

        // #1 ReferenceBean (Field Injection #1) // 3.这个会触发ReferenceAnnotationBeanPostProcessor的doGetInjectedBean
        @Reference(version = "${consumer.version}", url = "${consumer.url}")
        private DemoService demoServiceFromParent;

        public DemoService getDemoServiceFromParent() {
            return demoServiceFromParent;
        }

    }

    static class TestBean extends ParentBean {

        private DemoService demoService;

        @Autowired
        private DemoService autowiredDemoService;

        @Autowired
        private ApplicationContext applicationContext;

        public DemoService getDemoService() {
            return demoService;
        }

        // #2 ReferenceBean (Method Injection #2)
        @com.alibaba.dubbo.config.annotation.Reference(version = "2.5.7", url = "dubbo://127.0.0.1:12345?version=2.5.7")
        public void setDemoService(DemoService demoService) {
            this.demoService = demoService;
        }
    }

    @Test
    public void testReferenceBeansMethodAnnotation() {

        ReferenceAnnotationBeanPostProcessor beanPostProcessor = context.getBean(BEAN_NAME,
                ReferenceAnnotationBeanPostProcessor.class);

        Collection<ReferenceBean<?>> referenceBeans = beanPostProcessor.getReferenceBeans();

        Assertions.assertEquals(4, referenceBeans.size());

        ReferenceBean<?> referenceBean = referenceBeans.iterator().next();

        if ("helloService".equals(referenceBean.getId())) {
            Assertions.assertNotNull(referenceBean.getMethods());
        }
    }

}
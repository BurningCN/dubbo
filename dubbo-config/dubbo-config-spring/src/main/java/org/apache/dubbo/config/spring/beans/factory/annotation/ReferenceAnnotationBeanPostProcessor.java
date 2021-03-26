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

import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.ServiceBean;

import com.alibaba.spring.beans.factory.annotation.AbstractAnnotationBeanPostProcessor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.spring.util.AnnotationUtils.getAttribute;
import static com.alibaba.spring.util.AnnotationUtils.getAttributes;
import static org.apache.dubbo.config.spring.beans.factory.annotation.ServiceBeanNameBuilder.create;
import static org.springframework.util.StringUtils.hasText;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that Consumer service {@link Reference} annotated fields
 *
 * @see DubboReference
 * @see Reference
 * @see com.alibaba.dubbo.config.annotation.Reference
 * @since 2.5.7
 */
// 区别于 ServiceConfigPostProcessor 类 继承的是 BeanDefinitionRegistryPostProcessor
public class ReferenceAnnotationBeanPostProcessor extends AbstractAnnotationBeanPostProcessor implements
        ApplicationContextAware {

    /**
     * The bean name of {@link ReferenceAnnotationBeanPostProcessor}
     */
    public static final String BEAN_NAME = "referenceAnnotationBeanPostProcessor";

    /**
     * Cache size
     */
    private static final int CACHE_SIZE = Integer.getInteger(BEAN_NAME + ".cache.size", 32);

    private final ConcurrentMap<String, ReferenceBean<?>> referenceBeanCache =
            new ConcurrentHashMap<>(CACHE_SIZE);

    private final ConcurrentMap<InjectionMetadata.InjectedElement, ReferenceBean<?>> injectedFieldReferenceBeanCache =
            new ConcurrentHashMap<>(CACHE_SIZE);

    private final ConcurrentMap<InjectionMetadata.InjectedElement, ReferenceBean<?>> injectedMethodReferenceBeanCache =
            new ConcurrentHashMap<>(CACHE_SIZE);

    private ApplicationContext applicationContext;

    /**
     * {@link com.alibaba.dubbo.config.annotation.Reference @com.alibaba.dubbo.config.annotation.Reference} has been supported since 2.7.3
     * <p>
     * {@link DubboReference @DubboReference} has been supported since 2.7.7
     */
    // 这个是直接通过构造函数直接传入注解，而 ServiceConfigPostProcessor 是 postXX方法给 scan#includeFilters 集合属性 填充的注解
    public ReferenceAnnotationBeanPostProcessor() {
        super(DubboReference.class, Reference.class, com.alibaba.dubbo.config.annotation.Reference.class);
    }

    /**
     * Gets all beans of {@link ReferenceBean}
     *
     * @return non-null read-only {@link Collection}
     * @since 2.5.9
     */
    public Collection<ReferenceBean<?>> getReferenceBeans() {
        return referenceBeanCache.values();
    }

    /**
     * Get {@link ReferenceBean} {@link Map} in injected field.
     *
     * @return non-null {@link Map}
     * @since 2.5.11
     */
    public Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> getInjectedFieldReferenceBeanMap() {
        return Collections.unmodifiableMap(injectedFieldReferenceBeanCache);
    }

    /**
     * Get {@link ReferenceBean} {@link Map} in injected method.
     *
     * @return non-null {@link Map}
     * @since 2.5.11
     */
    public Map<InjectionMetadata.InjectedElement, ReferenceBean<?>> getInjectedMethodReferenceBeanMap() {
        return Collections.unmodifiableMap(injectedMethodReferenceBeanCache);
    }

    // 结合该类测试程序，如下方法第一次触发点在 #1 ReferenceBean (Field Injection #1)  比较处
    @Override
    protected Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                       InjectionMetadata.InjectedElement injectedElement) throws Exception {
        /**
         * The name of bean that annotated Dubbo's {@link Service @Service} in local Spring {@link ApplicationContext}
         */
        // eg referencedBeanName = ServiceBean:org.apache.dubbo.config.spring.api.DemoService:2.5.7
        // 这个referencedBeanName怎么理解的，可以理解为ServiceBean，就是 AncestorBean # setDemoServiceFromAncestor(DemoService x)
        // 的参数这个bean。这里的injectedType 就是 DemoService 类型。spring的ioc 依赖注入有一种方式就是setXx注入，而正好这个是setXx方法，
        // 所以会触发doGetInjectedBean方法，而且从这个方法名也能看出端倪，而且方法参数 injectedType 也能知道这个就是"被"注入的依赖对象（参数x）的类型。

        // 这里按照ServiceBeanNameBuilder格式构造referencedBeanName名称的作用是这样的，我们在这个大类对应的测试方法里面的开头加了
        // @ContextConfiguration(ServiceAnnotationTestConfiguration.class..)，这个配置类里面正好配置了
        // ServiceAnnotationBeanPostProcessor 这个bean，而这个bean又会转到ServiceClassPostProcessor ，其相关方法(postProcessXX)就会
        // 扫描到xx/provider包下的符合条件的类并注册BeanDefinition，并根据前面的DF构造ServiceBean对应的BeanDefinition注册到容器，而其中就包含了
        // 下面这个injectedType类型的ServiceBean！！ （后面 isLocalServiceBean 方法检查的时候会用到）

        // 除了setXx对该注解进行注入以来的bean，还有直接属性的方式，详见对应测试类的 private DemoService demoServiceFromParent;

        String referencedBeanName = buildReferencedBeanName(attributes, injectedType);


        /**
         * The name of bean that is declared by {@link Reference @Reference} annotation injection
         */
        // eg referenceBeanName = @Reference(url=dubbo://127.0.0.1:12345?version=2.5.7,version=2.5.7) org.apache.dubbo.config.spring.api.DemoService
        // 注意区分，上面带 ed 结尾，这里不带。
        String referenceBeanName = getReferenceBeanName(attributes, injectedType);

        if(referenceBeanName.equals("helloService")){
            int i = 1;
        }
        // 创建ReferenceBean实例
        ReferenceBean referenceBean = buildReferenceBeanIfAbsent(referenceBeanName, attributes, injectedType);

        // 检查ServiceBean是否存在，以及referenceBean是否是本地injvm的
        boolean localServiceBean = isLocalServiceBean(referencedBeanName, referenceBean, attributes);

        // 如果是本地服务（localServiceBean=true），如果还没暴露服务的话则进行export
        prepareReferenceBean(referencedBeanName, referenceBean, localServiceBean);

        // 将ReferenceBean注册到容器
        registerReferenceBean(referencedBeanName, referenceBean, attributes, localServiceBean, injectedType);

        // 缓存
        cacheInjectedReferenceBean(referenceBean, injectedElement);

        // 走refer
        return referenceBean.get();
    }

    // 结合测试程序5次@Reference注解，从而5次触发上面的方法（下面sb为ServiceBean，rb为ReferenceBean）
    // #1 生成sbName、rbName，referenceBeanCache取不到rbName，构建rb并存入，注册rb到容器，以别名的方式对sb.export + rb.get()(内部触发init，走refer）
    // #2 生成sbName、rbName和#1一样，referenceBeanCache能取到#1缓存的，发现sb已经export 了，直接返回，rb.get()内部发现已经init，不会refer
    // #3 生成sbName、rbName，rbName为my-reference-bean，和前两个不同，referenceBeanCache取不到rbName，构建rb并存入，但是sb已经被#1暴露，不会重复暴露，rb.get()内部走init，走refer，注意此时rb对象和前两个不是一个
    // #4 和 # 5 简单说下，他们的sb都是一个实例，只会export一次，但是rb是不同的，会各自refer一次，共两次
    // 且注意这个 registerAlias 起别名的逻辑！！

    /**
     * Register an instance of {@link ReferenceBean} as a Spring Bean
     *
     * @param referencedBeanName The name of bean that annotated Dubbo's {@link Service @Service} in the Spring {@link ApplicationContext}
     * @param referenceBean      the instance of {@link ReferenceBean} is about to register into the Spring {@link ApplicationContext}
     * @param attributes         the {@link AnnotationAttributes attributes} of {@link Reference @Reference}
     * @param localServiceBean   Is Local Service bean or not
     * @param interfaceClass     the {@link Class class} of Service interface
     * @since 2.7.3
     */
    private void registerReferenceBean(String referencedBeanName, ReferenceBean referenceBean,
                                       AnnotationAttributes attributes,
                                       boolean localServiceBean, Class<?> interfaceClass) {

        ConfigurableListableBeanFactory beanFactory = getBeanFactory();

        //
        String beanName = getReferenceBeanName(attributes, interfaceClass);

        if (localServiceBean) {  // If @Service bean is local one

            /**
             * Get  the @Service's BeanDefinition from {@link BeanFactory}
             * Refer to {@link ServiceAnnotationBeanPostProcessor#buildServiceBeanDefinition}
             */
            AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) beanFactory.getBeanDefinition(referencedBeanName);
            // 找到ref 实现类，该ref属性的填充逻辑在 ----> ServiceClassPostProcessor#buildServiceBeanDefinition# addPropertyReference(builder, "ref", annotatedServiceBeanName);
            RuntimeBeanReference runtimeBeanReference = (RuntimeBeanReference) beanDefinition.getPropertyValues().get("ref");

            // The name of bean annotated @Service // eg demoServiceImpl
            String serviceBeanName = runtimeBeanReference.getBeanName();
            // register Alias rather than a new bean name, in order to reduce duplicated beans
            // 注册别名，给 serviceBeanName起一个别名为beanName({ "demoServiceImpl" : "@Reference(url=dubbo://127.0.0.1:12345?version=2.5.7,version=2.5.7)"})，
            // 这样以后getBean()传入这两个都能拿到ref这个实现类对象bean，目的就是上面英文注释，减少重复的beans，这样通过@Reference就能拿到ServiceBean的ref实现类了
            beanFactory.registerAlias(serviceBeanName, beanName);
        } else { // Remote @Service Bean 在这个是远端，上面是本地
            if (!beanFactory.containsBean(beanName)) {
                beanFactory.registerSingleton(beanName, referenceBean);
            }
        }
    }

    /**
     * Get the bean name of {@link ReferenceBean} if {@link Reference#id() id attribute} is present,
     * or {@link #generateReferenceBeanName(AnnotationAttributes, Class) generate}.
     *
     * @param attributes     the {@link AnnotationAttributes attributes} of {@link Reference @Reference}
     * @param interfaceClass the {@link Class class} of Service interface
     * @return non-null
     * @since 2.7.3
     */
    private String getReferenceBeanName(AnnotationAttributes attributes, Class<?> interfaceClass) {
        // id attribute appears since 2.7.3  （对应的测试程序其中#3就是含有含id属性的）
        String beanName = getAttribute(attributes, "id");
        if (!hasText(beanName)) {// todo need pr 这里计算name的名称可以搞一个缓存，该方法被多次调用了
            beanName = generateReferenceBeanName(attributes, interfaceClass);
        }
        return beanName;
    }

    /**
     * Build the bean name of {@link ReferenceBean}
     *
     * @param attributes     the {@link AnnotationAttributes attributes} of {@link Reference @Reference}
     * @param interfaceClass the {@link Class class} of Service interface
     * @return
     * @since 2.7.3
     */
    private String generateReferenceBeanName(AnnotationAttributes attributes, Class<?> interfaceClass) {
        StringBuilder beanNameBuilder = new StringBuilder("@Reference"); // eg @Reference(url=dubbo://127.0.0.1:12345?version=2.5.7,version=2.5.7) org.apache.dubbo.config.spring.api.DemoService

        if (!attributes.isEmpty()) {
            beanNameBuilder.append('(');
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                beanNameBuilder.append(entry.getKey())
                        .append('=')
                        .append(entry.getValue())
                        .append(',');
            }
            // replace the latest "," to be ")"
            beanNameBuilder.setCharAt(beanNameBuilder.lastIndexOf(","), ')');
        }

        beanNameBuilder.append(" ").append(interfaceClass.getName());

        return beanNameBuilder.toString();
    }

    /**
     * Is Local Service bean or not?
     *
     * @param referencedBeanName the bean name to the referenced bean
     * @return If the target referenced bean is existed, return <code>true</code>, or <code>false</code>
     * @since 2.7.6
     */
    private boolean isLocalServiceBean(String referencedBeanName, ReferenceBean referenceBean, AnnotationAttributes attributes) {
        return existsServiceBean(referencedBeanName) && !isRemoteReferenceBean(referenceBean, attributes);
    }

    /**
     * Check the {@link ServiceBean} is exited or not
     *
     * @param referencedBeanName the bean name to the referenced bean
     * @return if exists, return <code>true</code>, or <code>false</code>
     * @revised 2.7.6
     */
    private boolean existsServiceBean(String referencedBeanName) {
        // 检查容器是否已经存在了该ServiceBean，结合该大类的测试类，其 setDemoServiceFromAncestor(DemoService x)，的这个x已经被注入过
        // 这个注入的bean名称为ServiceBean:org.apache.dubbo.config.spring.api.DemoService:2.5.7
        // 至于为何被注入了，原因请看doGetInjectedBean方法内的第一段注释没落，所以下面检查肯定通过
        return applicationContext.containsBean(referencedBeanName) &&
                applicationContext.isTypeMatch(referencedBeanName, ServiceBean.class);

    }

    private boolean isRemoteReferenceBean(ReferenceBean referenceBean, AnnotationAttributes attributes) {
        boolean remote = Boolean.FALSE.equals(referenceBean.isInjvm()) || Boolean.FALSE.equals(attributes.get("injvm"));
        return remote;
    }

    /**
     * Prepare {@link ReferenceBean}
     *
     * @param referencedBeanName The name of bean that annotated Dubbo's {@link DubboService @DubboService}
     *                           in the Spring {@link ApplicationContext}
     * @param referenceBean      the instance of {@link ReferenceBean}
     * @param localServiceBean   Is Local Service bean or not
     * @since 2.7.8
     */
    private void prepareReferenceBean(String referencedBeanName, ReferenceBean referenceBean, boolean localServiceBean) {
        //  Issue : https://github.com/apache/dubbo/issues/6224
        if (localServiceBean) { // If the local @Service Bean exists
            referenceBean.setInjvm(Boolean.TRUE);
            exportServiceBeanIfNecessary(referencedBeanName); // If the referenced ServiceBean exits, export it immediately，内部走export的逻辑，建立服务器+注册zk
        }
    }


    private static AtomicInteger a  = new AtomicInteger();

    private void exportServiceBeanIfNecessary(String referencedBeanName) {
        if (existsServiceBean(referencedBeanName)) {
            ServiceBean serviceBean = getServiceBean(referencedBeanName);
            // #2和#1产生的referencedBeanName也是一致的，#2在触发方法exportServiceBeanIfNecessary的时候发现因为先前#1已经暴露过了，所以不会重复暴露
            if (!serviceBean.isExported()) {
                System.out.println("======="+ (a.incrementAndGet()));
                serviceBean.export();
            }
        }
    }

    private ServiceBean getServiceBean(String referencedBeanName) {
        return applicationContext.getBean(referencedBeanName, ServiceBean.class);
    }

    @Override
    protected String buildInjectedObjectCacheKey(AnnotationAttributes attributes, Object bean, String beanName,
                                                 Class<?> injectedType, InjectionMetadata.InjectedElement injectedElement) {
        return buildReferencedBeanName(attributes, injectedType) +
                "#source=" + (injectedElement.getMember()) +
                "#attributes=" + getAttributes(attributes, getEnvironment());
    }

    /**
     * @param attributes           the attributes of {@link Reference @Reference}
     * @param serviceInterfaceType the type of Dubbo's service interface
     * @return The name of bean that annotated Dubbo's {@link Service @Service} in local Spring {@link ApplicationContext}
     */
    private String buildReferencedBeanName(AnnotationAttributes attributes, Class<?> serviceInterfaceType) {
        ServiceBeanNameBuilder serviceBeanNameBuilder = create(attributes, serviceInterfaceType, getEnvironment());
        return serviceBeanNameBuilder.build();
    }

    private ReferenceBean buildReferenceBeanIfAbsent(String referenceBeanName, AnnotationAttributes attributes,
                                                     Class<?> referencedType)
            throws Exception {

        // 先从缓存取，比如对应测试程序的#1和#2处，这俩根据@Reference注解生成的referenceBean是一样的，#2触发的时候就会拿到先前#1触发缓存的实例
        // 同理#2和#1产生的referencedBeanName也是一致的，#2在触发方法exportServiceBeanIfNecessary的时候发现因为先前#1已经暴露过了，所以不会重复暴露
        ReferenceBean<?> referenceBean = referenceBeanCache.get(referenceBeanName);

        if (referenceBean == null) {
            ReferenceBeanBuilder beanBuilder = ReferenceBeanBuilder
                    .create(attributes, applicationContext)
                    .interfaceClass(referencedType);
            // 内部会创建ReferenceBean对象，并在 ReferenceBeanBuilder#postConfigBean 中会调用Reference#afterPropertiesSet
            referenceBean = beanBuilder.build();
            referenceBeanCache.put(referenceBeanName, referenceBean);
        } else if (!referencedType.isAssignableFrom(referenceBean.getInterfaceClass())) {
            throw new IllegalArgumentException("reference bean name " + referenceBeanName + " has been duplicated, but interfaceClass " +
                    referenceBean.getInterfaceClass().getName() + " cannot be assigned to " + referencedType.getName());
        }
        return referenceBean;
    }

    private void cacheInjectedReferenceBean(ReferenceBean referenceBean,
                                            InjectionMetadata.InjectedElement injectedElement) {
        // 是通过方法参数注入的，还是通过字段属性注入的，存到对应的缓存容器
        if (injectedElement.getMember() instanceof Field) {
            injectedFieldReferenceBeanCache.put(injectedElement, referenceBean);
        } else if (injectedElement.getMember() instanceof Method) {
            injectedMethodReferenceBeanCache.put(injectedElement, referenceBean);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void destroy() throws Exception {
        super.destroy();
        this.referenceBeanCache.clear();
        this.injectedFieldReferenceBeanCache.clear();
        this.injectedMethodReferenceBeanCache.clear();
    }
}

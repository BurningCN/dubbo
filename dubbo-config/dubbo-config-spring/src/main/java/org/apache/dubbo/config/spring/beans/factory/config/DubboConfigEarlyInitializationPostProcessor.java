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
package org.apache.dubbo.config.spring.beans.factory.config;

import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.context.ConfigManager;

import com.alibaba.spring.beans.factory.config.GenericBeanPostProcessorAdapter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;
import org.springframework.core.PriorityOrdered;

import javax.annotation.PostConstruct;

/**
 * Generally, {@link AbstractConfig Dubbo Config} Bean will be added into {@link ConfigManager} on the bean initialization
 * life cycle through {@link CommonAnnotationBeanPostProcessor} executing the callback of
 * {@link PostConstruct @PostConstruct}. However, the instantiation and initialization of
 * {@link AbstractConfig Dubbo Config} Bean could be too early before {@link CommonAnnotationBeanPostProcessor}, e.g,
 * execution, thus it's required to register the current instance as a {@link BeanPostProcessor} into
 * {@link DefaultListableBeanFactory the BeanFatory} using {@link BeanDefinitionRegistryPostProcessor} as early as
 * possible.
 * 例如执行，因此需要尽早使用{@link BeanDefinitionRegistryPostProcessor}将当前实例作为{@link BeanPostProcessor}注册到{@link DefaultListableBeanFactory BeanFatory}中。
 * @see GenericBeanPostProcessorAdapter
 * @since 2.7.9
 */
// 注意继承的 GenericBeanPostProcessorAdapter，这个是实现 BeanPostProcessor 接口的，主要是限定了泛型，在后面的 processBeforeInitialization 可以直接拿到的就是AbstractConfig类型的
// BeanDefinitionRegistryPostProcessor 是 BeanFactoryPostProcessor
// GenericBeanPostProcessorAdapter     是 BeanPostProcessor
public class DubboConfigEarlyInitializationPostProcessor extends GenericBeanPostProcessorAdapter<AbstractConfig>
        implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private static final Log logger = LogFactory.getLog(DubboConfigEarlyInitializationPostProcessor.class.getName());

    public static final String BEAN_NAME = "dubboConfigEarlyInitializationPostProcessor";

    private DefaultListableBeanFactory beanFactory;

    // 下两个方法是 BeanDefinitionRegistryPostProcessor 的，前者是直接的，后者是爷爷BeanFactoryPostProcessor的方法
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // 拿到beanFactory的意义在于两个：1.将本类这种BeanPostProcessor注入到容器(看类头注释)。2.后面会获取所有的BeanPostProcessor
        this.beanFactory = unwrap(registry);
        // 进去
        initBeanFactory();
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (beanFactory == null) { // try again if postProcessBeanDefinitionRegistry method does not effect.
            this.beanFactory = unwrap(beanFactory);
            initBeanFactory();
        }
    }

    protected void processBeforeInitialization(AbstractConfig config, String beanName) throws BeansException {

        if (this.beanFactory == null) {
            if (logger.isErrorEnabled()) {
                logger.error("Current Processor is not running in Spring container, next action will be skipped!");
            }
            return;
        }

        // If CommonAnnotationBeanPostProcessor is already registered,  the method addIntoConfigManager()
        // will be invoked in Bean life cycle. ---- 这点可以看类头部注释，因为可能bean已经创建完成了，但是 CommonAnnotationBeanPostProcessor 还没有注册到容器

        // CommonAnnotationBeanPostProcessor是处理@PreDestroy、@PostConstruct、@Resource注解的，而AbstractConfig#addIntoConfigManager
        // 方法是标记@PostConstruct注解的，所以如果已经注册了CommonAnnotationBeanPostProcessor，addIntoConfigManager()方法将在Bean
        // 生命周期中被自动调用。如果没有注册，我们手动调用！！ --- > 这就是该BeanPostProcessor的作用，在processBeforeInitialization做拦截处理
        if (!hasRegisteredCommonAnnotationBeanPostProcessor()) {
            if (logger.isWarnEnabled()) {
                logger.warn("CommonAnnotationBeanPostProcessor is not registered yet, " +
                        "the method addIntoConfigManager() will be invoked directly");
            }
            // 手动填充到ConfigManager
            config.addIntoConfigManager();
        }
    }

    private DefaultListableBeanFactory unwrap(Object registry) {
        // BeanFactory的默认实现
        if (registry instanceof DefaultListableBeanFactory) {
            return (DefaultListableBeanFactory) registry;
        }
        return null;
    }

    private void initBeanFactory() {
        if (beanFactory != null) {
            // Register itself
            if (logger.isInfoEnabled()) {
                logger.info("BeanFactory is about to be initialized, trying to resolve the Dubbo Config Beans early " +
                        "initialization");
            }
            // 给BeanFactory添加PostProcessor的方式，直接调api
            beanFactory.addBeanPostProcessor(this);
        }
    }

    /**
     * {@link DefaultListableBeanFactory} has registered {@link CommonAnnotationBeanPostProcessor} or not?
     *
     * @return if registered, return <code>true</code>, or <code>false</code>
     */
    private boolean hasRegisteredCommonAnnotationBeanPostProcessor() {
        for (BeanPostProcessor beanPostProcessor : beanFactory.getBeanPostProcessors()) {
            if (CommonAnnotationBeanPostProcessor.class.equals(beanPostProcessor.getClass())) {
                return true;
            }
        }
        return false;
        /* beanFactory.getBeanPostProcessors()的数据如下（demo-annotation模块provider对应的Application）
        result = {CopyOnWriteArrayList@1675}  size = 11
                 0 = {ApplicationContextAwareProcessor@5657}
                 1 = {ConfigurationClassPostProcessor$ImportAwareBeanPostProcessor@5667}
                 2 = {PostProcessorRegistrationDelegate$BeanPostProcessorChecker@5673}
                *3 = {DubboConfigEarlyInitializationPostProcessor@1367}  // 注意
                 4 = {ConfigurationBeanBindingPostProcessor@5674}  // 注意 这个是DubboConfigConfiguration注入的
                *5 = {DubboConfigAliasPostProcessor@1636}   // 注意
                *6 = {DubboConfigDefaultPropertyValueBeanPostProcessor@5675}   // 注意
                 7 = {CommonAnnotationBeanPostProcessor@5676}  // 注意 这个是处理@PreDestroy、@PostConstruct、@Resource注解的
                 8 = {ReferenceAnnotationBeanPostProcessor@5677} // 注意
                 9 = {AutowiredAnnotationBeanPostProcessor@5678}  // 注意  这个是处理@Autowired注解的
                 10 = {ApplicationListenerDetector@5679}

                 // 且注入上面的顺序，内部会挨个按照顺序调用每个BeanPostProcessor的processBeforeInitialization方法（或者postProcessAfterInitialization），
                 然后按顺序调用processAfterInitialization方法（上面list的排序也取决于getOrder的值，即优先级，可以看到上面 DubboConfigEarlyInitializationPostProcessor 相对来说排在第一，前面几个应该是默认的）
        */
    }

    // 拥有最高的优先级，因为 该类和DubboConfigAliasPostProcessor都实现了DubboConfigAliasPostProcessor和BeanPostProcessor，所以这两个bean的优先顺序得定一下
    // 这样这两个类相同的实现方法会有一个优先级别，谁先触发
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}

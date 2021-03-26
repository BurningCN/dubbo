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
package org.apache.dubbo.config.spring;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.MetricsConfig;
import org.apache.dubbo.config.ModuleConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.SslConfig;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.spring.extension.SpringExtensionFactory;
import org.apache.dubbo.config.support.Parameter;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

/**
 * ReferenceFactoryBean
 */
public class ReferenceBean<T> extends ReferenceConfig<T> implements FactoryBean,
        ApplicationContextAware, InitializingBean, DisposableBean {

    private static final long serialVersionUID = 213195494150089726L;

    private transient ApplicationContext applicationContext;

    public ReferenceBean() {
        super();
    }

    public ReferenceBean(Reference reference) {
        super(reference);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        SpringExtensionFactory.addApplicationContext(applicationContext);
    }

    // 见demo模块xml方式里的consumer模块，因为我们在自定义的parser类里面设置的都是延迟加载。测试程序getBean的时候才会创建bean，就会调用这个方法
    // 而DubboBootstrap#start方法在referService方法内部判定shouldInit都是false，即不通过该入口进行refer，而是上面说的getBean入口
    @Override
    public Object getObject() {
        // 进去
        return get();
    }

    @Override
    public Class<?> getObjectType() {
        return getInterfaceClass();
    }

    @Override
    @Parameter(excluded = true)
    public boolean isSingleton() {
        return true;
    }

    /**
     * Initializes there Dubbo's Config Beans before @Reference bean autowiring
     */
    private void prepareDubboConfigBeans() {
        // Refactor 2.7.9
        final boolean includeNonSingletons = true;
        final boolean allowEagerInit = false;

        // beansOfTypeIncludingAncestors 返回给定类型或子类型的所有bean，如果当前bean工厂是层次结构的beanfactory，还可以选择祖先bean工厂中定义的bean。

        beansOfTypeIncludingAncestors(applicationContext, ApplicationConfig.class, includeNonSingletons, allowEagerInit);
        beansOfTypeIncludingAncestors(applicationContext, ModuleConfig.class, includeNonSingletons, allowEagerInit);
        beansOfTypeIncludingAncestors(applicationContext, RegistryConfig.class, includeNonSingletons, allowEagerInit);
        beansOfTypeIncludingAncestors(applicationContext, ProtocolConfig.class, includeNonSingletons, allowEagerInit);
        beansOfTypeIncludingAncestors(applicationContext, MonitorConfig.class, includeNonSingletons, allowEagerInit);
        beansOfTypeIncludingAncestors(applicationContext, ProviderConfig.class, includeNonSingletons, allowEagerInit);
        beansOfTypeIncludingAncestors(applicationContext, ConsumerConfig.class, includeNonSingletons, allowEagerInit);
        beansOfTypeIncludingAncestors(applicationContext, ConfigCenterBean.class, includeNonSingletons, allowEagerInit);
        beansOfTypeIncludingAncestors(applicationContext, MetadataReportConfig.class, includeNonSingletons, allowEagerInit);
        beansOfTypeIncludingAncestors(applicationContext, MetricsConfig.class, includeNonSingletons, allowEagerInit);
        beansOfTypeIncludingAncestors(applicationContext, SslConfig.class, includeNonSingletons, allowEagerInit);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public void afterPropertiesSet() throws Exception {

        // 在@Reference bean自动装配之前初始化Dubbo的配置bean
        // Initializes Dubbo's Config Beans before @Reference bean autowiring
        prepareDubboConfigBeans();

        // lazy init by default. 使用该ReferenceBean的方式，默认是延迟初始化的，Bootstrap的start方法内部的referServices的时候，
        // 判定shouldInit都是false的，所以不会立马引用，而是通过ctx.getBean，内部会走ReferenceBean的FactoryBean#getObject方法，
        // 详见demo-xml的consumer Application测试程序
        if (init == null) {
            init = false;
        }

        // eager init if necessary.
        if (shouldInit()) {
            getObject();
        }
    }

    @Override
    public void destroy() {
        // do nothing
    }
}

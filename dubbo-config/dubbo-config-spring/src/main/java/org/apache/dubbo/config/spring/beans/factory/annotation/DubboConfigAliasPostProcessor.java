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

import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.spring.context.annotation.DubboConfigConfigurationRegistrar;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import static com.alibaba.spring.util.BeanRegistrar.hasAlias;
import static org.springframework.util.ObjectUtils.nullSafeEquals;
import static org.springframework.util.StringUtils.hasText;

/**
 * A Post-Processor class to set the alias of Dubbo Config bean using its {@link AbstractConfig#getId()}
 *
 * @since 2.7.5
 */
// OK
// 这个主要体现 在 BeanPostProcessor#postProcessAfterInitialization 逻辑，DubboConfigAliasPostProcessor在DubboBeanUtils进行注册
// 每个bean注册完成后（context.refresh），就会走postPxx逻辑，这个逻辑的作用就是判定如果注册的bean类型为AbstractConfig的话
// 那么看他生成的beanName和AbstractConfig.id是否一致，不一致的话，则给beanName其一个别名，别名为id！！

// 这种情况一般发生在我们在properties文件中直接配置application、registry、protocol 的方式，比如文件 classpath:/META-INF/dubbo-provider.properties，
// 可以看EnableDubboTest第一条测试程序，断点打在该类最后一个方法（注意结合DubboConfigConfigurationRegistrar、DubboConfigConfiguration）
// 在这种情况下，比如properties文件配置了比如dubbo.protocol.name=dubbo这条，那么 DubboConfigConfiguration会检测到该符合配置的前缀，会生成ProtocolConfig bean，
// 给该bean生成的baneName为 org.apache.dubbo.config.ProtocolConfig#0 ，通过别名映射，将name映射到前者，这样更简洁。
public class DubboConfigAliasPostProcessor implements BeanDefinitionRegistryPostProcessor, BeanPostProcessor {

    /**
     * The bean name of {@link DubboConfigConfigurationRegistrar}
     */
    public final static String BEAN_NAME = "dubboConfigAliasPostProcessor";

    private BeanDefinitionRegistry registry;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        this.registry = registry;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // DO NOTHING
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AbstractConfig) {
            String id = ((AbstractConfig) bean).getId();
            if (hasText(id)                                     // id MUST be present in AbstractConfig
                    && !nullSafeEquals(id, beanName)            // id MUST NOT be equal to bean name
                    && !hasAlias(registry, beanName, id)) {     // id MUST NOT be present in AliasRegistry
                registry.registerAlias(beanName, id);
            }
        }
        return bean;
    }
}

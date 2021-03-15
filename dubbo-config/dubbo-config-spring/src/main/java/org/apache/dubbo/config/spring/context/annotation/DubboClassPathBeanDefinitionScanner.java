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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

import java.util.Set;

import static org.springframework.context.annotation.AnnotationConfigUtils.registerAnnotationConfigProcessors;

/**
 * Dubbo {@link ClassPathBeanDefinitionScanner} that exposes some methods to be public.
 *
 * @see #doScan(String...)
 * @see #registerDefaultFilters()
 * @since 2.5.7
 */

// OK
public class DubboClassPathBeanDefinitionScanner extends ClassPathBeanDefinitionScanner {


    public DubboClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters, Environment environment,
                                               ResourceLoader resourceLoader) {

        /**
         * ClassPathBeanDefinitionScanner内部 发现 useDefaultFilters = true
         * 会注册spring扫描类过滤器，加了特定注解的类会被扫描到
         * 带有@Component、@Repository、@Service、@Controller、@ManagedBean、@Named
         */
        super(registry, useDefaultFilters);

        setEnvironment(environment);

        setResourceLoader(resourceLoader);

        // 注册后置处理器
        registerAnnotationConfigProcessors(registry);

    }

    public DubboClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, Environment environment,
                                               ResourceLoader resourceLoader) {

        this(registry, false, environment, resourceLoader);

    }
    // todo need pr 下面两个方法没用，多此一举
    @Override
    public Set<BeanDefinitionHolder> doScan(String... basePackages) {
        // 扫描指定的包，并注册beanDefinition
        return super.doScan(basePackages);
    }

    @Override
    public boolean checkCandidate(String beanName, BeanDefinition beanDefinition) throws IllegalStateException {
        // 检查registry是否存在（内部通过beanName 或 beanDefinition，返回true 表示不存在）
        return super.checkCandidate(beanName, beanDefinition);
    }

}

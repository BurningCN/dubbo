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

import org.apache.dubbo.common.Version;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.MetricsConfig;
import org.apache.dubbo.config.ModuleConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.SslConfig;
import org.apache.dubbo.config.spring.ConfigCenterBean;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.beans.factory.config.ConfigurableSourceBeanMetadataElement;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.w3c.dom.Element;

import static org.apache.dubbo.config.spring.util.DubboBeanUtils.registerCommonBeans;

/**
 * DubboNamespaceHandler
 *
 * @export
 */
// OK
// Dubbo基于Spring提供的NamespaceHandler和BeanDefinitionParser来扩展了自己XML Schemas
public class DubboNamespaceHandler extends NamespaceHandlerSupport implements ConfigurableSourceBeanMetadataElement {

    static {
        //去classpath下检查是否有其他的同名class，否则会打错误日志
        Version.checkDuplicate(DubboNamespaceHandler.class);
    }

    @Override
    public void init() {
        // 我们看到，dubbo的各个标签都是通过DubboBeanDefinitionParser来解析的。下面的操作就是在命名空间处理器注册多个自定义的BeanDefinitionParser
        // 即DubboBeanDefinitionParser，通常为每一个xsd:element都要注册一个BeanDefinitionParser（有多少个element去看dubbo.xsd）。


        // 解析<dubbo:application/>标签  全局配置，用于配置当前应用信息，不管该应用是提供者还是消费者
            // (1).每个大标签都有与之对应的类，比如 <dubbo:application/> 有对应 ApplicationConfig，标签里的一些信息会通过对应类的set等方法赋值进去
            // <dubbo:application name="csp-service"/> 的name值会调用ApplicationConfig的setName方法赋值到自己的name属性上
            // (2).required参数表示是不是必须要具有这个标签/XXConfig类
            // (3).有多少个element/标签就会创建多少个DubboBeanDefinitionParser实例
        registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));

        // 解析<dubbo:module/>标签  模块配置 , 用于配置当前模块信息
        registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));

        // 解析<dubbo:registry/>标签  用于配置连接注册中心相关信息
        registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));

        registerBeanDefinitionParser("config-center", new DubboBeanDefinitionParser(ConfigCenterBean.class, true));
        registerBeanDefinitionParser("metadata-report", new DubboBeanDefinitionParser(MetadataReportConfig.class, true));

        // 解析<dubbo:monitor/>标签  用于配置连接监控中心相关信息
        registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
        registerBeanDefinitionParser("metrics", new DubboBeanDefinitionParser(MetricsConfig.class, true));
        registerBeanDefinitionParser("ssl", new DubboBeanDefinitionParser(SslConfig.class, true));

        // 解析<dubbo:provider/>标签   当 ProtocolConfig 和 ServiceConfig 某属性没有配置时，采用此缺省值，可选
        registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));

        // 解析<dubbo:consumer/>标签  当 ReferenceConfig 某属性没有配置时，采用此缺省值，可选
        registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));

        // 解析<dubbo:protocol/>标签   协议
        registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));

        // 解析<dubbo:service/>标签   用于暴露一个服务，定义服务的元信息，一个服务可以用多个协议暴露，一个服务也可以注册到多个注册中心。注意传入的ServiceBean
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));

        // 解析<dubbo:reference/> 标签  用于创建一个远程服务代理，一个引用可以指向多个注册中心
        registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));

        // 解析<dubbo:annotation/>标签
        registerBeanDefinitionParser("annotation", new AnnotationBeanDefinitionParser());
    }

    /**
     * Override {@link NamespaceHandlerSupport#parse(Element, ParserContext)} method
     *
     * @param element       {@link Element}
     * @param parserContext {@link ParserContext}
     * @return
     * @since 2.7.5
     */
    // 每次解析一个大标签（比如 <dubbo:application/> ）的时候都会调用下面的方法
    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        BeanDefinitionRegistry registry = parserContext.getRegistry();
        // 进去
        registerAnnotationConfigProcessors(registry);
        /**
         * @since 2.7.8
         * issue : https://github.com/apache/dubbo/issues/6275
         */
        // 注册自己定义的一些通用组件
        registerCommonBeans(registry);
        // 解析xml的Element并生成BeanDefinition，内部会调用DubboBeanDefinitionParser的parse方法。// 进去
        BeanDefinition beanDefinition = super.parse(element, parserContext);
        setSource(beanDefinition);
        return beanDefinition;
    }

    /**
     * Register the processors for the Spring Annotation-Driven features
     *
     * @param registry {@link BeanDefinitionRegistry}
     * @see AnnotationConfigUtils
     * @since 2.7.5
     */
    // 利用spring的api注册一些 Annotation-Driven 相关处理器bean（比如@Autowired的，具体可以进去看）
    private void registerAnnotationConfigProcessors(BeanDefinitionRegistry registry) {
        AnnotationConfigUtils.registerAnnotationConfigProcessors(registry);
    }
}

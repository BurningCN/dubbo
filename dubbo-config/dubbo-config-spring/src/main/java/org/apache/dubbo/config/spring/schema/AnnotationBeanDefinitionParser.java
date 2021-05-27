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

import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.apache.dubbo.config.spring.beans.factory.annotation.ServiceAnnotationBeanPostProcessor;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import static org.springframework.util.StringUtils.commaDelimitedListToStringArray;
import static org.springframework.util.StringUtils.trimArrayElements;

/**
 * @link BeanDefinitionParser}
 * @see ServiceAnnotationBeanPostProcessor
 * @see ReferenceAnnotationBeanPostProcessor
 * @since 2.5.9
 */

// OK
// 这个和DubboBeanDefinitionParser作用是一样的，自定义的BeanDefinition解析器，都是用来用来解析 XSD 文件的。
// 前者是直接实现BeanDefinitionParser接口，该类是继承AbstractSingleBeanDefinitionParser，其实都差不多。
// 该类是处理xsd文件中 <dubbo:annotation package="" /> 这个标签的内容值，拿到packagesToScan，进行注解扫描
public class AnnotationBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    /**
     * parse
     * <prev>
     * &lt;dubbo:annotation package="" /&gt;
     * </prev>
     *
     * @param element
     * @param parserContext
     * @param builder
     */
    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {

        String packageToScan = element.getAttribute("package");

        // 按照,分割成string数组，然后trim去除两边空格
        String[] packagesToScan = trimArrayElements(commaDelimitedListToStringArray(packageToScan));

        // 给构造函数传递参数，根据下面重写的getBeanClass方法，就是传递给ServiceAnnotationBeanPostProcessor类的构造方法，就拿到package了
        builder.addConstructorArgValue(packagesToScan);

        // 这一行代码，设置beanDefinition的role就是BeanDefinition.ROLE_INFRASTRUCTURE，所以会触发AOP的功能。
        builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

        /**
         * @since 2.7.6 Register the common beans
         * @since 2.7.8 comment this code line, and migrated to
         * @see DubboNamespaceHandler#parse(Element, ParserContext)
         * @see https://github.com/apache/dubbo/issues/6174
         */
        // registerCommonBeans(parserContext.getRegistry());
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }

    // 注意这里
    @Override
    protected Class<?> getBeanClass(Element element) {
        return ServiceAnnotationBeanPostProcessor.class;
    }

}

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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Activate. This annotation is useful for automatically activate certain extensions with the given criteria,
 * for examples: <code>@Activate</code> can be used to load certain <code>Filter</code> extension when there are
 * multiple implementations.
 * 激活。这个注释对于根据给定的条件自动激活某些扩展很有用，例如:<code>@Activate</code>可以用来加载特定的<code>过滤器</code>扩展当有多种实现时。
 * <ol>
 * <li>{@link Activate#group()} specifies group criteria. Framework SPI defines the valid group values.
 * <li>{@link Activate#value()} specifies parameter key in {@link URL} criteria.
 *
 * <li>{@link Activate#group()}指定分组标准/条件。框架SPI定义了有效的组值。
 * <li>{@link Activate#value()}指定参数key在URL的条件。
 * </ol>
 * SPI provider can call {@link ExtensionLoader#getActivateExtension(URL, String, String)} to find out all activated
 * extensions with the given criteria.
 * SPI提供程序可以调用{@link ExtensionLoader#getActivateExtension(URL, String, String)}来找出所有激活的扩展与给定的标准。
 *
 * @see SPI
 * @see URL
 * @see ExtensionLoader
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Activate {

    // 重点关注group、value、order（前两个最重要，用来指定这个扩展类在什么条件下激活），具体看英文注释。然后以ActivateExt1这个SPI接口为例，看下实现类

    /**
     * Activate the current extension when one of the groups matches. The group passed into
     * {@link ExtensionLoader#getActivateExtension(URL, String, String)} will be used for matching.
     *
     * @return group names to match
     * @see ExtensionLoader#getActivateExtension(URL, String, String)
     */

    // 如MonitorFilter类上的@Activate(group = {Constants.PROVIDER, Constants.CONSUMER})
    // 表示如果过滤器使用方（通过group指定）属于Constants.PROVIDER（服务提供方）或者Constants.CONSUMER（服务消费方）就激活使用这个过滤器。
    String[] group() default {};

    /**
     * Activate the current extension when the specified keys appear in the URL's parameters.
     * <p>
     * For example, given <code>@Activate("cache, validation")</code>, the current extension will be return only when
     * there's either <code>cache</code> or <code>validation</code> key appeared in the URL's parameters.
     * </p>
     *
     * @return URL parameter keys
     * @see ExtensionLoader#getActivateExtension(URL, String)
     * @see ExtensionLoader#getActivateExtension(URL, String, String)
     */

    // 如TokenFilter类上的@Activate(group = Constants.PROVIDER, value = Constants.TOKEN_KEY)
    // 表示如果过滤器使用方（通过group指定）属于Constants.PROVIDER（服务提供方）并且 URL中有参数 Constants.TOKEN_KEY（token）时就激活使用这个过滤器。
    String[] value() default {};

    /**
     * Relative ordering info, optional
     * Deprecated since 2.7.0
     *
     * @return extension list which should be put before the current one
     */
    @Deprecated
    String[] before() default {};

    /**
     * Relative ordering info, optional
     * Deprecated since 2.7.0
     *
     * @return extension list which should be put after the current one
     */
    @Deprecated
    String[] after() default {};

    /**
     * Absolute ordering info, optional
     *
     * @return absolute ordering info
     */
    int order() default 0;
}
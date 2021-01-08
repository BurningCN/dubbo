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
package org.apache.dubbo.common.config.configcenter;

import org.apache.dubbo.common.URL;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;

/**
 * Abstract {@link DynamicConfigurationFactory} implementation with cache ability
 *
 * @see DynamicConfigurationFactory
 * @since 2.7.5
 */
// OK
// dubbo的各种抽象工厂都是这种结构，有一个AbstractXXFactory，可以搜下AbstractFactory发现匹配到很多
// 包装工厂产品也是这样，具体产品->Abstract产品->产品接口
public abstract class AbstractDynamicConfigurationFactory implements DynamicConfigurationFactory {

    private volatile Map<String, DynamicConfiguration> dynamicConfigurations = new ConcurrentHashMap<>();

    @Override
    public final DynamicConfiguration getDynamicConfiguration(URL url) {
        // toServiceString把URL对象转化为字符串，进去
        String key = url == null ? DEFAULT_KEY : url.toServiceString();
        return dynamicConfigurations.computeIfAbsent(key, k -> createDynamicConfiguration(url));
    }

    // 被不同的子类实现
    protected abstract DynamicConfiguration createDynamicConfiguration(URL url);
}

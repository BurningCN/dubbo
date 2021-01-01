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
package org.apache.dubbo.common.config;

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.ConfigUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Configuration from system properties and dubbo.properties
 */
// OK
public class PropertiesConfiguration implements Configuration {

    public PropertiesConfiguration() {
        // 获取SPI对应的Loader
        ExtensionLoader<OrderedPropertiesProvider> propertiesProviderExtensionLoader =
                ExtensionLoader.getExtensionLoader(OrderedPropertiesProvider.class);
        // 获取所有的扩展名
        Set<String> propertiesProviderNames = propertiesProviderExtensionLoader.getSupportedExtensions();
        if (propertiesProviderNames == null || propertiesProviderNames.isEmpty()) {
            return;
        }
        // 获取所有扩展实例，目前有两个，MockOrderedPropertiesProviderTest1\MockOrderedPropertiesProviderTest2，
        // 去看下这两个类
        List<OrderedPropertiesProvider> orderedPropertiesProviders = new ArrayList<>();
        for (String propertiesProviderName : propertiesProviderNames) {
            orderedPropertiesProviders.add(propertiesProviderExtensionLoader.getExtension(propertiesProviderName));
        }


        //order the propertiesProvider according the priority descending
        orderedPropertiesProviders.sort((OrderedPropertiesProvider a, OrderedPropertiesProvider b) -> {
            // 根据这两个方法的返回值比较，排序一下，倒序
            return b.priority() - a.priority();
        });

        //load the default properties
        // 加载默认的配置属性，进去
        Properties properties = ConfigUtils.getProperties();

        //override the properties.
        for (OrderedPropertiesProvider orderedPropertiesProvider :
                orderedPropertiesProviders) {
            // 将两个实例的initProperties返回的Properties对象填充到properties，注意可能会覆盖，
            // 具体看下两个实现类的，他们都配置了同一个key（testKey），根据优先级排序，较后的会把前面同名key的val覆盖掉。
            properties.putAll(orderedPropertiesProvider.initProperties());
        }

        // 所以PropertiesConfiguration的信息主要是存到ConfigUtils，进去
        ConfigUtils.setProperties(properties);
    }

    @Override
    public Object getInternalProperty(String key) {
        return ConfigUtils.getProperty(key);
    }
}

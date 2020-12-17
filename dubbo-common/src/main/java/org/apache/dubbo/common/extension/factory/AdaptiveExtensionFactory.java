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
package org.apache.dubbo.common.extension.factory;

import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionFactory;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AdaptiveExtensionFactory
 */
// OK
@Adaptive
public class AdaptiveExtensionFactory implements ExtensionFactory {

    // 根据 loadFile()方法的缓存原则，AdaptiveExtensionFactory实例中的factories的size返回应为2，里面只会保存这两个类实例：
    // spring=com.alibaba.dubbo.config.spring.extension.SpringExtensionFactory
    // spi=com.alibaba.dubbo.common.extension.factory.SpiExtensionFactory
    // 因为adaptive=com.alibaba.dubbo.common.extension.factory.AdaptiveExtensionFactory是保存在cachedAdaptiveClass上的
    private final List<ExtensionFactory> factories;

    public AdaptiveExtensionFactory() {
        ExtensionLoader<ExtensionFactory> loader = ExtensionLoader.getExtensionLoader(ExtensionFactory.class);
        List<ExtensionFactory> list = new ArrayList<ExtensionFactory>();
        for (String name : loader.getSupportedExtensions()) {
            list.add(loader.getExtension(name));
        }
        // unmodifiableList不可变。SpiExtensionFactory
        factories = Collections.unmodifiableList(list);
    }

    //分析清楚AdaptiveExtensionFactory类的getExtension方法，就可以明白这个IOC容器是如何取出需要的SPI实例依赖了
    @Override
    public <T> T getExtension(Class<T> type, String name) {
        // 调用SpringExtensionFactory、、SpringExtensionFactory类的getExtension方法
        // 前者用于创建"自适应的拓展"（注意自适应指的是Adaptive），后者是用于从 Spring 的 IOC 容器中获取所需的拓展
        for (ExtensionFactory factory : factories) {
            // 看看SpringExtensionFactory
            T extension = factory.getExtension(type, name);
            if (extension != null) {
                return extension;
            }
        }
        return null;
    }

}

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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.rpc.cluster.Constants.PRIORITY_KEY;

/**
 * Configurator. (SPI, Prototype, ThreadSafe)
 *
 */
// OK
public interface Configurator extends Comparable<Configurator> {

    /**
     * Get the configurator url.
     *
     * @return configurator url.
     */
    URL getUrl();

    /**
     * Configure the provider url.
     *
     * @param url - old provider url.
     * @return new provider url.
     */
    URL configure(URL url);


    /**
     * Convert override urls to map for use when re-refer. Send all rules every time, the urls will be reassembled and
     * calculated
     * 将覆盖url转换为映射，以便重新引用时使用。每次发送所有规则，url将被重新组装和计算
     *
     * URL contract:  合同，契约；婚约；（非正式）暗杀协议；（桥牌）定约
     * <ol>
     * <li>override://0.0.0.0/...( or override://ip:port...?anyhost=true)&para1=value1... means global rules
     * (all of the providers take effect)</li> 意味着全局规则(所有提供者生效)
     * <li>override://ip:port...?anyhost=false Special rules (only for a certain provider)</li>特殊规则(仅适用于某一提供者)
     * <li>override:// rule is not supported... ,needs to be calculated by registry itself</li> 规则不支持…，需要由注册表本身计算
     * <li>override://0.0.0.0/ without parameters means clearing the override</li> 没有参数意味着清除覆盖
     * </ol>
     *
     * @param urls URL list to convert
     * @return converted configurator list
     */
    // 传进来的url参数就像上面的li示例那样
    static Optional<List<Configurator>> toConfigurators(List<URL> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            return Optional.empty();
        }

        // 获取自适应配置工厂
        ConfiguratorFactory configuratorFactory = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                .getAdaptiveExtension();

        // 待返回容器
        List<Configurator> configurators = new ArrayList<>(urls.size());
        for (URL url : urls) {
            // 有一个url是empty://的，清空容器，结束循环
            if (EMPTY_PROTOCOL.equals(url.getProtocol())) {
                configurators.clear();
                break;
            }
            Map<String, String> override = new HashMap<>(url.getParameters());
            // The anyhost parameter of override may be added automatically, it can't change the judgement of changing url
            // 覆盖可自动添加任意主机参数，不能改变对变化url的判断
            override.remove(ANYHOST_KEY);
            // 移除anyhost参数之后，没有剩余参数了，不处理该url
            if (CollectionUtils.isEmptyMap(override)) {
                continue;
            }
            // 根据工厂获取具体Configurator（两种具体产品），内部会从url取参数值来确定使用哪种产品（具体看ConfiguratorFactory$Adaptive.java）
            configurators.add(configuratorFactory.getConfigurator(url));// 比如url为override:// 那么就是OverrideConfigurator
        }
        // 排序，去看下面的compareTo
        Collections.sort(configurators);
        return Optional.of(configurators);
    }

    /**
     * Sort by host, then by priority
     * 1. the url with a specific host ip should have higher priority than 0.0.0.0
     * 2. if two url has the same host, compare by priority value；
     */
    @Override
    default int compareTo(Configurator o) {
        if (o == null) {
            return -1;
        }

        // 根据host字符串的自然顺序排
        int ipCompare = getUrl().getHost().compareTo(o.getUrl().getHost());
        // host is the same, sort by priority
        if (ipCompare == 0) {
            int i = getUrl().getParameter(PRIORITY_KEY, 0);
            int j = o.getUrl().getParameter(PRIORITY_KEY, 0);
            // Integer的compare，String的compareTo
            return Integer.compare(i, j);
        } else {
            return ipCompare;
        }
    }
}

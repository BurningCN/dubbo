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
package org.apache.dubbo.metadata;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.SPI;

import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.CONFIG_MAPPING_TYPE;
import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.apache.dubbo.common.utils.StringUtils.SLASH;
import static org.apache.dubbo.metadata.DynamicConfigurationServiceNameMapping.DEFAULT_MAPPING_GROUP;

/**
 * The interface for Dubbo service name Mapping
 *
 * @since 2.7.5
 */
@SPI("config") // todo need pr 使用静态常量
// 有两个扩展，扩展名分别是config和matedata，表示相关的记录是存到config-center对应的DynamicConfiguration(比如zkDynamicConfiguration)，还是metadata对应的metadataReport（比如zkMetadataReport）
public interface ServiceNameMapping {

    /**
     * Map the specified Dubbo service interface, group, version and protocol to current Dubbo service name
     * 将指定的Dubbo服务接口、组、版本和协议映射到当前的Dubbo服务名称
     */
    void map(URL url);

    /**
     * Get the service names from the specified Dubbo service interface, group, version and protocol
     *
     * @return
     */
    Set<String> getAndListen(URL url, MappingListener mappingListener);

    /**
     * Get the default extension of {@link ServiceNameMapping}
     *
     * @return non-null {@link ServiceNameMapping}
     * @see DynamicConfigurationServiceNameMapping
     */
    static ServiceNameMapping getDefaultExtension() {
        return getExtensionLoader(ServiceNameMapping.class).getDefaultExtension();
    }

    static ServiceNameMapping getExtension(String name) { // name为null的话，用"config"扩展名，即 DynamicConfigurationServiceNameMapping
        return getExtensionLoader(ServiceNameMapping.class).getExtension(name == null ? CONFIG_MAPPING_TYPE : name);
    }

    // todo need pr 下三个参数可以删除
    static String buildGroup(String serviceInterface, String group, String version, String protocol) {
        //        the issue : https://github.com/apache/dubbo/issues/4671
        //        StringBuilder groupBuilder = new StringBuilder(serviceInterface)
        //                .append(KEY_SEPARATOR).append(defaultString(group))
        //                .append(KEY_SEPARATOR).append(defaultString(version))
        //                .append(KEY_SEPARATOR).append(defaultString(protocol));
        //        return groupBuilder.toString();
        // /mapping/ (注意还有/services、/dubbo，分别对应ZKServiceDiscovery和ZKMetaReport 都是不一样的。还有/dubbo/config---这个是config-center相关的，对应ZookeeperDynamicConfiguration)
        return DEFAULT_MAPPING_GROUP + SLASH + serviceInterface;
    }
}

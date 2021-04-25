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
package org.apache.dubbo.registry.client;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.support.AbstractRegistryFactory;

import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.SERVICE_REGISTRY_PROTOCOL;
import static org.apache.dubbo.registry.Constants.DEFAULT_REGISTRY;

// OK
public class ServiceDiscoveryRegistryFactory extends AbstractRegistryFactory {

    @Override
    protected Registry createRegistry(URL url) {
        // url eg service-discovery-registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=demo-provider&dubbo=2.0.2&id=org.apache.dubbo.config.RegistryConfig&interface=org.apache.dubbo.registry.RegistryService&metadata-type=remote&pid=67248&registry=zookeeper&registry-type=service&timestamp=1619165300619
        if (SERVICE_REGISTRY_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
            // 获取registry参数值，比如zookeeper（默认为dubbo）
            String protocol = url.getParameter(REGISTRY_KEY, DEFAULT_REGISTRY);
            // 转化为具体的协议开头，比如zookeeper://， 这点和InterfaceCompatibleRegistryProtocol#getRegistryUrl的逻辑一致
            url = url.setProtocol(protocol).removeParameter(REGISTRY_KEY);
        }
        // 进去
        return new ServiceDiscoveryRegistry(url);
    }

}

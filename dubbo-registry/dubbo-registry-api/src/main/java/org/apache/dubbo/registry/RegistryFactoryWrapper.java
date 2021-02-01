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

package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.Collections;

// OK
// 类似 ProtocolListenerWrapper
public class RegistryFactoryWrapper implements RegistryFactory {
    private RegistryFactory registryFactory;

    // 拷贝构造函数，满足isWrapperClass
    public RegistryFactoryWrapper(RegistryFactory registryFactory) {
        this.registryFactory = registryFactory;
    }

    @Override
    public Registry getRegistry(URL url) {
        // 类似 ProtocolListenerWrapper的export方法：
        // return new ListenerExporterWrapper<T>(protocol.export(invoker),
        //                Collections.unmodifiableList(ExtensionLoader.getExtensionLoader(ExporterListener.class)
        //                        .getActivateExtension(invoker.getUrl(), EXPORTER_LISTENER_KEY)));
        // ListenerRegistryWrapper进去
        return new ListenerRegistryWrapper(registryFactory.getRegistry(url),
                Collections.unmodifiableList(ExtensionLoader.getExtensionLoader(RegistryServiceListener.class)
                        .getActivateExtension(url, "registry.listeners")));
    }
}

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
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.model.ApplicationModel.getName;

/**
 * The {@link ServiceNameMapping} implementation based on {@link DynamicConfiguration}
 */
public class DynamicConfigurationServiceNameMapping implements ServiceNameMapping {

    public static String DEFAULT_MAPPING_GROUP = "mapping";

    private static final List<String> IGNORED_SERVICE_INTERFACES = asList(MetadataService.class.getName());

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void map(URL url) {
        String serviceInterface = url.getServiceInterface();
        String group = url.getParameter(GROUP_KEY);
        String version = url.getParameter(VERSION_KEY);
        String protocol = url.getProtocol();

        if (IGNORED_SERVICE_INTERFACES.contains(serviceInterface)) {
            return;
        }

        // 获取动态配置实例，先前注册过，这里的实际类型为CompositeDynamicConfiguration，其内部包含zkDynamicConfiguration
        DynamicConfiguration dynamicConfiguration = DynamicConfiguration.getDynamicConfiguration();

        // the Dubbo Service Key as group
        // the service(application) name as key
        // It does matter whatever the content is, we just need a record
        // appName
        String key = getName();
        String content = valueOf(System.currentTimeMillis());

        execute(() -> {
            // 三个参数比如 ： demo-provider、mapping/samples.servicediscovery.demo.DemoService、时间戳
            // 最后在zk会生成path和content分别如下：（生成这个path的目的是在消费端没有指定provided-by参数值的时候，想要获取支持的app级别的服务，就会使用到，具体可以看下面的getAndListener）
            // get /dubbo/config/mapping/samples.servicediscovery.demo.DemoService/demo-provider
            // 1619495549553

            // metadata模式的path 为 /dubbo/mapping/samples.servicediscovery.demo.DemoService/demo-provider
            dynamicConfiguration.publishConfig(key, ServiceNameMapping.buildGroup(serviceInterface, group, version, protocol), content);
            if (logger.isInfoEnabled()) {
                logger.info(String.format("Dubbo service[%s] mapped to interface name[%s].",
                        group, serviceInterface, group));
            }
        });
    }

    @Override
    // 这里的listener参数没有用，另一个实现类是有用到的
    public Set<String> getAndListen(URL url, MappingListener mappingListener) {
        String serviceInterface = url.getServiceInterface();
        String group = url.getParameter(GROUP_KEY);
        String version = url.getParameter(VERSION_KEY);
        String protocol = url.getProtocol();
        DynamicConfiguration dynamicConfiguration = DynamicConfiguration.getDynamicConfiguration();

        Set<String> serviceNames = new LinkedHashSet<>();
        execute(() -> {
            // 从 dynamicConfiguration 获取节点值
            Set<String> keys = dynamicConfiguration
                    // buildGroup eg mapping/samples.servicediscovery.demo.DemoService
                    // getConfigKeys 内部生成的path 为 /dubbo/config/mapping/samples.servicediscovery.demo.DemoService
                    // 这个path是父级节点，其有一个子节点，是appName，比如为demo-provider，这个子节点的值为当前时间戳，这个path的生成过程是
                    // provider生成的，具体逻辑可以看上面的 map 方法
                    .getConfigKeys(ServiceNameMapping.buildGroup(serviceInterface, group, version, protocol));
            serviceNames.addAll(keys);
        });
        return Collections.unmodifiableSet(serviceNames);
    }

    private void execute(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            if (logger.isWarnEnabled()) {
                logger.warn(e.getMessage(), e);
            }
        }
    }
}

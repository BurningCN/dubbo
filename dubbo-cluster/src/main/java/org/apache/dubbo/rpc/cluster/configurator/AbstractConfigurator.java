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
package org.apache.dubbo.rpc.cluster.configurator;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.cluster.Configurator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;
import static org.apache.dubbo.common.constants.CommonConstants.ENABLED_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACES;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.COMPATIBLE_CONFIG_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.CONFIG_VERSION_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.OVERRIDE_PROVIDERS_KEY;

/**
 * AbstractOverrideConfigurator
 */
// OK
// 集群中关于configurator实现的部分，有两种配置方式，分别是不存在再添加和覆盖添加
public abstract class AbstractConfigurator implements Configurator {

    // 这个就是override url
    private final URL configuratorUrl;

    public AbstractConfigurator(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("configurator url == null");
        }
        this.configuratorUrl = url;
    }

    @Override
    public URL getUrl() {
        return configuratorUrl;
    }

    @Override
    public URL configure(URL url) {
        // If override url is not enabled or is invalid, just return.
        // enable参数为true或者为null那么就不走下面的if内部逻辑，表示允许（用configuratorUrl的部分参数）覆盖url
            if (!configuratorUrl.getParameter(ENABLED_KEY, true) ||
                // host的检查逻辑
                configuratorUrl.getHost() == null || url == null || url.getHost() == null) {
            return url;
        }
        /*
         * This if branch is created since 2.7.0.
         */
        String apiVersion = configuratorUrl.getParameter(CONFIG_VERSION_KEY);
        if (StringUtils.isNotEmpty(apiVersion)) {
            // 获取两个url的side参数值
            String currentSide = url.getParameter(SIDE_KEY);
            String configuratorSide = configuratorUrl.getParameter(SIDE_KEY);
            // 两个url的side相等，且side为consumer，且configuratorUrl的port为0，默认值就是0，即没port(configuratorUrl本身就只有ip没有port，可以看Configurator#configure注释)
            if (currentSide.equals(configuratorSide) && CONSUMER.equals(configuratorSide) && 0 == configuratorUrl.getPort()) {
                // 获取本机当前本地ip作为host
                url = configureIfMatch(NetUtils.getLocalHost(), url);
            // 两个url的side相等，且side为provider，且两个url的port相等
            } else if (currentSide.equals(configuratorSide) && PROVIDER.equals(configuratorSide) && url.getPort() == configuratorUrl.getPort()) {
                // 用url自己的host值
                url = configureIfMatch(url.getHost(), url);
            }
        }
        /*
         * This else branch is deprecated and is left only to keep compatibility with versions before 2.7.0
         */
        else {
            // 兼容旧版本，进去
            url = configureDeprecated(url);
        }
        return url;
    }

    @Deprecated
    private URL configureDeprecated(URL url) {
        // If override url has port, means it is a provider address. We want to control a specific provider with this override url, it may take effect on the specific provider instance or on consumers holding this provider instance.
        // 如果configuratorUrl有端口，则意味着它是提供者地址。我们想用这个覆盖url控制特定的提供者，它可能对特定的提供者实例或持有该提供者实例的消费者生效。
        if (configuratorUrl.getPort() != 0) {
            if (url.getPort() == configuratorUrl.getPort()) {
                // 两个url的port相等
                return configureIfMatch(url.getHost(), url);
            }
        } else {
            // 没有端口，override输入消费端地址 或者 0.0.0.0
            // 1.如果是消费端地址，则意图是控制消费者机器，必定在消费端生效，提供端忽略；
            // 2.如果是0.0.0.0可能是控制提供端，也可能是控制提供端
            if (url.getParameter(SIDE_KEY, PROVIDER).equals(CONSUMER)) {
                // NetUtils.getLocalHost是消费端注册到zk的消费者地址
                return configureIfMatch(NetUtils.getLocalHost(), url);
            } else if (url.getParameter(SIDE_KEY, CONSUMER).equals(PROVIDER)) {
                // 控制所有提供端，地址必定是0.0.0.0，否则就要配端口从而执行上面的if分支了
                return configureIfMatch(ANYHOST_VALUE, url);
            }
        }
        return url;
    }

    // 该方法是当条件匹配时，才对url进行配置。
    // 传进的host有3种值，ANYHOST_VALUE、url.getHost()、NetUtils.getLocalHost()
    private URL configureIfMatch(String host, URL url) {
        // 判断1 host
        if (ANYHOST_VALUE.equals(configuratorUrl.getHost()) || host.equals(configuratorUrl.getHost())) {
            // TODO, to support wildcards
            String providers = configuratorUrl.getParameter(OVERRIDE_PROVIDERS_KEY);
            // 判断2 provider
            if (StringUtils.isEmpty(providers) || providers.contains(url.getAddress()) ||
                    providers.contains(ANYHOST_VALUE)) {

                String configApplication = configuratorUrl.getParameter(APPLICATION_KEY,
                        configuratorUrl.getUsername());
                String currentApplication = url.getParameter(APPLICATION_KEY, url.getUsername());
                // 判断3 application
                if (configApplication == null || ANY_VALUE.equals(configApplication)
                        || configApplication.equals(currentApplication)) {

                    Set<String> conditionKeys = new HashSet<String>();
                    // 填充处1 固定的值填充 这些参数将从configuratorUrl移除（如果后续的for里面的if条件穿过的话）
                    conditionKeys.add(CATEGORY_KEY);
                    conditionKeys.add(Constants.CHECK_KEY);
                    conditionKeys.add(DYNAMIC_KEY);
                    conditionKeys.add(ENABLED_KEY);
                    conditionKeys.add(GROUP_KEY);
                    conditionKeys.add(VERSION_KEY);
                    conditionKeys.add(APPLICATION_KEY);
                    conditionKeys.add(SIDE_KEY);
                    conditionKeys.add(CONFIG_VERSION_KEY);
                    conditionKeys.add(COMPATIBLE_CONFIG_KEY);
                    conditionKeys.add(INTERFACES);
                    // todo need pr-pr 1.上面集合的填充处可以完全放在下面的for循环后面，防止下面for循环内部if直接return，导致无效add。2.还有一点就是下面Application的判定前面已经判断了，可以直接去除
                    for (Map.Entry<String, String> entry : configuratorUrl.getParameters().entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if (key.startsWith("~") || APPLICATION_KEY.equals(key) || SIDE_KEY.equals(key)) {
                            // 填充处2 configuratorUrl的所有参数中能满足上述条件的参数填充 ---> 其实这里压根不需要重复add（后两个已经add了），只需要对~xx的进行add就行了 ----> 这点已经发起issue并被改正
                            conditionKeys.add(key);
                            // 两个url的参数值比较，如果不等，直接返回url本身，注意是仅限于上面三种参数(~、application、side)的值比较
                            if (value != null && !ANY_VALUE.equals(value)
                                    && !value.equals(url.getParameter(key.startsWith("~") ? key.substring(1) : key))) {
                                return url;
                            }
                        }
                    }
                    // configuratorUrl移除conditionKeys容器中的参数
                    // doConfigure抽象方法。子类给出自己实现，去看下实现
                    return doConfigure(url, configuratorUrl.removeParameters(conditionKeys));
                }
            }
        }
        return url;
    }

    protected abstract URL doConfigure(URL currentUrl, URL configUrl);

}

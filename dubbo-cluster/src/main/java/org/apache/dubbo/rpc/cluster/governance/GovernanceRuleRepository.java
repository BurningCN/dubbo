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
package org.apache.dubbo.rpc.cluster.governance;

import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.extension.SPI;

@SPI("default")
public interface GovernanceRuleRepository {

    String DEFAULT_GROUP = "dubbo";

    /**
     * {@link #addListener(String, String, ConfigurationListener)}
     *
     * @param key      the key to represent a configuration
     * @param listener configuration listener
     */
    default void addListener(String key, ConfigurationListener listener) {
        // 进去
        addListener(key, DEFAULT_GROUP, listener);
    }


    /**
     * {@link #removeListener(String, String, ConfigurationListener)}
     *
     * @param key      the key to represent a configuration
     * @param listener configuration listener
     */
    default void removeListener(String key, ConfigurationListener listener) {
        removeListener(key, DEFAULT_GROUP, listener);
    }

    /**
     * Register a configuration listener for a specified key
     * The listener only works for service governance（服务治理） purpose, so the target group would always be the value user
     * specifies at startup or 'dubbo' by default. This method will only register listener, which means it will not
     * trigger a notification that contains the current value.
     *
     * 侦听器仅用于服务治理目的，因此目标组将始终是用户在启动时指定的值，或者默认为“dubbo”。此方法将只注册侦听器，这意味着它不会触发包含当前值的通知。
     *
     * @param key      the key to represent a configuration
     * @param group    the group where the key belongs to
     * @param listener configuration listener
     */
    // 看实现
    void addListener(String key, String group, ConfigurationListener listener);

    /**
     * Stops one listener from listening to value changes in the specified key.
     *
     * @param key      the key to represent a configuration
     * @param group    the group where the key belongs to
     * @param listener configuration listener
     */
    void removeListener(String key, String group, ConfigurationListener listener);

    /**
     * Get the governance rule mapped to the given key and the given group
     * 获取映射到给定键和给定组的治理规则
     * @param key   the key to represent a configuration
     * @param group the group where the key belongs to
     * @return target configuration mapped to the given key and the given group
     */
    default String getRule(String key, String group) {
        // 进去
        return getRule(key, group, -1L);
    }

    /**
     * Get the governance rule mapped to the given key and the given group. If the
     * rule fails to return after timeout exceeds, IllegalStateException will be thrown.
     *  获取映射到给定键和给定组的治理规则。如果规则在timeout超过后返回失败，将抛出IllegalStateException。
     * @param key     the key to represent a configuration
     * @param group   the group where the key belongs to
     * @param timeout timeout value for fetching the target config
     * @return target configuration mapped to the given key and the given group, IllegalStateException will be thrown
     * if timeout exceeds.
     */
    // 看实现
    String getRule(String key, String group, long timeout) throws IllegalStateException;
}

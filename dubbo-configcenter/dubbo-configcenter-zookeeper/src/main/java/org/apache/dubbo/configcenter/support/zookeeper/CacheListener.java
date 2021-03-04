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
package org.apache.dubbo.configcenter.support.zookeeper;

import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.zookeeper.DataListener;
import org.apache.dubbo.remoting.zookeeper.EventType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;

import static org.apache.dubbo.common.constants.CommonConstants.DOT_SEPARATOR;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_SEPARATOR;

/**
 *
 */

// OK
// 作为ZookeeperDynamicConfiguration的属性，其keyListeners存放了业务方指定的针对key的监听器，当key/path的节点信息发生变更，就会挨个调用
// ConfigurationListener.process方法
public class CacheListener implements DataListener {
    private static final int MIN_PATH_DEPTH = 5;

    // 参考类上面注释
    private Map<String, Set<ConfigurationListener>> keyListeners = new ConcurrentHashMap<>();
    // 用于等待connect zk 初始化完毕的闭锁
    private CountDownLatch initializedLatch;
    // 从ZookeeperDynamicConfiguration构造方法传过来的
    private String rootPath;

    public CacheListener(String rootPath, CountDownLatch initializedLatch) {
        this.rootPath = rootPath;
        // 跟下使用处
        this.initializedLatch = initializedLatch;
    }

    public void addListener(String key, ConfigurationListener configurationListener) {
        // 一个key可以有多个监听器
        Set<ConfigurationListener> listeners = this.keyListeners.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>());
        listeners.add(configurationListener);
    }

    public void removeListener(String key, ConfigurationListener configurationListener) {
        Set<ConfigurationListener> listeners = this.keyListeners.get(key);
        if (listeners != null) {
            listeners.remove(configurationListener);
        }
    }

    // 实现了DataListener接口的方法，主要在CuratorWatcherImpl的childEvent调用的时候，最后会触发下面的方法
    @Override
    public void dataChanged(String path, Object value, EventType eventType) {
        if (eventType == null) {
            return;
        }

        if (eventType == EventType.INITIALIZED) {
            // 当收到这个事件的时候，就countdown，ZookeeperDynamicConfiguration在initializedLatch.await就解除阻塞了，表示连接完成并初始化完毕
            initializedLatch.countDown();
            return;
        }

        if (path == null || (value == null && eventType != EventType.NodeDeleted)) {
            return;
        }

        // TODO We only care the changes happened on a specific path level, for example
        //  /dubbo/config/dubbo/configurators, other config changes not in this level will be ignored,
        // TODO，我们只关心发生在特定路径级别上的更改/dubbo/config/dubbo/configurators，其他配置更改在这个级别将被忽略，
        if (path.split("/").length >= MIN_PATH_DEPTH) {
            // 将path转化为key，就是/转化为.
            String key = pathToKey(path);
            ConfigChangeType changeType;
            switch (eventType) {
                case NodeCreated:
                    changeType = ConfigChangeType.ADDED;
                    break;
                case NodeDeleted:
                    changeType = ConfigChangeType.DELETED;
                    break;
                case NodeDataChanged:
                    changeType = ConfigChangeType.MODIFIED;
                    break;
                default:
                    return;
            }

            // 创建事件对象
            ConfigChangedEvent configChangeEvent = new ConfigChangedEvent(key, getGroup(path), (String) value, changeType);
            // 获取指定path下的业务方注册的监听器们
            Set<ConfigurationListener> listeners = keyListeners.get(path);
            if (CollectionUtils.isNotEmpty(listeners)) {
                // 挨个调用
                listeners.forEach(listener -> listener.process(configChangeEvent));
            }
        }
    }
    /**
     * This is used to convert a configuration nodePath into a key
     * TODO doc
     *
     * @param path
     * @return key (nodePath less the config root path)
     */
    private String pathToKey(String path) {
        if (StringUtils.isEmpty(path)) {
            return path;
        }
        String groupKey = path.replace(rootPath + PATH_SEPARATOR, "").replaceAll(PATH_SEPARATOR, DOT_SEPARATOR);
        return groupKey.substring(groupKey.indexOf(DOT_SEPARATOR) + 1);
    }

    private String getGroup(String path) {
        if (!StringUtils.isEmpty(path)) {
            int beginIndex = path.indexOf(rootPath + PATH_SEPARATOR);
            if (beginIndex > -1) {
                int endIndex = path.indexOf(PATH_SEPARATOR, beginIndex);
                if (endIndex > beginIndex) {
                    return path.substring(beginIndex, endIndex);
                }
            }
        }
        return path;
    }
}

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

package org.apache.dubbo.rpc.cluster.router.mesh.route;

import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.VsDestinationGroup;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.destination.DestinationRule;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.VirtualServiceRule;
import org.apache.dubbo.rpc.cluster.router.mesh.util.VsDestinationGroupRuleDispatcher;

import org.yaml.snakeyaml.Yaml;

import java.text.MessageFormat;
import java.util.Map;


public class MeshAppRuleListener implements ConfigurationListener {

    public static final Logger logger = LoggerFactory.getLogger(MeshAppRuleListener.class);

    // 派遣类一般内部都含有listener
    private final VsDestinationGroupRuleDispatcher vsDestinationGroupRuleDispatcher = new VsDestinationGroupRuleDispatcher();

    private final String appName;

    private VsDestinationGroup vsDestinationGroupHolder;

    // gx
    public MeshAppRuleListener(String appName) {
        this.appName = appName;
    }

    //主动调用或者被动调用，被动调用就是zk的节点变更
    public void receiveConfigInfo(String configInfo) {
        if(logger.isDebugEnabled()) {
            // 注意这个api的使用，String.format也可以，不过都是%s 这里是 0 1
            // 注意这个日志。也能知道这个类的一些含义 是给app级别的
            logger.debug(MessageFormat.format("[MeshAppRule] Received rule for app [{0}]: {1}.",
                    appName, configInfo));
        }
        try {

            VsDestinationGroup vsDestinationGroup = new VsDestinationGroup();
            vsDestinationGroup.setAppName(appName);

            Yaml yaml = new Yaml();
            Yaml yaml2 = new Yaml();
            // 注意和loadAs的区别，这里loadAll获取到了迭代器
            Iterable<Object> objectIterable = yaml.loadAll(configInfo);
            for (Object result : objectIterable) {

                Map resultMap = (Map) result;
                // kind就这两种值
                if ("DestinationRule".equals(resultMap.get("kind"))) {
                    // yaml2.dump(result)是转化为yaml格式的字符串
                    DestinationRule destinationRule = yaml2.loadAs(yaml2.dump(result), DestinationRule.class);
                    vsDestinationGroup.getDestinationRuleList().add(destinationRule);

                } else if ("VirtualService".equals(resultMap.get("kind"))) {
                    VirtualServiceRule virtualServiceRule = yaml2.loadAs(yaml2.dump(result), VirtualServiceRule.class);
                    vsDestinationGroup.getVirtualServiceRuleList().add(virtualServiceRule);
                }
            }

            vsDestinationGroupHolder = vsDestinationGroup;
        } catch (Exception e) {
            logger.error("[MeshAppRule] parse failed: " + configInfo, e);
        }
        if (vsDestinationGroupHolder != null) {
            vsDestinationGroupRuleDispatcher.post(vsDestinationGroupHolder);
        }

    }

    // 该类本身是监听器 但是内部其实委托给了 vsDestinationGroupRuleDispatcher ，该类的注册订阅也是。
    public void register(MeshRuleRouter subscriber) {
        // 注册的同时获取通知，当然之前consumer订阅zk的时候同时拿到提供者列表
        if (vsDestinationGroupHolder != null) {
            subscriber.onRuleChange(vsDestinationGroupHolder);
        }
        // MeshRuleRouter VsDestinationGroupRuleListener 子父类关系
        vsDestinationGroupRuleDispatcher.register(subscriber);
    }


    public void unregister(MeshRuleRouter sub) {
        vsDestinationGroupRuleDispatcher.unregister(sub);
    }

    @Override
    public void process(ConfigChangedEvent event) {
        receiveConfigInfo(event.getContent());
    }
}

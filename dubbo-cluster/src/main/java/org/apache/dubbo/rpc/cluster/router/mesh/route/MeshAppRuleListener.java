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

    public static final String DESTINATION_RULE_KEY = "DestinationRule";

    public static final String VIRTUAL_SERVICE_KEY = "VirtualService";

    public static final String KIND_KEY = "kind";

    private final VsDestinationGroupRuleDispatcher vsDestinationGroupRuleDispatcher = new VsDestinationGroupRuleDispatcher();

    private final String appName;

    private VsDestinationGroup vsDestinationGroupHolder;

    public MeshAppRuleListener(String appName) {
        this.appName = appName;
    }

    public void receiveConfigInfo(String configInfo) {
        if(logger.isDebugEnabled()) {
            logger.debug(MessageFormat.format("[MeshAppRule] Received rule for app [{0}]: {1}.",
                    appName, configInfo));
        }
        try {

            VsDestinationGroup vsDestinationGroup = new VsDestinationGroup();
            vsDestinationGroup.setAppName(appName);

            Yaml yaml = new Yaml();
            Yaml yaml2 = new Yaml();
            Iterable<Object> objectIterable = yaml.loadAll(configInfo);
            for (Object result : objectIterable) {

                Map resultMap = (Map) result;
                if (DESTINATION_RULE_KEY.equals(resultMap.get(KIND_KEY))) {
                    DestinationRule destinationRule = yaml2.loadAs(yaml2.dump(result), DestinationRule.class);
                    vsDestinationGroup.getDestinationRuleList().add(destinationRule);

                } else if (VIRTUAL_SERVICE_KEY.equals(resultMap.get(KIND_KEY))) {
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

    public void register(MeshRuleRouter subscriber) {
        if (vsDestinationGroupHolder != null) {
            subscriber.onRuleChange(vsDestinationGroupHolder);
        }
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

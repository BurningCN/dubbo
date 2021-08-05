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

package org.apache.dubbo.rpc.cluster.router.mesh.util;

import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.VsDestinationGroup;

import java.util.Set;


// 理清楚几个关系
// VsDestinationGroupRuleDispatcher
// VsDestinationGroupRuleListener
// VsDestinationGroup
//  VirtualServiceRule
//  DestinationRule
public class VsDestinationGroupRuleDispatcher {

    private Set<VsDestinationGroupRuleListener> listenerSet = new ConcurrentHashSet<>();

    public synchronized void post(VsDestinationGroup vsDestinationGroup) {
        for (VsDestinationGroupRuleListener vsDestinationGroupRuleListener : listenerSet) {
            try {
                vsDestinationGroupRuleListener.onRuleChange(vsDestinationGroup);
            } catch (Throwable throwable) {

            }
        }
    }

    // 其实chs本身是线程安全的，在add和remove本来不需要枷锁，但是这里枷锁的目的是为了上面的post，如果不加锁，就会出现上面遍历chs的时候同时有
    // 可能有其他线程在add或remove数据导致出现类似npe的问题
    public synchronized boolean register(VsDestinationGroupRuleListener listener) {
        if (listener == null) {
            return false;
        }
        return listenerSet.add(listener);
    }

    public synchronized void unregister(VsDestinationGroupRuleListener listener) {
        if (listener == null) {
            return;
        }
        listenerSet.remove(listener);
    }
}

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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Router;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.VsDestinationGroup;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.destination.DestinationRule;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.destination.DestinationRuleSpec;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.destination.Subset;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.DubboMatchRequest;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.DubboRoute;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.DubboRouteDetail;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.VirtualServiceRule;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.VirtualServiceSpec;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.destination.DubboDestination;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.destination.DubboRouteDestination;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.match.StringMatch;
import org.apache.dubbo.rpc.cluster.router.mesh.util.VsDestinationGroupRuleListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;


public class MeshRuleRouter implements Router, VsDestinationGroupRuleListener {

    private int priority = -500;
    private boolean force = false;
    private URL url;

    private volatile VsDestinationGroup vsDestinationGroup;

    private Map<String, String> sourcesLabels = new HashMap<>();

    private volatile List<Invoker<?>> invokerList = new ArrayList<>();

    private volatile Map<String, List<Invoker<?>>> subsetMap;

    private String remoteAppName;

    // gx
    public MeshRuleRouter(URL url) {
        this.url = url;
        sourcesLabels.putAll(url.getParameters());
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {

        // 进去 方法很重要
        List<DubboRouteDestination> routeDestination = getDubboRouteDestination(invocation);

        if (routeDestination == null) {
            return invokers;
        } else {
            // 随机获取一个允许的目标路由
            DubboRouteDestination dubboRouteDestination = routeDestination.get(ThreadLocalRandom.current().nextInt(routeDestination.size()));

            DubboDestination dubboDestination = dubboRouteDestination.getDestination();

            String host = dubboDestination.getHost();
            String subset = dubboDestination.getSubset();

            List<Invoker<?>> result;

            // subSetMap的生成逻辑是基于两个回调方法产生的invokerList和vsDestinationGroup进行computeSet计算出来的
            Map<String, List<Invoker<?>>> subsetMapCopy = this.subsetMap;

            //TODO make intersection with invokers  这句话的意思就是整个方法其实没有考虑invokers
            if (subsetMapCopy != null) {

                do {
                    result = subsetMapCopy.get(subset);

                    if (result != null && result.size() > 0) {
                        return (List) result;
                    }

                    // 走回退逻辑
                    dubboRouteDestination = dubboDestination.getFallback();
                    // 可能会不断的触发回退逻辑，所以do-while
                    if (dubboRouteDestination == null) {
                        break;
                    }
                    dubboDestination = dubboRouteDestination.getDestination();

                    host = dubboDestination.getHost();
                    subset = dubboDestination.getSubset();
                } while (true);

                return null;
            }
        }

        return invokers;
    }

    // gx
    // notify和onRuleChange对应
    @Override
    public <T> void notify(List<Invoker<T>> invokers) {
        List invokerList = invokers == null ? Collections.emptyList() : invokers;
        // 这里是invokerList的赋值处，注意vsDestinationGroup的赋值处
        this.invokerList = invokerList;
        registerAppRule(invokerList);
        // 在 onRuleChange 触发的时候也会调用下面这个方法
        computeSubset();
    }


    private void registerAppRule(List<Invoker<?>> invokers) {
        if (StringUtils.isEmpty(remoteAppName)) {
            synchronized (this) {
                if (StringUtils.isEmpty(remoteAppName) && invokers != null && invokers.size() > 0) {
                    for (Invoker invoker : invokers) {
                        String applicationName = invoker.getUrl().getRemoteApplication();
                        if (StringUtils.isNotEmpty(applicationName) && !"unknown".equals(applicationName)) {
                            remoteAppName = applicationName;
                            // 注册一个即返回了 因为这批的invoker的app都是一个，应用级别的服务发现
                            MeshRuleManager.register(remoteAppName, this);
                            break;
                        }
                    }
                }
            }
        }
    }


    @Override
    public void onRuleChange(VsDestinationGroup vsDestinationGroup) {
        this.vsDestinationGroup = vsDestinationGroup;
        computeSubset();
    }

    @Override
    public boolean isRuntime() {
        return true;
    }

    @Override
    public boolean isForce() {
        return force;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    private List<DubboRouteDestination> getDubboRouteDestination(Invocation invocation) {

        if (vsDestinationGroup != null) {

            // 入站流量匹配
            List<VirtualServiceRule> virtualServiceRuleList = vsDestinationGroup.getVirtualServiceRuleList();
            if (virtualServiceRuleList.size() > 0) {
                for (VirtualServiceRule virtualServiceRule : virtualServiceRuleList) {
                    // 找到第一个匹配服务名称的DubboRoute
                    DubboRoute dubboRoute = getDubboRoute(virtualServiceRule, invocation);
                    if (dubboRoute != null) {
                        //下面是更细粒度的匹配，即方法名称 参数类型等 进去
                        return getDubboRouteDestination(dubboRoute, invocation);
                    }
                }
            }
        }
        return null;
    }

    // 服务名称匹配
    protected DubboRoute getDubboRoute(VirtualServiceRule virtualServiceRule, Invocation invocation) {
        String serviceName = invocation.getServiceName();

        VirtualServiceSpec spec = virtualServiceRule.getSpec();
        List<DubboRoute> dubboRouteList = spec.getDubbo();
        if (dubboRouteList.size() > 0) {
            for (DubboRoute dubboRoute : dubboRouteList) {
                List<StringMatch> stringMatchList = dubboRoute.getServices();
                if (stringMatchList == null || stringMatchList.size() == 0) {
                    return dubboRoute;
                }
                for (StringMatch stringMatch : stringMatchList) {
                    if (StringMatch.isMatch(stringMatch, serviceName)) {
                        return dubboRoute;
                    }
                }
            }
        }
        return null;
    }


    protected List<DubboRouteDestination> getDubboRouteDestination(DubboRoute dubboRoute, Invocation invocation) {

        List<DubboRouteDetail> dubboRouteDetailList = dubboRoute.getRoutedetail();
        if (dubboRouteDetailList.size() > 0) {
            DubboRouteDetail dubboRouteDetail = findMatchDubboRouteDetail(dubboRouteDetailList, invocation);
            if (dubboRouteDetail != null) {
                return dubboRouteDetail.getRoute();
            }
        }

        return null;
    }

    protected DubboRouteDetail findMatchDubboRouteDetail(List<DubboRouteDetail> dubboRouteDetailList, Invocation invocation) {

        String methodName = invocation.getMethodName();
        String[] parameterTypeList = invocation.getCompatibleParamSignatures();
        Object[] parameters = invocation.getArguments();


        for (DubboRouteDetail dubboRouteDetail : dubboRouteDetailList) {
            List<DubboMatchRequest> matchRequestList = dubboRouteDetail.getMatch();
            if (matchRequestList == null || matchRequestList.size() == 0) {
                return dubboRouteDetail;
            }

            boolean match = true;

            //FIXME to deal with headers
            for (DubboMatchRequest dubboMatchRequest : matchRequestList) {
                if (!DubboMatchRequest.isMatch(dubboMatchRequest, methodName, parameterTypeList, parameters,
                        // url的parameters
                        sourcesLabels,
                        new HashMap<>(), invocation.getAttachments(),
                        new HashMap<>())) {
                    match = false;
                    break;
                }
            }

            if (match) {
                return dubboRouteDetail;
            }
        }
        return null;
    }


    // 加了锁 保证通知的多个事件挨个执行
    protected synchronized void computeSubset() {
        // 下两个（invokerList和vsDestinationGroup）都不是null才可以进行 computeSubsetMap
        if (invokerList == null || invokerList.size() == 0) {
            this.subsetMap = null;
            return;
        }

        if (vsDestinationGroup == null) {
            this.subsetMap = null;
            return;
        }

        // 有人说compute的计算比较复杂，是否可以用缓存，不太行，因为这两个关键的变量是不断变化的
        Map<String, List<Invoker<?>>> subsetMap = computeSubsetMap(invokerList, vsDestinationGroup.getDestinationRuleList());

        if (subsetMap.size() == 0) {
            this.subsetMap = null;
        } else {
            this.subsetMap = subsetMap;
        }
    }


    // 主要就是将invoker的parameter和 DestinationRule#subSet的 lables 这两个map进行匹配，如果后者所有的entry在前者都存在则满足
    protected Map<String, List<Invoker<?>>> computeSubsetMap(List<Invoker<?>> invokers, List<DestinationRule> destinationRules) {
        Map<String, List<Invoker<?>>> subsetMap = new HashMap<>();

        for (DestinationRule destinationRule : destinationRules) {
            DestinationRuleSpec destinationRuleSpec = destinationRule.getSpec();
            String host = destinationRuleSpec.getHost();
            List<Subset> subsetList = destinationRuleSpec.getSubsets();

            for (Subset subset : subsetList) {
                String subsetName = subset.getName();
                List<Invoker<?>> subsetInvokerList = new ArrayList<>();
                subsetMap.put(subsetName, subsetInvokerList);

                Map<String, String> labels = subset.getLabels();

                for (Invoker<?> invoker : invokers) {
                    Map<String, String> parameters = invoker.getUrl().getParameters();
                    if (containMapKeyValue(parameters, labels)) {
                        subsetInvokerList.add(invoker);
                    }
                }
            }
        }

        return subsetMap;
    }


    protected boolean containMapKeyValue(Map<String, String> originMap, Map<String, String> inputMap) {
        if (inputMap == null || inputMap.size() == 0) {
            return true;
        }

        for (Map.Entry<String, String> entry : inputMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            String originMapValue = originMap.get(key);
            if (!value.equals(originMapValue)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void stop() {
        MeshRuleManager.unregister(this);
    }

    /**
     * just for test
     * @param vsDestinationGroup
     */
    protected void setVsDestinationGroup(VsDestinationGroup vsDestinationGroup) {
        this.vsDestinationGroup = vsDestinationGroup;
    }

    /**
     * just for test
     * @param sourcesLabels
     */
    protected void setSourcesLabels(Map<String, String> sourcesLabels) {
        this.sourcesLabels = sourcesLabels;
    }

    /**
     * just for test
     * @param invokerList
     */
    protected void setInvokerList(List<Invoker<?>> invokerList) {
        this.invokerList = invokerList;
    }

    /**
     * just for test
     * @param subsetMap
     */
    protected void setSubsetMap(Map<String, List<Invoker<?>>> subsetMap) {
        this.subsetMap = subsetMap;
    }


    public VsDestinationGroup getVsDestinationGroup() {
        return vsDestinationGroup;
    }

    public Map<String, String> getSourcesLabels() {
        return sourcesLabels;
    }

    public List<Invoker<?>> getInvokerList() {
        return invokerList;
    }

    public Map<String, List<Invoker<?>>> getSubsetMap() {
        return subsetMap;
    }
}

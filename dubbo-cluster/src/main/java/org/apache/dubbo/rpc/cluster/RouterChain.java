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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Router chain
 */
// OK
public class RouterChain<T> {

    // full list of addresses from registry, classified by method name.
    private List<Invoker<T>> invokers = Collections.emptyList();

    // containing all routers, reconstruct every time 'route://' urls change.
    private volatile List<Router> routers = Collections.emptyList();

    // Fixed router instances: ConfigConditionRouter, TagRouter, e.g., the rule for each instance may change but the
    // instance will never delete or recreate.
    // 固定的路由器实例:ConfigConditionRouter, TagRouter，例如，每个实例的规则可能会改变，但是实例永远不会被删除或重新创建。
    private List<Router> builtinRouters = Collections.emptyList();

    public static <T> RouterChain<T> buildChain(URL url) {
        return new RouterChain<>(url);
    }

    private RouterChain(URL url) {
        // dubbo使用RouterChain找到所有的RouterFactory对象，然后由RouterFactory创建出Router对象，Router对象的集合作为RouterChain的属性，之后访问路由规则都是通过RouterChain完成的
        // RouterFactory实现类有注解@Activate，因此RouterFactory实现类是通过调用getActivateExtension完成加载。默认情况下，凡是有@Activate的类，dubbo都会加载
        List<RouterFactory> extensionFactories = ExtensionLoader.getExtensionLoader(RouterFactory.class)
                .getActivateExtension(url, "router");
        //0 = {MockRouterFactory@3840}
        //1 = {TagRouterFactory@3841}
        //2 = {AppRouterFactory@3842}
        //3 = {ServiceRouterFactory@3843}

        // 使用RouterFactory创建出Router对象
        List<Router> routers = extensionFactories.stream()
                .map(factory -> factory.getRouter(url))
                .collect(Collectors.toList());

        initWithRouters(routers);
        // 上面方法对routes排序后如下
//        0 = {MockInvokersSelector@3730}
//        1 = {TagRouter@3731}
//        2 = {ServiceRouter@3732}
//        3 = {AppRouter@3733}
    }

    /**
     * the resident routers must being initialized before address notification. 常驻路由器必须在地址通知之前被初始化。
     * FIXME: this method should not be public
     */
    // 每次客户端启动的时候，都会使用buildChain方法构建RouterChain对象。RouterChain使用routers保存所有的Route对象集合，
    // 这里注意一点，builtinRouters在客户端启动完毕后不会再发生改变，它记录了客户端设置的Route对象集合；而routers属性是会
    // 发生改变的，每当新增一个远程服务提供者时，客户端会收到注册中心的通知，之后解析服务端的参数，如果服务端配置了路有规则，
    // 那么routers属性值便是服务端设置的路有规则对象与builtinRouters的并集。
    public void initWithRouters(List<Router> builtinRouters) {
        this.builtinRouters = builtinRouters;
        this.routers = new ArrayList<>(builtinRouters);
        this.sort();// 每个Route对象都实现了Comparable接口，排序按照字段priority的值升序排列
    }

    /**
     * If we use route:// protocol in version before 2.7.0, each URL will generate a Router instance, so we should
     * keep the routers up to date, that is, each time router URLs changes, we should update the routers list, only
     * keep the builtinRouters which are available all the time and the latest notified routers which are generated
     * from URLs.
     *
     * 如果我们在版本2.7.0之前使用 route:// 协议,每个URL将生成一个路由实例,所以我们应该保持routers最新,也就是说,每一次路由器URL更改,我们应该更新
     * 路由器列表,只保留可用的builtinRouters,以及由URLs生成的最新已通知的routers。
     *
     * @param routers routers from 'router://' rules in 2.6.x or before.
     */
    public void addRouters(List<Router> routers) {
        List<Router> newRouters = new ArrayList<>();
        newRouters.addAll(builtinRouters);
        newRouters.addAll(routers);
        CollectionUtils.sort(newRouters);
        this.routers = newRouters;
    }

    private void sort() {
        Collections.sort(routers);
    }

    /**
     *
     * @param url
     * @param invocation
     * @return
     */
    public List<Invoker<T>> route(URL url, Invocation invocation) {
        List<Invoker<T>> finalInvokers = invokers;
        for (Router router : routers) {
            //调用了路由规则筛选出合适的远程服务提供者集合 ，挨个过滤的，比如第一个router 筛出 3个 出来，第二个router再次筛选，只剩1个满足
            finalInvokers = router.route(finalInvokers, url, invocation);
        }
        return finalInvokers;
    }

    /**
     * Notify router chain of the initial addresses from registry at the first time.
     * Notify whenever addresses in registry change.
     *
     * *第一时间从注册表中通知路由器链的初始地址。
     * *在注册表中地址发生变化时通知。
     */
    public void setInvokers(List<Invoker<T>> invokers) {
        this.invokers = (invokers == null ? Collections.emptyList() : invokers);
        routers.forEach(router -> router.notify(this.invokers));
    }
}

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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round robin load balance.
 */
// 本节，我们来看一下 Dubbo 中加权轮询负载均衡的实现 RoundRobinLoadBalance。在详细分析源码前，我们先来了解一下什么是加权轮询。
// 这里从最简单的轮询开始讲起，所谓轮询是指将请求轮流分配给每台服务器。举个例子，我们有三台服务器 A、B、C。我们将第一个请求分配给服务器 A，
// 第二个请求分配给服务器 B，第三个请求分配给服务器 C，第四个请求再次分配给服务器 A。这个过程就叫做轮询。轮询是一种无状态负载均衡算法，实现简单，
// 适用于每台服务器性能相近的场景下。但现实情况下，我们并不能保证每台服务器性能均相近。如果我们将等量的请求分配给性能较差的服务器，这显然是不合理的。
// 因此，这个时候我们需要对轮询过程进行加权，以调控每台服务器的负载。经过加权后，每台服务器能够得到的请求数比例，接近或等于他们的权重比。
// 比如服务器 A、B、C 权重比为 5:2:1。那么在8次请求中，服务器 A 将收到其中的5次请求，服务器 B 会收到其中的2次请求，服务器 C 则收到其中的1次请求。
//
// 以上就是加权轮询的算法思想，搞懂了这个思想，接下来我们就可以分析源码了。我们先来看一下 2.6.4 版本的 RoundRobinLoadBalance。
public class RoundRobinLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "roundrobin";

    private static final int RECYCLE_PERIOD = 60000;

    protected static class WeightedRoundRobin {
        private int weight;
        private AtomicLong current = new AtomicLong(0);
        private long lastUpdate;

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
            current.set(0);
        }

        public long increaseCurrent() {
            return current.addAndGet(weight);
        }

        public void sel(int total) {
            current.addAndGet(-1 * total);
        }

        public long getLastUpdate() {
            return lastUpdate;
        }

        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }

    private ConcurrentMap<String, ConcurrentMap<String, WeightedRoundRobin>> methodWeightMap = new ConcurrentHashMap<String, ConcurrentMap<String, WeightedRoundRobin>>();

    /**
     * get invoker addr list cached for specified invocation
     * <p>
     * <b>for unit test only</b>
     *
     * @param invokers
     * @param invocation
     * @return
     */
    protected <T> Collection<String> getInvokerAddrList(List<Invoker<T>> invokers, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        Map<String, WeightedRoundRobin> map = methodWeightMap.get(key);
        if (map != null) {
            return map.keySet();
        }
        return null;
    }

    // 以[5,2,1]举例
    // [-3,2,1]
    // [2,2,1]
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // key = 全限定类名 + "." + 方法名，比如 com.xxx.DemoService.sayHello // 获取轮询key  服务名+方法名
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        // 获取 url 到 WeightedRoundRobin 映射表，如果为空，则创建一个新的
        ConcurrentMap<String, WeightedRoundRobin> map = methodWeightMap.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        int totalWeight = 0; // 权重总和
        long maxCurrent = Long.MIN_VALUE;
        long now = System.currentTimeMillis();
        Invoker<T> selectedInvoker = null;
        WeightedRoundRobin selectedWRR = null;
        // 下面这个循环主要用于查找最大和最小权重，计算权重总和等
        for (Invoker<T> invoker : invokers) {
            String identifyString = invoker.getUrl().toIdentityString();// eg test1://127.0.0.1:11/DemoService
            int weight = getWeight(invoker, invocation);//获取权重值
            WeightedRoundRobin weightedRoundRobin = map.computeIfAbsent(identifyString, k -> {
                WeightedRoundRobin wrr = new WeightedRoundRobin();
                wrr.setWeight(weight);
                return wrr;
            });

            if (weight != weightedRoundRobin.getWeight()) {
                //weight changed
                weightedRoundRobin.setWeight(weight);
            }
            long cur = weightedRoundRobin.increaseCurrent();
            weightedRoundRobin.setLastUpdate(now);
            if (cur > maxCurrent) {
                maxCurrent = cur;
                selectedInvoker = invoker;
                selectedWRR = weightedRoundRobin;
            }
            totalWeight += weight;
        }
        if (invokers.size() != map.size()) {
            // 移除最老的 没被参与到轮询选举的invoker。
            // 存在 methodWeightMap 里的 （与invoker对应的）weightedRoundRobin 如果能从map缓存中取出，上面循环都会更新此invoker对应的
            // weightedRoundRobin的时间戳，如果长时间没有更新，说明这个他很久没被调用了（从methodWeightMap缓存很久没被取出），所以可以移除他
            map.entrySet().removeIf(item -> now - item.getValue().getLastUpdate() > RECYCLE_PERIOD);
        }
        if (selectedInvoker != null) {
            // sel减去总和 这是保证轮询顺序的的核心。前面for循环内部的if (cur > maxCurrent) 是考虑权重的核心，合起来就是 加权 + 轮询
            // 可以打断点调试看 methodWeightMap 的各个权重值，就理解这个机制了。我截取了部分数据放到了最后（断点停在  selectedWRR.sel(totalWeight); 还没有sel）
            selectedWRR.sel(totalWeight);
            return selectedInvoker;
        }
        // should not happen here
        return invokers.get(0);
    }

}
//key = "DemoService.test"
//map = {ConcurrentHashMap@2974}  size = 4
// "test4://127.0.0.1:9999/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@2978}
//  key = "test4://127.0.0.1:9999/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@2978}
//   weight = 11
//   current = {AtomicLong@3008} "-16"   ------ > 这里该invoker是最大权重 被选择出来，然后 11 - 27  = -16
//   lastUpdate = 1616859832448
// "test1://127.0.0.1:11/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3002}
//  key = "test1://127.0.0.1:11/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3002}
//   weight = 1
//   current = {AtomicLong@3010} "1"
//   lastUpdate = 1616859832448
// "test2://127.0.0.1:12/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3004}
//  key = "test2://127.0.0.1:12/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3004}
//   weight = 9
//   current = {AtomicLong@3012} "9"
//   lastUpdate = 1616859832448
// "test3://127.0.0.1:13/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3006}
//  key = "test3://127.0.0.1:13/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3006}
//   weight = 6
//   current = {AtomicLong@3014} "6"
//   lastUpdate = 1616859832448
//
//
//
//
//
//key = "DemoService.test"
//map = {ConcurrentHashMap@2974}  size = 4
// "test1://127.0.0.1:11/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3002}
// "test4://127.0.0.1:9999/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@2978}
// "test2://127.0.0.1:12/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3004}
// "test3://127.0.0.1:13/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3006}
//  key = "test1://127.0.0.1:11/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3002}
//  key = "test4://127.0.0.1:9999/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@2978}
//  key = "test2://127.0.0.1:12/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3004}
//  key = "test3://127.0.0.1:13/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3006}
//   weight = 1
//   current = {AtomicLong@3010} "2"   ------ > 1+1 = 2
//   lastUpdate = 1616859861607
//   weight = 11
//   current = {AtomicLong@3008} "-5"  ------ > -16+11 = -5
//   lastUpdate = 1616859861607
//   weight = 9
//   current = {AtomicLong@3012} "-9"  ------ > 9+9 - 27 = -9 最大权重 被选出 ，正好排在 前面的11 后面（11 9 6 1）
//   lastUpdate = 1616859861607
//   weight = 6
//   current = {AtomicLong@3014} "12" ------ > 6+ 6 =12
//   lastUpdate = 1616859861607
//
//
//
//key = "DemoService.test"
//map = {ConcurrentHashMap@2974}  size = 4
// "test4://127.0.0.1:9999/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@2978}
//  key = "test4://127.0.0.1:9999/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@2978}
//   weight = 11
//   current = {AtomicLong@3008} "6"  ------ > -5+11 = 6
//   lastUpdate = 1616859866217
// "test1://127.0.0.1:11/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3002}
//  key = "test1://127.0.0.1:11/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3002}
//   weight = 1
//   current = {AtomicLong@3010} "3"  ------ > 2+1 =3
//   lastUpdate = 1616859866217
// "test2://127.0.0.1:12/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3004}
//  key = "test2://127.0.0.1:12/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3004}
//   weight = 9
//   current = {AtomicLong@3012} "0"   ------ > -9+9 = 0
//   lastUpdate = 1616859866217
// "test3://127.0.0.1:13/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3006}
//  key = "test3://127.0.0.1:13/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3006}
//   weight = 6
//   current = {AtomicLong@3014} "-9" ------ > 12 + 6 - 27 = -9  最大权重 被选出 ，正好排在 前面的9 后面（11 9 6 1）
//   lastUpdate = 1616859866217
//
//
//
//
//
//key = "DemoService.test"
//map = {ConcurrentHashMap@2974}  size = 4
// "test4://127.0.0.1:9999/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@2978}
//  key = "test4://127.0.0.1:9999/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@2978}
//   weight = 11
//   current = {AtomicLong@3008} "-10"
//   lastUpdate = 1616859873769
// "test1://127.0.0.1:11/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3002}
//  key = "test1://127.0.0.1:11/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3002}
//   weight = 1
//   current = {AtomicLong@3010} "4"
//   lastUpdate = 1616859873769
// "test2://127.0.0.1:12/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3004}
//  key = "test2://127.0.0.1:12/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3004}
//   weight = 9
//   current = {AtomicLong@3012} "9"
//   lastUpdate = 1616859873769
// "test3://127.0.0.1:13/DemoService" -> {RoundRobinLoadBalance$WeightedRoundRobin@3006}
//  key = "test3://127.0.0.1:13/DemoService"
//  value = {RoundRobinLoadBalance$WeightedRoundRobin@3006}
//   weight = 6
//   current = {AtomicLong@3014} "-3"
//   lastUpdate = 1616859873769

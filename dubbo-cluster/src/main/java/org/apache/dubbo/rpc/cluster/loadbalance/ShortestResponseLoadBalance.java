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
import org.apache.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ShortestResponseLoadBalance
 * </p>
 * Filter the number of invokers with the shortest response time of success calls and count the weights and quantities of these invokers.
 * If there is only one invoker, use the invoker directly;
 * if there are multiple invokers and the weights are not the same, then random according to the total weight;
 * if there are multiple invokers and the same weight, then randomly called.
 */
//从多个服务提供者中选择出调用成功的且响应时间最短的服务提供者，由于满足这样条件的服务提供者有可能有多个。所以当选择出多个服务提供者后要根据他们的权重做分析。
//但是如果只选择出来了一个，直接用选出来这个。
//如果真的有多个，看它们的权重是否一样，如果不一样，则走加权随机算法的逻辑。
//如果它们的权重是一样的，则随机调用一个。

// OK 最短响应时间负载均衡
public class ShortestResponseLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "shortestresponse";

    // 方法整体逻辑和 LeastActiveLoadBalance 负载均衡策略 基本一致，只是一个是判断谁的响应时间短，一个是判断谁的活跃数低。
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        int length = invokers.size();
        // Estimated shortest response time of all invokers 所有服务提供者的估计最短响应时间。（这个地方我觉得注释描述的不太准确，看后面的代码可以知道这只是一个零时变量，在循环中存储当前最短响应时间是多少。）
        long shortestResponse = Long.MAX_VALUE;
        // The number of invokers having the same estimated shortest response time 具有相同最短响应时间的服务提供者个数，初始化为 0。
        int shortestCount = 0;
        // The index of invokers having the same estimated shortest response time 数组里面放的是具有相同最短响应时间的服务提供者的下标。
        int[] shortestIndexes = new int[length];
        // the weight of every invokers 每一个服务提供者的权重。
        int[] weights = new int[length];
        // The sum of the warmup weights of all the shortest response  invokers 多个具有相同最短响应时间的服务提供者对应的预热（预热这个点还是挺重要的，在下面讲最小活跃数负载均衡的时候有详细说明）权重之和。
        int totalWeight = 0;
        // The weight of the first shortest response invokers 第一个具有最短响应时间的服务提供者的权重。
        int firstWeight = 0;
        // Every shortest response invoker has the same weight value? 多个满足条件的提供者的权重是否一致。
        boolean sameWeight = true;

        // Filter out all the shortest response invokers
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            RpcStatus rpcStatus = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName());
            // Calculate the estimated response time from the product of active connections and succeeded average elapsed time.
            long succeededAverageElapsed = rpcStatus.getSucceededAverageElapsed(); // 获取调用成功的平均时间。
            int active = rpcStatus.getActive();// 获取的是该服务提供者的活跃数，也就是堆积的请求数。
            long estimateResponse = succeededAverageElapsed * active; // 获取的就是如果当前这个请求发给这个服务提供者预计需要等待的时间。乘以 active 的原因是因为它需要排在堆积的请求的后面嘛。

            // 后面的代码怎么写？当然是出来一个更短的就把这个踢出去呀，或者出来一个一样长时间的就记录一下，接着去 pk 权重了。

            int afterWarmup = getWeight(invoker, invocation);
            weights[i] = afterWarmup;
            // Same as LeastActiveLoadBalance      如果出现有更短的响应时间的服务提供者首先记录更短的响应时间然后记录当前服务提供者的下标
            if (estimateResponse < shortestResponse) {
                shortestResponse = estimateResponse;
                shortestCount = 1;
                shortestIndexes[0] = i;
                totalWeight = afterWarmup;
                firstWeight = afterWarmup;
                sameWeight = true;
            } else if (estimateResponse == shortestResponse) {// 如果出现时间一样长的服务提供者首先更新shortest Count参数， 记录下标然后计算总权重接着判断权重是否相等
                shortestIndexes[shortestCount++] = i;
                totalWeight += afterWarmup;
                if (sameWeight && i > 0
                        && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        if (shortestCount == 1) {// 里面参数的含义我们都知道了，所以，标号为③的地方的含义就很好解释了：经过选择后只有一个服务提供者满足条件。所以，直接使用这个服务提供者。
            return invokers.get(shortestIndexes[0]);
        }
        if (!sameWeight && totalWeight > 0) {// 这个地方我就不展开讲了（后面的加权随机负载均衡那一小节有详细说明），熟悉的朋友一眼就看出来这是加权随机负载均衡的写法了。
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            for (int i = 0; i < shortestCount; i++) {
                int shortestIndex = shortestIndexes[i];
                offsetWeight -= weights[shortestIndex];
                if (offsetWeight < 0) {
                    return invokers.get(shortestIndex);
                }
            }
        }
        return invokers.get(shortestIndexes[ThreadLocalRandom.current().nextInt(shortestCount)]);
    }
}

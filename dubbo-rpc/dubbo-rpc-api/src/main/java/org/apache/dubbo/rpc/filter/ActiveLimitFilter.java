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
package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcStatus;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.rpc.Constants.ACTIVES_KEY;

/**
 * ActiveLimitFilter restrict the concurrent client invocation for a service or service's method from client side.
 * To use active limit filter, configured url with <b>actives</b> and provide valid >0 integer value.
 * <pre>
 *     e.g. <dubbo:reference id="demoService" check="false" interface="org.apache.dubbo.demo.DemoService" "actives"="2"/>
 *      In the above example maximum 2 concurrent invocation is allowed.
 *      If there are more than configured (in this example 2) is trying to invoke remote method, then rest of invocation
 *      will wait for configured timeout(default is 0 second) before invocation gets kill by dubbo.
 * </pre>
 *
 * ActiveLimitFilter限制客户端对服务或服务方法的并发客户端调用。
 * 要使用活动限制过滤器，配置url <b>活动</b>，并提供有效的>0整数值。
 * 例如:<dubbo:reference id="demoService" check="false" interface="org.apache.dubbo.demo.DemoService" "actives"="2"/>
 * 在上面的示例中，最多允许2个并发调用。
 * 如果有超过配置的(在本例2中)正在尝试调用远程方法，则调用其余的方法
 * 将等待配置的超时(默认为0秒)，然后调用被dubbo终止。
 *
 * @see Filter
 */
// OK
@Activate(group = CONSUMER, value = ACTIVES_KEY)
public class ActiveLimitFilter implements Filter, Filter.Listener {

    private static final String ACTIVELIMIT_FILTER_START_TIME = "activelimit_filter_start_time";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        URL url = invoker.getUrl();
        String methodName = invocation.getMethodName();
        // 进去
        int max = invoker.getUrl().getMethodParameter(methodName, ACTIVES_KEY, 0);
        // 进去
        final RpcStatus rpcStatus = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName());
        // 进去，核心！！判断调用次数是否到达最大
        if (!RpcStatus.beginCount(url, methodName, max)) {
            // 进去，和前面 ACTIVES_KEY 的方式一样
            long timeout = invoker.getUrl().getMethodParameter(invocation.getMethodName(), TIMEOUT_KEY, 0);
            long start = System.currentTimeMillis();
            long remain = timeout;
            // 常见的sync+wait（多线程会在这里竞争，谁能走出sync块-关键是下面的while块，就能发起后面的invoke调用）
            synchronized (rpcStatus) {
                // 进去再次判断（类似双重检查，第一重在上面的if）
                while (!RpcStatus.beginCount(url, methodName, max)) {
                    try {
                        // 不能调用的话，进行等待直到超时
                        rpcStatus.wait(remain);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    remain = timeout - elapsed;
                    // 如果超时了，抛异常，否则回到while继续尝试（目的就是能走出while循环块，运行后面 invoke发起调用）
                    if (remain <= 0) {
                        throw new RpcException(RpcException.LIMIT_EXCEEDED_EXCEPTION,
                                "Waiting concurrent invoke timeout in client-side for service:  " +
                                        invoker.getInterface().getName() + ", method: " + invocation.getMethodName() +
                                        ", elapsed: " + elapsed + ", timeout: " + timeout + ". concurrent invokes: " +
                                        rpcStatus.getActive() + ". max concurrent invoke limit: " + max);
                    }
                }
            }
        }

        // 记录正式开始调用的时间
        invocation.put(ACTIVELIMIT_FILTER_START_TIME, System.currentTimeMillis());

        return invoker.invoke(invocation);
    }

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        String methodName = invocation.getMethodName();
        URL url = invoker.getUrl();
        int max = invoker.getUrl().getMethodParameter(methodName, ACTIVES_KEY, 0);
        // getElapsed、endCount 进去（endCount和beginCount对应）
        RpcStatus.endCount(url, methodName, getElapsed(invocation), true);
        // 进去
        notifyFinish(RpcStatus.getStatus(url, methodName), max);
    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {
        String methodName = invocation.getMethodName();
        URL url = invoker.getUrl();
        int max = invoker.getUrl().getMethodParameter(methodName, ACTIVES_KEY, 0);

        // 除了这段，其他和onResponse基本一致
        if (t instanceof RpcException) {
            RpcException rpcException = (RpcException) t;
            if (rpcException.isLimitExceed()) {
                return;
            }
        }
        RpcStatus.endCount(url, methodName, getElapsed(invocation), false);
        notifyFinish(RpcStatus.getStatus(url, methodName), max);
    }

    private long getElapsed(Invocation invocation) {
        Object beginTime = invocation.get(ACTIVELIMIT_FILTER_START_TIME);
        // 计算调用花费多久
        return beginTime != null ? System.currentTimeMillis() - (Long) beginTime : 0;
    }

    private void notifyFinish(final RpcStatus rpcStatus, int max) {
        if (max > 0) {
            synchronized (rpcStatus) {
                // 唤醒其他线程在上面等待的（endCount内部将active -- 了，这样其他线程调用beginCount内部++就不会超过max 了）
                rpcStatus.notifyAll();
            }
        }
    }
}

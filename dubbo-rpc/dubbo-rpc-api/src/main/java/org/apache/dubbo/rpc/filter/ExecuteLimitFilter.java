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
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcStatus;

import static org.apache.dubbo.rpc.Constants.EXECUTES_KEY;


/**
 * The maximum parallel execution request count per method per service for the provider.If the max configured
 * <b>executes</b> is set to 10 and if invoke request where it is already 10 then it will throws exception. It
 * continue the same behaviour un till it is <10.
 *
 * 提供程序的每个服务的每个方法的最大并行执行请求数。如果配置了max
 * executes被设置为10，并且如果调用请求时它已经是10，那么它将抛出异常。它
 * 继续相同的行为，直到它<10。
 */

// OK 大体可以参考ActiveLimitFilter，不过ExecuteLimitFilter不考虑超时
@Activate(group = CommonConstants.PROVIDER, value = EXECUTES_KEY)
public class ExecuteLimitFilter implements Filter, Filter.Listener {

    private static final String EXECUTE_LIMIT_FILTER_START_TIME = "execute_limit_filter_start_time";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        URL url = invoker.getUrl();
        String methodName = invocation.getMethodName();
        // 从url中获取该方法限制的数量（没有的话，默认为max=0，在下面的beginCount会被识别最大调用次数为Interger.MAX_VALUE）
        int max = url.getMethodParameter(methodName, EXECUTES_KEY, 0);
        // 是否允许调用，进去
        if (!RpcStatus.beginCount(url, methodName, max)) {
            throw new RpcException(RpcException.LIMIT_EXCEEDED_EXCEPTION,
                    "Failed to invoke method " + invocation.getMethodName() + " in provider " +
                            url + ", cause: The service using threads greater than <dubbo:service executes=\"" + max +
                            "\" /> limited.");
        }

        invocation.put(EXECUTE_LIMIT_FILTER_START_TIME, System.currentTimeMillis());
        try {
            return invoker.invoke(invocation);
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new RpcException("unexpected exception when ExecuteLimitFilter", t);
            }
        }
    }

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        // 进去，最后成功标记为true
        RpcStatus.endCount(invoker.getUrl(), invocation.getMethodName(), getElapsed(invocation), true);
    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {
        if (t instanceof RpcException) {
            RpcException rpcException = (RpcException) t;
            if (rpcException.isLimitExceed()) {
                return;
            }
        }
        // 进去，最后成功标记为false
        RpcStatus.endCount(invoker.getUrl(), invocation.getMethodName(), getElapsed(invocation), false);
    }

    private long getElapsed(Invocation invocation) {
        Object beginTime = invocation.get(EXECUTE_LIMIT_FILTER_START_TIME);
        return beginTime != null ? System.currentTimeMillis() - (Long) beginTime : 0;
    }
}

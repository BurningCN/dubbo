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

package org.apache.dubbo.common.threadpool.support.limited;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadlocal.NamedInternalThreadFactory;
import org.apache.dubbo.common.threadpool.ThreadPool;
import org.apache.dubbo.common.threadpool.support.AbortPolicyWithReport;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.CORE_THREADS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_CORE_THREADS;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_QUEUES;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_THREADS;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_THREAD_NAME;
import static org.apache.dubbo.common.constants.CommonConstants.QUEUES_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.THREADS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.THREAD_NAME_KEY;

/**
 * Creates a thread pool that creates new threads as needed until limits reaches. This thread pool will not shrink
 * automatically.
 * 创建一个线程池，根据需要创建新的线程，直到限制到达。此线程池不会自动收缩。
 */
// OK
public class LimitedThreadPool implements ThreadPool {

    @Override
    public Executor getExecutor(URL url) {
        String name = url.getParameter(THREAD_NAME_KEY, DEFAULT_THREAD_NAME);
        int cores = url.getParameter(CORE_THREADS_KEY, DEFAULT_CORE_THREADS);
        int threads = url.getParameter(THREADS_KEY, DEFAULT_THREADS);
        int queues = url.getParameter(QUEUES_KEY, DEFAULT_QUEUES);
        // 创建线程池
        return new ThreadPoolExecutor(
                cores,// 核心线程数
                threads,// 最大线程数
                Long.MAX_VALUE, // keepAlive时间-->直接最大值，一直工作，所以就是类上面注释提到的不会自动收缩
                TimeUnit.MILLISECONDS,// keepAlive单位
                queues == 0 ? new SynchronousQueue<Runnable>() : // 任务队列
                        (queues < 0 ? new LinkedBlockingQueue<Runnable>()
                                : new LinkedBlockingQueue<Runnable>(queues)),
                // 线程工厂，name作为线程名称的前缀，注意使用的是Named[Internal]ThreadFactory，new 的是 InternalThread 线程，
                // 目的是要用改造版的 ThreadLocal （即InternalThreadLocal），必须要配合 InternalThread 线程使用，否则就会退化为原生的 ThreadLocal
                // InternalThreadLocal可以后面去了解。NamedInternalThreadFactory进去
                new NamedInternalThreadFactory(name, true),
                new AbortPolicyWithReport(name, url));// 拒绝策略，使用基于AbortPolicy自定义的一个拒绝策略，进去

    }

}

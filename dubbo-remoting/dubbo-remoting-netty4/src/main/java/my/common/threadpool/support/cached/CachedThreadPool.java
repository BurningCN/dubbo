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
package my.common.threadpool.support.cached;

import my.common.threadpool.ThreadPool;
import my.common.threadpool.support.AbortPolicyWithReport;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadlocal.NamedInternalThreadFactory;

import java.util.concurrent.*;

import static org.apache.dubbo.common.constants.CommonConstants.*;

/**
 * This thread pool is self-tuned. Thread will be recycled after idle for one minute, and new thread will be created for
 * the upcoming request.
 * 这个线程池是自调优的。线程在空闲一分钟后将被回收，新的线程将为即将到来的请求创建。
 *
 * @see java.util.concurrent.Executors#newCachedThreadPool()---这个注释就表明当前类其实就是模仿了原生的newCachedThreadPool
 */
// OK
// 少部分注释，更多解释看LimitedThreadPool（页推荐优先看Limited再回来）
public class CachedThreadPool implements ThreadPool {

    @Override
    public Executor getExecutor(URL url) {
        String name = url.getParameter(THREAD_NAME_KEY, DEFAULT_THREAD_NAME);
        int cores = url.getParameter(CORE_THREADS_KEY, DEFAULT_CORE_THREADS);
        int threads = url.getParameter(THREADS_KEY, Integer.MAX_VALUE); // 最大个数为Integer最大值
        int queues = url.getParameter(QUEUES_KEY, DEFAULT_QUEUES); // 默认数量为0
        int alive = url.getParameter(ALIVE_KEY, DEFAULT_ALIVE);// 空闲一分钟自动删除，需要时重建。
        return new ThreadPoolExecutor(cores, threads, alive, TimeUnit.MILLISECONDS,
                queues == 0 ? new SynchronousQueue<Runnable>() :
                        (queues < 0 ? new LinkedBlockingQueue<Runnable>()
                                : new LinkedBlockingQueue<Runnable>(queues)),
                new NamedInternalThreadFactory(name, true), new AbortPolicyWithReport(name, url));
    }
}

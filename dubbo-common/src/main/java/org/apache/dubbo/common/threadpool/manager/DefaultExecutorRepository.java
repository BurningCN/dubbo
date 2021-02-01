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
package org.apache.dubbo.common.threadpool.manager;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.ThreadPool;
import org.apache.dubbo.common.utils.NamedThreadFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.EXECUTOR_SERVICE_COMPONENT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.THREADS_KEY;

/**
 * Consider implementing {@code Licycle} to enable executors shutdown when the process stops.
 */
// OK
// 这个类的设计主要是搞一些适用不同场景下的线程池，外界通过这个类拿去需要的线程池执行自己想要提交的任务即可
public class DefaultExecutorRepository implements ExecutorRepository {
    private static final Logger logger = LoggerFactory.getLogger(DefaultExecutorRepository.class);

    private int DEFAULT_SCHEDULER_SIZE = Runtime.getRuntime().availableProcessors();

    private final ExecutorService SHARED_EXECUTOR = Executors.newCachedThreadPool(new NamedThreadFactory("DubboSharedHandler", true));

    private Ring<ScheduledExecutorService> scheduledExecutors = new Ring<>();

    private ScheduledExecutorService serviceExporterExecutor;

    private ScheduledExecutorService reconnectScheduledExecutor;

    private ConcurrentMap<String, ConcurrentMap<Integer, ExecutorService>> data = new ConcurrentHashMap<>();

    public DefaultExecutorRepository() {
        // 创建DEFAULT_SCHEDULER_SIZE个定时调度线程池ScheduledExecutorService
        for (int i = 0; i < DEFAULT_SCHEDULER_SIZE; i++) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Dubbo-framework-scheduler"));
            // 进去
            scheduledExecutors.addItem(scheduler);
        }
        // reconnectScheduledExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Dubbo-reconnect-scheduler"));
        // 这个是暴露服务的线程，看下getXXX
        serviceExporterExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("Dubbo-exporter-scheduler"));
    }

    /**
     * Get called when the server or client instance initiating.
     *
     * @param url
     * @return
     */
    // 备注已经说明在服务提供者或者服务消费者初始化的时候会调用，通过debug 可以得出：服务提供者初始化会创建线程名为
    // DubboServerHandler-10.12.16.67:20880-thread 的线程池，服务消费者会创建线程名为 DubboClientHandler-10.12.16.67:20880-thread 的线程池。

    // 这里需要说明下，Dubbo 创建的线程池会存储在 Map 中共享使用（就是data属性）
    // private ConcurrentMap<String, ConcurrentMap<Integer, ExecutorService>> data = new ConcurrentHashMap<>();
    // 外面的 key 表示服务提供方还是消费方，里面的 key 表示服务暴露的端口号，也就是说消费方对于相同端口号的服务只会创建一个线程池，
    // 共享同一个线程池进行服务请求和消息接收后一系列处理。
    public synchronized ExecutorService createExecutorIfAbsent(URL url) {
        String componentKey = EXECUTOR_SERVICE_COMPONENT_KEY; // java.util.concurrent.ExecutorService
        if (CONSUMER_SIDE.equalsIgnoreCase(url.getParameter(SIDE_KEY))) {
            componentKey = CONSUMER_SIDE;
        }
        //    1. put与compute：
        //           不论key是否存在，强制用value覆盖进去。
        //           区别：put返回旧value或null，compute返回新的value
        //    2. putIfAbsent与computeIfAbsent：
        //           key存在，则不操作，两个都返回当前key的val;
        //           key不存在，则赋值一对新的（key，value）putIfAbsent返回null，computeIfAbsent返回新的value，且注意computeXX第二个参数接受的是Function
        Map<Integer, ExecutorService> executors = data.computeIfAbsent(componentKey, k -> new ConcurrentHashMap<>());
        Integer portKey = url.getPort();
        // createExecutor进去
        ExecutorService executor = executors.computeIfAbsent(portKey, k -> createExecutor(url));
        // If executor has been shut down, create a new one
        if (executor.isShutdown() || executor.isTerminated()) {
            executors.remove(portKey);
            executor = createExecutor(url);
            executors.put(portKey, executor);
        }
        return executor;
    }

    public ExecutorService getExecutor(URL url) {
        String componentKey = EXECUTOR_SERVICE_COMPONENT_KEY;
        if (CONSUMER_SIDE.equalsIgnoreCase(url.getParameter(SIDE_KEY))) {
            componentKey = CONSUMER_SIDE;
        }
        Map<Integer, ExecutorService> executors = data.get(componentKey);

        /**
         * It's guaranteed that this method is called after {@link #createExecutorIfAbsent(URL)}, so data should already
         * have Executor instances generated and stored.
         * 可以保证这个方法在{@link #createExecutorIfAbsent(URL)}之后被调用，所以data应该已经被调用了
         * 生成并存储执行程序实例。
         */
        if (executors == null) {
            logger.warn("No available executors, this is not expected, framework should call createExecutorIfAbsent first " +
                    "before coming to here.");
            return null;
        }

        Integer portKey = url.getPort();
        ExecutorService executor = executors.get(portKey);
        if (executor != null) {
            if (executor.isShutdown() || executor.isTerminated()) {
                executors.remove(portKey);
                // 进去
                executor = createExecutor(url);
                executors.put(portKey, executor);
            }
        }
        return executor;
    }

    @Override
    public void updateThreadpool(URL url, ExecutorService executor) {
        try {
            if (url.hasParameter(THREADS_KEY)
                    && executor instanceof ThreadPoolExecutor && !executor.isShutdown()) {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                int threads = url.getParameter(THREADS_KEY, 0);

                // 获取和设置线程池的core和max
                int max = threadPoolExecutor.getMaximumPoolSize();
                int core = threadPoolExecutor.getCorePoolSize();
                if (threads > 0 && (threads != max || threads != core)) {
                    if (threads < core) {
                        threadPoolExecutor.setCorePoolSize(threads);
                        if (core == max) {
                            threadPoolExecutor.setMaximumPoolSize(threads);
                        }
                    } else {
                        threadPoolExecutor.setMaximumPoolSize(threads);
                        if (core == max) {
                            threadPoolExecutor.setCorePoolSize(threads);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }

    @Override
    public ScheduledExecutorService nextScheduledExecutor() {
        return scheduledExecutors.pollItem();
    }

    // gx
    @Override
    public ScheduledExecutorService getServiceExporterExecutor() {
        return serviceExporterExecutor;
    }

    @Override
    public ExecutorService getSharedExecutor() {
        return SHARED_EXECUTOR;
    }

    private ExecutorService createExecutor(URL url) {
        // 获取ThreadPool的自适应扩展类(源码字符串+Compiler自动生成的)，自适应扩展类的getExecutor方法内部会根据url获取对应的
        // 扩展类实例，默认是fixed（四种，具体看ThreadPool的SPI文件）并调用其getExecutor方法（同时传入url）
        return (ExecutorService) ExtensionLoader.getExtensionLoader(ThreadPool.class).getAdaptiveExtension().getExecutor(url);
    }

}

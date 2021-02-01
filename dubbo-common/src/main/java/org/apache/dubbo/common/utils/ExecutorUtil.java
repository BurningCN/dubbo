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
package org.apache.dubbo.common.utils;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.THREAD_NAME_KEY;

// OK
public class ExecutorUtil {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorUtil.class);
    // 这个线程池很特殊，名字叫做关闭线程池，核心线程数是0
    private static final ThreadPoolExecutor SHUTDOWN_EXECUTOR = new ThreadPoolExecutor(0, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(100),
            new NamedThreadFactory("Close-ExecutorService-Timer", true));

    public static boolean isTerminated(Executor executor) {
        if (executor instanceof ExecutorService) {
            // 线程池是否关闭
            if (((ExecutorService) executor).isTerminated()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Use the shutdown pattern from:
     * https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html
     *
     * @param executor the Executor to shutdown
     * @param timeout  the timeout in milliseconds before termination
     */
    public static void gracefulShutdown(Executor executor, int timeout) {
        if (!(executor instanceof ExecutorService) || isTerminated(executor)) {
            return;
        }
        final ExecutorService es = (ExecutorService) executor;
        try {
            // Disable new tasks from being submitted
            es.shutdown();
        } catch (SecurityException ex2) {
            return;
        } catch (NullPointerException ex2) {
            return;
        }
        try {
            // Wait a while for existing tasks to terminate
            if (!es.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                es.shutdownNow();
            }
        } catch (InterruptedException ex) {
            es.shutdownNow();
            Thread.currentThread().interrupt();
            // 从上面shutdown到这里的逻辑和rmq的一样，dubbo多了下面的操作
        }
        if (!isTerminated(es)) {
            // 如果还没关闭，创建一个线程取关闭线程池，进去
            newThreadToCloseExecutor(es);
        }
    }

    // 立即关闭
    public static void shutdownNow(Executor executor, final int timeout) {
        if (!(executor instanceof ExecutorService) || isTerminated(executor)) {
            return;
        }
        final ExecutorService es = (ExecutorService) executor;
        try {
            es.shutdownNow();
        } catch (SecurityException ex2) {
            return;
        } catch (NullPointerException ex2) {
            return;
        }
        try {
            // 都shutdownNow了，还要等待一段时间
            es.awaitTermination(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (!isTerminated(es)) {
            newThreadToCloseExecutor(es);
        }
    }

    private static void newThreadToCloseExecutor(final ExecutorService es) {
        // 再次判断，双重检查
        if (!isTerminated(es)) {
            SHUTDOWN_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 1000次循环关闭，确保退出（因为SHUTDOWN_EXECUTOR的核心线程数为0，所以都进队列了）
                        for (int i = 0; i < 1000; i++) {
                            es.shutdownNow();
                            if (es.awaitTermination(10, TimeUnit.MILLISECONDS)) {
                                break;
                            }
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (Throwable e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
            });
        }
    }

    /**
     * append thread name with url address
     *
     * @return new url with updated thread name
     */
    // 线程池的线程名称带ip信息
    public static URL setThreadName(URL url, String defaultName) {
        String name = url.getParameter(THREAD_NAME_KEY, defaultName);// eg defaultName=DubboServerHandler
        name = name + "-" + url.getAddress();// DubboServerHandler-192.168.1.7:20880
        url = url.addParameter(THREAD_NAME_KEY, name);// threadname=DubboServerHandler-192.168.1.7:20880
        return url;
    }

    // Qos heart
    public static void cancelScheduledFuture(ScheduledFuture<?> scheduledFuture) {
        ScheduledFuture<?> future = scheduledFuture;
        if (future != null && !future.isCancelled()) {
            future.cancel(true);
        }
    }
}

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
package org.apache.dubbo.common.threadpool.support;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.event.ThreadPoolExhaustedEvent;
import org.apache.dubbo.common.utils.JVMUtil;
import org.apache.dubbo.event.EventDispatcher;

import static org.apache.dubbo.common.constants.CommonConstants.DUMP_DIRECTORY;

/**
 * Abort Policy.
 * Log warn info when abort.
 */
// OK
// dubbo自己实现了一个拒绝策略，在原来支持的AbortPolicy基础上加了WithReport功能。可以看到extends ThreadPoolExecutor.AbortPolicy
public class AbortPolicyWithReport extends ThreadPoolExecutor.AbortPolicy {

    protected static final Logger logger = LoggerFactory.getLogger(AbortPolicyWithReport.class);

    private final String threadName;

    private final URL url;

    private static volatile long lastPrintTime = 0;

    private static final long TEN_MINUTES_MILLS = 10 * 60 * 1000;

    private static final String OS_WIN_PREFIX = "win";

    private static final String OS_NAME_KEY = "os.name";

    private static final String WIN_DATETIME_FORMAT = "yyyy-MM-dd_HH-mm-ss";

    private static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    private static Semaphore guard = new Semaphore(1);

    // gx发现主要是在new ThreadPoolExecutor的时候传入自定义的拒绝策略
    public AbortPolicyWithReport(String threadName, URL url) {
        this.threadName = threadName;
        this.url = url;
    }

    // 当线程池发生拒绝操作的时候，会调用如下方法，这里是重写了父类AbortPolicy的rejectedExecution方法
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        // 没有用到参数r

        String msg = String.format("Thread pool is EXHAUSTED!" +  // Thread pool is EXHAUSTED! 线程池EXHAUSTED疲惫不堪，存放太多任务触发拒绝操作
                " Thread Name: %s, Pool Size: %d (active: %d, core: %d, max: %d, largest: %d), Task: %d (completed: "
                + "%d)," +
                " Executor status:(isShutdown:%s, isTerminated:%s, isTerminating:%s), in %s://%s:%d!",
            threadName, e.getPoolSize(), e.getActiveCount(), e.getCorePoolSize(), e.getMaximumPoolSize(),
            e.getLargestPoolSize(),
            e.getTaskCount(), e.getCompletedTaskCount(), e.isShutdown(), e.isTerminated(), e.isTerminating(),
            url.getProtocol(), url.getIp(), url.getPort());
        // 1.打日志 以AbortPolicyWithReportTest为例:Thread pool is EXHAUSTED! Thread Name: Test, Pool Size: 0 (active: 0, core: 1, max: 1, largest: 0), Task: 0 (completed: 0), Executor status:(isShutdown:false, isTerminated:false, isTerminating:false), in dubbo://10.20.130.230:20880!
        logger.warn(msg);

        // 2.dump系统当前线程的堆栈信息，进去
        dumpJStack();

        // 3.处理事件，事件监听模型的惯用法：发生事件后让对应的监听器进行处理，进去
        dispatchThreadPoolExhaustedEvent(msg); // dispatch:派遣，发送；迅速处理，

        // 4.最后还是抛异常，和AbortPolicy的rejectedExecution方法的内容一样，只是上面做了一些其他操作
        throw new RejectedExecutionException(msg);
    }

    /**
     * dispatch ThreadPoolExhaustedEvent
     * @param msg
     */
    public void dispatchThreadPoolExhaustedEvent(String msg) {
        // dispatch进去
        EventDispatcher.getDefaultExtension().dispatch(new ThreadPoolExhaustedEvent(this, msg));
    }

    private void dumpJStack() {
        long now = System.currentTimeMillis();

        // dump every 10 minutes
        if (now - lastPrintTime < TEN_MINUTES_MILLS) {
            return;
        }

        // 信号量限制并发数为1
        if (!guard.tryAcquire()) {
            return;
        }

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            String dumpPath = url.getParameter(DUMP_DIRECTORY, System.getProperty("user.home"));

            SimpleDateFormat sdf;

            String os = System.getProperty(OS_NAME_KEY).toLowerCase();

            // window system don't support ":" in file name
            if (os.contains(OS_WIN_PREFIX)) {
                sdf = new SimpleDateFormat(WIN_DATETIME_FORMAT);
            } else {
                sdf = new SimpleDateFormat(DEFAULT_DATETIME_FORMAT);
            }

            String dateStr = sdf.format(new Date());
            // try-with-resources
            try (FileOutputStream jStackStream = new FileOutputStream(
                new File(dumpPath, "Dubbo_JStack.log" + "." + dateStr))) { // new File第二个参数是child及具体的文件名称
                // 进去
                JVMUtil.jstack(jStackStream);
            } catch (Throwable t) {
                logger.error("dump jStack error", t);
            } finally {
                // 释放信号量
                guard.release();
            }
            lastPrintTime = System.currentTimeMillis();
        });

        // must shutdown thread pool ,if not will lead to OOM
        pool.shutdown();

    }



}

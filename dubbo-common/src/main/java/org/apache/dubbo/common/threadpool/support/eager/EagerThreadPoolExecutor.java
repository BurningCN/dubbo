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

package org.apache.dubbo.common.threadpool.support.eager;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EagerThreadPoolExecutor
 */
// OK
// 接触到的自定义ThreadFactory的方式居多，现在还看到了自定义拒绝策略、以及下面这个自定义线程池类，继承了ThreadPoolExecutor，
// 重写了afterExecute、execute方法（其实还有一个beforeExecute），主要就是在执行任务的时候做一些前后置操作
// 该线程池的特点是：可以重新将拒绝掉的task，重新添加的work queue中执行。相当于有一个重试机制！
public class EagerThreadPoolExecutor extends ThreadPoolExecutor {

    /**
     * task count
     */
    private final AtomicInteger submittedTaskCount = new AtomicInteger(0);

    public EagerThreadPoolExecutor(int corePoolSize,
                                   int maximumPoolSize,
                                   long keepAliveTime,
                                   TimeUnit unit, TaskQueue workQueue,
                                   ThreadFactory threadFactory,
                                   RejectedExecutionHandler handler) { // 这个就是拒绝策略，eg AbortPolicy implements RejectedExecutionHandler handler
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /**
     * @return current tasks which are executed
     */
    public int getSubmittedTaskCount() {
        return submittedTaskCount.get();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        // 后置处理，执行后submittedTaskCount--
        submittedTaskCount.decrementAndGet();
    }


    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException();
        }
        // do not increment in method beforeExecute! todo 疑问
        // 前置处理，执行前submittedTaskCount++
        submittedTaskCount.incrementAndGet();
        try {
            // 执行任务
            super.execute(command);
        } catch (RejectedExecutionException rx) {
            // 因为定义线程池目前只用了AbortPolicyWithReport，本质还是AbortPolicy，任务过载的时候抛异常，进入catch块

            // retry to offer the task into queue.
            final TaskQueue queue = (TaskQueue) super.getQueue();
            try {
                // 重新把任务投递到队列，进去
                if (!queue.retryOffer(command, 0, TimeUnit.MILLISECONDS)) {
                    // 投递失败的话（失败原因看下面日志），抛异常
                    submittedTaskCount.decrementAndGet();
                    // 日志
                    throw new RejectedExecutionException("Queue capacity is full.", rx);
                }
            } catch (InterruptedException x) {
                // 同上
                submittedTaskCount.decrementAndGet();
                throw new RejectedExecutionException(x);
            }
            // 除RejectedExecutionException之外的异常
        } catch (Throwable t) {
            // decrease any way
            submittedTaskCount.decrementAndGet();
            throw t;
        }
    }
}

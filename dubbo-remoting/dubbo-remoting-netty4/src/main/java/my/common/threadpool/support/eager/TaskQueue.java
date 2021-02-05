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

package my.common.threadpool.support.eager;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * TaskQueue in the EagerThreadPoolExecutor
 * It offer a task if the executor's submittedTaskCount less than currentPoolThreadSize
 * or the currentPoolThreadSize more than executor's maximumPoolSize.
 * That can make the executor create new worker
 * when the task num is bigger than corePoolSize but less than maximumPoolSize.
 *
 * 在EagerThreadPoolExecutor中的TaskQueue
 * 如果executor的submittedTaskCount小于currentPoolThreadSize，或者currentPoolThreadSize大于executor的maximumPoolSize，那么它将提供一个任务。
 * 当任务num大于corePoolSize但小于maximumPoolSize时，执行器可以创建新的worker。
 *
 */

// OK
// 可以看下博客的方案2就是下面的设计。标题:Java如何让线程池满后再放队列，地址:https://www.jianshu.com/p/62043956bfb6
public class TaskQueue<R extends Runnable> extends LinkedBlockingQueue<Runnable> {

    private static final long serialVersionUID = -2635853580887179627L;

    private EagerThreadPoolExecutor executor;

    // gx
    public TaskQueue(int capacity) {
        super(capacity);
    }

    // gx 目的是为了在后面的offer方法获取线程池的一些属性(getPoolSize)
    public void setExecutor(EagerThreadPoolExecutor exec) {
        executor = exec;
    }

    // 线程池内部会调用如下方法（在线程数达到coreSize的时候），外界一般不会调用
    // offer方法的含义是：将任务提交到队列中，返回值为true/false，分别代表提交成功/提交失败
    @Override
    public boolean offer(Runnable runnable) {
        if (executor == null) {
            // 日志
            throw new RejectedExecutionException("The task queue does not have executor!");
        }

        // 获取当前线程数，肯定返回值肯定>=coreSize的（因为前面说了offer只有在线程数达到coreSize的时候才会调用）
        int currentPoolThreadSize = executor.getPoolSize();

        // have free worker. put task into queue to let the worker deal with task.
        // 假设coreSize=5,maxSize=10，currentPoolThreadSize肯定>=5，executor.getSubmittedTaskCount()记作count。
        // 且注意execute前count++，afterExecute后count--
        // (1).假设为currentPoolThreadSize = 5，count = 5，表示此时5个线程都在执行中，一个也没有触发afterExecute（内部使得count--），跳过if分支
        // (2).假设为currentPoolThreadSize = 5，count = 3，表示此时3个线程都在执行中，另两个触发afterExecute（内部使得count--），进if分支，直接添加到队列，让剩余的两个线程从队列取任务
        // 假设为currentPoolThreadSize > 5，和前面两个描述一样
        if (executor.getSubmittedTaskCount() < currentPoolThreadSize) {
            return super.offer(runnable);
        }

        // return false to let executor create new worker. 这是关键
        // 重点： 当前线程数小于 最大线程数 ，返回false，暗含入队失败，让线程池去创建新的线程
        if (currentPoolThreadSize < executor.getMaximumPoolSize()) {
            return false;
        }

        // currentPoolThreadSize >= max
        // 重点: 代码运行到此处，说明当前线程数 >= 最大线程数，需要真正的提交到队列中
        return super.offer(runnable);
    }

    /**
     * retry offer task
     *
     * @param o task
     * @return offer success or not
     * @throws RejectedExecutionException if executor is terminated.
     */
    // gx
    public boolean retryOffer(Runnable o, long timeout, TimeUnit unit) throws InterruptedException {
        if (executor.isShutdown()) {
            throw new RejectedExecutionException("Executor is shutdown!");
        }
        return super.offer(o, timeout, unit);
    }
}

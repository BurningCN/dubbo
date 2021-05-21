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
package org.apache.dubbo.common.threadpool;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The most important difference between this Executor and other normal Executor is that this one doesn't manage
 * any thread.
 *
 * Tasks submitted to this executor through {@link #execute(Runnable)} will not get scheduled to a specific thread, though normal executors always do the schedule.
 * Those tasks are stored in a blocking queue and will only be executed when a thread calls {@link #waitAndDrain()}, the thread executing the task
 * is exactly the same as the one calling waitAndDrain.
 *
 这个Executor和其他正常Executor之间最重要的区别是这个Executor不管理任何线程。
 通过execute(Runnable)方法提交给这个执行器的任务不会被调度到特定线程，而其他的Executor就把Runnable交给线程去执行了。
 这些任务存储在阻塞队列中，只有当thead调用waitAndDrain()方法时才会真正执行。简单来说就是，执行task的thead与调用waitAndDrain()方法的thead完全相同。
 */
/*(1)消费端发起调用的时候，首 先DefaultInvoker#doInvoke的else分支 ->getCallbackExecutor ->SYNC->ThreadlessExecutor executor
-> client.getChannel().request -> DefaultFuture#newFuture 存入该executor。

(2)上面发起rpc之后，AsyncToSyncInvoker#invoke判定是SYNC，则会调用AsyncRpcResult#get->threadlessExecutor.waitAndDrain();在阻塞队列take阻塞

(3) 消费端拿到对端的响应数据的时候，Client这端的AllChannelHandler触发received方法，调用getPreferredExecutorService方法，
拿到先前保存在DefaultFuture的 ThreadlessExecutor executor对象，调用其execute方法，put任务到阻塞队列。步骤2解除阻塞，执行 ChannelEventRunnable任务，进行解码逻辑*/
public class ThreadlessExecutor extends AbstractExecutorService {
    private static final Logger logger = LoggerFactory.getLogger(ThreadlessExecutor.class.getName());

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    private ExecutorService sharedExecutor;

    private CompletableFuture<?> waitingFuture;

    private boolean finished = false;

    private volatile boolean waiting = true;

    private final Object lock = new Object();

    public ThreadlessExecutor(ExecutorService sharedExecutor) {
        this.sharedExecutor = sharedExecutor;
    }

    public CompletableFuture<?> getWaitingFuture() {
        return waitingFuture;
    }

    public void setWaitingFuture(CompletableFuture<?> waitingFuture) {
        this.waitingFuture = waitingFuture;
    }

    public boolean isWaiting() {
        return waiting;
    }

    /**
     * Waits until there is a task, executes the task and all queued tasks (if there're any). The task is either a normal
     * response or a timeout response.
     * 等待，直到有任务，执行任务和所有排队的任务(如果有的话)。该任务要么是正常响应，要么是超时响应。
     */
    public void waitAndDrain() throws InterruptedException {
        /**
         * Usually, {@link #waitAndDrain()} will only get called once. It blocks for the response for the first time,
         * once the response (the task) reached and being executed waitAndDrain will return, the whole request process
         * then finishes. Subsequent calls on {@link #waitAndDrain()} (if there're any) should return immediately.
         *
         * There's no need to worry that {@link #finished} is not thread-safe. Checking and updating of
         * 'finished' only appear in waitAndDrain, since waitAndDrain is binding to one RPC call (one thread), the call
         * of it is totally sequential.
         *
         * 通常，{@link #waitAndDrain()}只会被调用一次。它第一次阻塞响应，一旦响应(任务)到达并被执行，waitAndDrain将返回，整个请求过程就会结束。
         * 后续对{@link #waitAndDrain()}的调用(如果有的话)应该立即返回。
         * 没有必要担心{@link #finished}不是线程安全的。检查和更新'finished'只会出现在waitAndDrain中，因为waitAndDrain绑定到一个RPC调用(一个线程)，
         * 所以对它的调用完全是顺序的。
         *
         * 这里补充一句，上面说不要担心线程安全，是因为该类对象构造方法是每次现new的（DubboInvoker#doInvoke的else逻辑的getCallbackExecutor）
         * 每次new完之后，调用waitAndDrain处理的时候，最后将finished置为true。
         *
         */
        if (finished) {
            return;
        }

        // 阻塞式获取
        Runnable runnable = queue.take();

        synchronized (lock) {
            waiting = false;
            runnable.run();
        }

        // 执行queue里的所有任务
        runnable = queue.poll();
        while (runnable != null) {
            try {
                runnable.run();
            } catch (Throwable t) {
                logger.info(t);

            }
            runnable = queue.poll();
        }
        // mark the status of ThreadlessExecutor as finished.
        finished = true;
    }

    public long waitAndDrain(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        /*long startInMs = System.currentTimeMillis();
        Runnable runnable = queue.poll(timeout, unit);
        if (runnable == null) {
            throw new TimeoutException();
        }
        runnable.run();
        long elapsedInMs = System.currentTimeMillis() - startInMs;
        long timeLeft = timeout - elapsedInMs;
        if (timeLeft < 0) {
            throw new TimeoutException();
        }
        return timeLeft;*/
        throw new UnsupportedOperationException();
    }

    /**
     * If the calling thread is still waiting for a callback task, add the task into the blocking queue to wait for schedule.
     * Otherwise, submit to shared callback executor directly.
     *
     * @param runnable
     */
    // 同时我们还可以看到，里面还维护了一个名称叫做sharedExecutor的线程池。见名知意，我们就知道了，这里应该是要做线程池共享了。
    // 1.使用ThreadlessExceutor，aka.，将回调直接委托给发起调用的线程。
    // 2.使用shared executor执行回调。
    @Override
    public void execute(Runnable runnable) {
        // 这里加锁和前面的synchronized (lock) 一致，控制是否是waiting，做相应的操作的，比如前面在执行，waiting为false。那么这里就直接用sharedExecutor执行
        synchronized (lock) {
            if (!waiting) {
                sharedExecutor.execute(runnable);
            } else {
                queue.add(runnable);
            }
        }
    }

    /**
     * tells the thread blocking on {@link #waitAndDrain()} to return, despite of the current status, to avoid endless waiting.
     */
    public void notifyReturn(Throwable t) {
        // an empty runnable task.
        execute(() -> {
            waitingFuture.completeExceptionally(t);
        });
    }

    /**
     * The following methods are still not supported
     */

    @Override
    public void shutdown() {
        shutdownNow();
    }

    @Override
    public List<Runnable> shutdownNow() {
        notifyReturn(new IllegalStateException("Consumer is shutting down and this call is going to be stopped without " +
                "receiving any result, usually this is called by a slow provider instance or bad service implementation."));
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }
}

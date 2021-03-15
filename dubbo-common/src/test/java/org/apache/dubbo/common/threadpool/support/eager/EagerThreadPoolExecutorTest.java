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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.threadpool.ThreadPool;
import org.apache.dubbo.common.threadpool.support.AbortPolicyWithReport;
import org.apache.dubbo.common.utils.NamedThreadFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

// OK
public class EagerThreadPoolExecutorTest {

    private static final URL URL = new URL("dubbo", "localhost", 8080);

    /**
     * It print like this:
     * thread number in current pool：1,  task number in task queue：0 executor size: 1
     * thread number in current pool：2,  task number in task queue：0 executor size: 2
     * thread number in current pool：3,  task number in task queue：0 executor size: 3
     * thread number in current pool：4,  task number in task queue：0 executor size: 4
     * thread number in current pool：5,  task number in task queue：0 executor size: 5
     * thread number in current pool：6,  task number in task queue：0 executor size: 6
     * thread number in current pool：7,  task number in task queue：0 executor size: 7
     * thread number in current pool：8,  task number in task queue：0 executor size: 8
     * thread number in current pool：9,  task number in task queue：0 executor size: 9
     * thread number in current pool：10,  task number in task queue：0 executor size: 10
     * thread number in current pool：10,  task number in task queue：4 executor size: 10
     * thread number in current pool：10,  task number in task queue：3 executor size: 10
     * thread number in current pool：10,  task number in task queue：2 executor size: 10
     * thread number in current pool：10,  task number in task queue：1 executor size: 10
     * thread number in current pool：10,  task number in task queue：0 executor size: 10
     * <p>
     *
     * ☆☆☆☆
     * We can see , when the core threads are in busy,
     * the thread pool create thread (but thread nums always less than max) instead of put task into queue.
     * 对应上面注释，一般设置core、max、queue之后，线程数达到了核心线程数+任务队列满了，线程数才会超过核心线程数（当然不会超过max），
     * 而TaskQueue这种设计不需要，当线程数达到了核心线程数，不会把任务放到队列，而是直接创建线程！从上面输出就看出来了
     * 这能表明为何叫做EagerThreadPoolExecutor，eager的意思就是迫切的、急切的
     *
     */
    @Test
    public void testEagerThreadPool() throws Exception {
        String name = "eager-tf";
        int queues = 5;
        int cores = 5;
        int threads = 10;
        // alive 1 second
        long alive = 1000;

        // init queue and executor
        TaskQueue taskQueue = new TaskQueue(queues);
        final EagerThreadPoolExecutor executor = new EagerThreadPoolExecutor(
                cores, // 5
                threads, // 10
                alive,// 1000
                TimeUnit.MILLISECONDS,
                taskQueue, // 5
                new NamedThreadFactory(name, true),
                new AbortPolicyWithReport(name, URL));
        taskQueue.setExecutor(executor);

        // 15个任务
        for (int i = 0; i < 15; i++) {
            Thread.sleep(50);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // 获取线程池当前线程数 和 任务队列的任务数其实就是队列大小--->回到上面看☆☆☆☆的部分
                    System.out.println("thread number in current pool：" + executor.getPoolSize() + ", " +
                            " task number in task queue：" + executor.getQueue()
                            .size() + " executor size: " + executor.getPoolSize());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        Thread.sleep(5000);
        // cores theads are all alive.这也是一个重要的点，线程池的核心线程数的线程是永远不是死亡的，只有[超过核心线程数的那些线程]在空闲时间到达在alive到达后死亡
        // 上面睡了5s，那些[超过核心线程数的那些线程]的alive才1s（超过的个数为5，可以看上面输出），肯定全部死亡，所以这里输出当前的线程数量就是核心线程数的值
        Assertions.assertEquals(executor.getPoolSize(), cores, "more than cores threads alive!");
    }

    @Test
    public void testSPI() {
        ExecutorService executorService = (ExecutorService) ExtensionLoader.getExtensionLoader(ThreadPool.class)
                .getExtension("eager")
                // 进去
                .getExecutor(URL);
        Assertions.assertEquals("EagerThreadPoolExecutor", executorService.getClass()
            .getSimpleName(), "test spi fail!");
    }
}
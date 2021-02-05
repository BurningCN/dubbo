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
package my.common.threadpool.concurrent;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

// OK
public class ScheduledCompletableFuture {


    public static <T> CompletableFuture<T> schedule(
            ScheduledExecutorService executor,
            Supplier<T> task,// 注意
            long delay,
            TimeUnit unit
    ) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        executor.schedule(
                () -> {
                    try {
                        return completableFuture.complete(task.get());
                    } catch (Throwable t) {
                        return completableFuture.completeExceptionally(t);
                    }
                },
                delay,
                unit
        );
        return completableFuture;
    }

    public static void main(String[] args) {
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService scheduledExecutorService1 = Executors.newScheduledThreadPool(10);
    }


    // gx
    public static <T> CompletableFuture<T> submit(
            ScheduledExecutorService executor,// ScheduledExecutorService的两种常见构造见上面
            Supplier<T> task
    ) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        executor.submit(
                () -> {
                    try {
                        // Supplier包装到CompletableFuture。所以我们实现一个带有返回结果的任务不一定使用callable，可以直接用CompletableFuture
                        return completableFuture.complete(task.get());// 注意下task.get()
                    } catch (Throwable t) {
                        return completableFuture.completeExceptionally(t);
                    }
                }
        );
        // 直接直接返回completableFuture对象，不会阻塞，具体何时完成取决于上面的线程何时complete或者completeExceptionally
        return completableFuture;
    }

}

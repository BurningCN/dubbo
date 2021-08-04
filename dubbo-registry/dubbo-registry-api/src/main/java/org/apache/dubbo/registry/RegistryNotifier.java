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
package org.apache.dubbo.registry;

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 延迟执行 + 同一时间段的多次触发仅触发最后一次
public abstract class RegistryNotifier {

    private volatile long lastExecuteTime;
    private volatile long lastEventTime;

    private Object rawAddresses;
    private long delayTime;

    private ScheduledExecutorService scheduler;

    public RegistryNotifier(long delayTime) {
        this(delayTime, null);
    }

    public RegistryNotifier(long delayTime, ScheduledExecutorService scheduler) {
        this.delayTime = delayTime;
        if (scheduler == null) {
            this.scheduler = ExtensionLoader.getExtensionLoader(ExecutorRepository.class)
                    .getDefaultExtension().getRegistryNotificationExecutor();
        } else {
            this.scheduler = scheduler;
        }
    }

    public synchronized void notify(Object rawAddresses) {
        this.rawAddresses = rawAddresses;
        long notifyTime = System.currentTimeMillis();
        this.lastEventTime = notifyTime;
        //注意区分，上面是last{Event}Time,这里是last{Execute}Time
        long delta = (System.currentTimeMillis() - lastExecuteTime) - delayTime;
        if (delta >= 0) {
            scheduler.submit(new NotificationTask(this, notifyTime));
        } else {
            scheduler.schedule(new NotificationTask(this, notifyTime), -delta, TimeUnit.MILLISECONDS);
        }
    }

    public long getDelayTime() {
        return delayTime;
    }

    protected abstract void doNotify(Object rawAddresses);

    public static class NotificationTask implements Runnable {
        private final RegistryNotifier listener;
        private final long time;

        public NotificationTask(RegistryNotifier listener, long time) {
            this.listener = listener;
            this.time = time;
        }

        @Override
        public void run() {
            // 这里判断的原因我猜是假设前面notify方法连续调用两次，第一次肯定 lastEventTime = time，走submit逻辑，刚要执行下面的if，
            // 前面notify更早的触发，此时lastEventTime是最新的值了，下面的if判断两个值不相等，也就不会执行了
            // 这也就是lastEventTime声明为volatile的原因。同时实现了多次通知只会确保最后一次得到调用
            if (this.time == listener.lastEventTime) {
                listener.doNotify(listener.rawAddresses);
                listener.lastExecuteTime = System.currentTimeMillis();
                // 和前面sync是一个锁，需要下面判断+赋值变成原子操作，所以加了锁
                synchronized (listener) {
                    if (this.time == listener.lastEventTime) {
                        listener.rawAddresses = null;
                    }
                }
            }
        }
    }

}

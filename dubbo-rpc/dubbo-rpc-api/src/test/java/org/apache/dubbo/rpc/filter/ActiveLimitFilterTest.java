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
package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcStatus;
import org.apache.dubbo.rpc.support.BlockMyInvoker;
import org.apache.dubbo.rpc.support.MockInvocation;
import org.apache.dubbo.rpc.support.MyInvoker;
import org.apache.dubbo.rpc.support.RuntimeExceptionInvoker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ActiveLimitFilterTest.java
 */
public class ActiveLimitFilterTest {

    ActiveLimitFilter activeLimitFilter = new ActiveLimitFilter();

    @Test
    public void testInvokeNoActives() {
        URL url = URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1&actives=0");
        Invoker<ActiveLimitFilterTest> invoker = new MyInvoker<ActiveLimitFilterTest>(url);
        Invocation invocation = new MockInvocation();
        activeLimitFilter.invoke(invoker, invocation);
    }

    @Test
    public void testInvokeLessActives() {
        URL url = URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1&actives=10");
        Invoker<ActiveLimitFilterTest> invoker = new MyInvoker<ActiveLimitFilterTest>(url);
        Invocation invocation = new MockInvocation();
        activeLimitFilter.invoke(invoker, invocation);
    }

    @Test
    public void testInvokeGreaterActives() {
        AtomicInteger count = new AtomicInteger(0);
        // 注意active和timeout参数
        URL url = URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1&actives=1&timeout=1");
        // 一次调用要阻塞100ms，而上面的timeout限制1ms，肯定下面100*100次，9999次都因为超时抛异常
        final Invoker<ActiveLimitFilterTest> invoker = new BlockMyInvoker<ActiveLimitFilterTest>(url, 100);
        final Invocation invocation = new MockInvocation();
        final CountDownLatch latch = new CountDownLatch(1);
        // 100个线程，每个线程100次调用，但是active为1，只有一次能成功，有9999次会多次尝试后仍获取不到机会（内部的不能满足RpcStatus.active<=max）失败
        for (int i = 0; i < 100; i++) {
            Thread thread = new Thread(new Runnable() {

                public void run() {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    for (int i = 0; i < 100; i++) {
                        try {
                            // 进去
                            activeLimitFilter.invoke(invoker, invocation);
                        } catch (RpcException expected) {
                            // 调用失败的话，count++，记录失败次数
                            count.incrementAndGet();
                        }
                    }
                }
            });
            thread.start();
        }
        latch.countDown();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // count = 9999，失败9999次，因为active = 1
        assertNotSame(0, count.intValue());
    }

    @Test
    public void testInvokeNotTimeOut() throws Exception {
        int totalThread = 100;
        int maxActives = 10;
        long timeout = 1000;
        long blockTime = 0;
        AtomicInteger count = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(1); // 控制所有线程在起点线
        final CountDownLatch latchBlocking = new CountDownLatch(totalThread);// 控制所有线程跑到终点
        URL url = URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1&actives=" + maxActives + "&timeout=" + timeout);
        // 阻塞时间0
        final Invoker<ActiveLimitFilterTest> invoker = new BlockMyInvoker<ActiveLimitFilterTest>(url, blockTime);
        final Invocation invocation = new MockInvocation();
        for (int i = 0; i < totalThread; i++) {
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        try {
                            // 所有线程在这里等待
                            latch.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        try {
                            Result asyncResult = activeLimitFilter.invoke(invoker, invocation);
                            // 阻塞等待结果，其实压根不会阻塞，具体看BlockMyInvoker的invoke方法，直接睡眠完成才会返回，
                            Result result = asyncResult.get();
                            // 关键是这个，内部会把调用次数 -- 进去
                            activeLimitFilter.onResponse(result, invoker, invocation);
                        } catch (RpcException expected) {
                            count.incrementAndGet();
                            activeLimitFilter.onError(expected, invoker, invocation);
                        } catch (Exception e) {
                            fail();
                        }
                    } finally {
                        latchBlocking.countDown();
                    }
                }
            });
            thread.start();
        }
        // 发号
        latch.countDown();

        try {
            latchBlocking.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 没有一次抛异常，全部调用成功
        assertEquals(0, count.intValue());
    }

    @Test
    public void testInvokeTimeOut() throws Exception {
        int totalThread = 100;
        int maxActives = 10;
        long timeout = 1;
        long blockTime = 10000;
        AtomicInteger count = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latchBlocking = new CountDownLatch(totalThread);
        URL url = URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1&actives=" + maxActives + "&timeout=" + timeout);
        // 一次调用要阻塞100ms，而上面的timeout限制1ms，下面100次，肯定90次都因为超时失败
        final Invoker<ActiveLimitFilterTest> invoker = new BlockMyInvoker<ActiveLimitFilterTest>(url, blockTime);
        final Invocation invocation = new MockInvocation();
        RpcStatus.removeStatus(url);
        RpcStatus.removeStatus(url, invocation.getMethodName());

        Result asyncResult = activeLimitFilter.invoke(invoker, invocation);
        Result result = asyncResult.get();
        for (int i = 0; i < totalThread; i++) {
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        try {
                            Result asyncResult = activeLimitFilter.invoke(invoker, invocation);
                            // 阻塞等待结果
                            Result result = asyncResult.get();
                            activeLimitFilter.onResponse(result, invoker, invocation);
                        } catch (RpcException expected) {
                            count.incrementAndGet();
                        } catch (Exception e) {
                            fail();
                        }
                    } finally {
                        latchBlocking.countDown();
                    }
                }
            });
            thread.start();
        }
        latch.countDown();

        try {
            latchBlocking.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertEquals(90, count.intValue());
    }



    // 先看下下面的testInvokeRuntimeExceptionWithActiveCountMatch方法，再看这个
    @Test
    public void testInvokeRuntimeException() {
        Assertions.assertThrows(RuntimeException.class, () -> {
            URL url = URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1&actives=0");
            Invoker<ActiveLimitFilterTest> invoker = new RuntimeExceptionInvoker(url);
            Invocation invocation = new MockInvocation();
            RpcStatus count = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName());
            int beforeExceptionActiveCount = count.getActive();
            activeLimitFilter.invoke(invoker, invocation);
            int afterExceptionActiveCount = count.getActive();
            assertEquals(beforeExceptionActiveCount, afterExceptionActiveCount, "After exception active count should be same");
        });
    }

    @Test
    public void testInvokeRuntimeExceptionWithActiveCountMatch() {
        // active = 0 max=Integer.MAX_VALUE
        URL url = URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1&actives=0");
        Invoker<ActiveLimitFilterTest> invoker = new RuntimeExceptionInvoker(url);
        Invocation invocation = new MockInvocation();
        RpcStatus count = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName());
        int beforeExceptionActiveCount = count.getActive();// 0
        try {
            activeLimitFilter.invoke(invoker, invocation);
        } catch (RuntimeException ex) { // 上面的invoke会抛异常
            // 进去
            activeLimitFilter.onError(ex, invoker, invocation);
            int afterExceptionActiveCount = count.getActive();
            assertEquals(beforeExceptionActiveCount, afterExceptionActiveCount, "After exception active count should be same");
        }
    }
}

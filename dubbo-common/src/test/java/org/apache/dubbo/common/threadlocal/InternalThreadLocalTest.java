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

package org.apache.dubbo.common.threadlocal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class InternalThreadLocalTest {

    private static final int THREADS = 10;

    private static final int PERFORMANCE_THREAD_COUNT = 1000;

    private static final int GET_COUNT = 1000000;

    @Test
    public void testInternalThreadLocal() throws InterruptedException {
        final AtomicInteger index = new AtomicInteger(0);

        // 模拟原生ThreadLocal模式，创建的时候也可以有initialValue。而且后面直接{xxx}还是挺新颖的，相当于new的同时重写initialValue方法
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>() {

            // 重写了InternalThreadLocal的initialValue，这方法的调用时机在get方法内的最后一行
            @Override
            protected Integer initialValue() throws Exception {
                Integer v = index.getAndIncrement();
                System.out.println("thread : " + Thread.currentThread().getName() + " init value : " + v);
                return v;
            }
        };
        // 创建10个线程，每个线程的任务就是从internalThreadLocal取值，根据原生ThreadLocal的作用肯定也是取到的自己的副本变量值
        for (int i = 0; i < THREADS; i++) {
            // get方法进去
            // 发现输出的值都是自己设置进去的，当然没有显示调用过set，不过在get的时候内部如果发现没有初始化过，会调用上面的初始化方法，相当于"set"
            // 而且输出的就是自己"set"进去的值，即上面return v的值
            Thread t = new Thread(internalThreadLocal::get);
            t.start();
        }

        Thread.sleep(2000);
    }

    @Test
    public void testRemoveAll() throws InterruptedException {
        // 内部的index属性为1
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>();
        // 这个值存到(当前Thread的)InternalThreadLocalMap的Object[] indexedVariables数组的index=1的位置，值为1
        internalThreadLocal.set(1);
        Assertions.assertEquals(1, (int)internalThreadLocal.get(), "set failed");

        // 内部的index属性为2（每次new都会递增）
        final InternalThreadLocal<String> internalThreadLocalString = new InternalThreadLocal<String>();
        // 这个值存到(当前Thread的)InternalThreadLocalMap的Object[] indexedVariables数组的index=2的位置，值为"value"
        internalThreadLocalString.set("value");
        Assertions.assertEquals("value", internalThreadLocalString.get(), "set failed");

        // 上面两次set之后，此时线程自己的InternalThreadLocalMap的第0个元素存放了先前的两个InternalThreadLocal，类比
        // 原生Thread里面就有一个ThreadLocal.ThreadLocalMap threadLocals变量，存储了这个线程之前set过的值，k是ThreadLocal引用，v是值
        // 只是InternalThreadLocalMap不是kv存储的，是数组结构，除0的位置存放了之前set过的值，比如上面的1和"value"。
        // 看下面☆处展示了当前threadLocalMap.indexedVariables数组存放的值

        // 这里给移除掉，进去
        InternalThreadLocal.removeAll();
        Assertions.assertNull(internalThreadLocal.get(), "removeAll failed!");
        Assertions.assertNull(internalThreadLocalString.get(), "removeAll failed!");
    }
    // ☆
    //threadLocalMap = {InternalThreadLocalMap@1567}
    // indexedVariables = {Object[32]@1568}
    //  0 = {Collections$SetFromMap@1569}  size = 2
    //   0 = {InternalThreadLocal@1562}
    //   1 = {InternalThreadLocal@1561}
    //  1 = {Integer@1570} 1
    //  2 = "value"
    //  3 = {Object@1572} --->这里的Object就是UNSET
    //  4 = {Object@1572}
    //  ........
    //  30 = {Object@1572}
    //  31 = {Object@1572}

    @Test
    public void testSize() throws InterruptedException {
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>();
        internalThreadLocal.set(1);
        Assertions.assertEquals(1, InternalThreadLocal.size(), "size method is wrong!");

        final InternalThreadLocal<String> internalThreadLocalString = new InternalThreadLocal<String>();
        internalThreadLocalString.set("value");

        // size进去
        Assertions.assertEquals(2, InternalThreadLocal.size(), "size method is wrong!");
    }

    @Test
    public void testSetAndGet() {
        final Integer testVal = 10;
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>();
        internalThreadLocal.set(testVal);
        Assertions.assertEquals(testVal, internalThreadLocal.get(), "set is not equals get");
    }

    @Test
    public void testRemove() {
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>();
        internalThreadLocal.set(1);
        Assertions.assertEquals(1, (int)internalThreadLocal.get(), "get method false!");

        // 虽然内部的index属性是递增的（每次new InternalThreadLocal递增），但是每个InternalThreadLocal记录了index的当前值，index本质还是实例属性
        // remove内部就是获取InternalThreadLocal的index，将Thread的InternalThreadLocalMap对应index位置为UNSET，同时将该InternalThreadLocal
        // 从Thread的InternalThreadLocalMap第0位取出Collection集合中移除
        // 进去
        internalThreadLocal.remove();
        Assertions.assertNull(internalThreadLocal.get(), "remove failed!");
    }

    @Test
    public void testOnRemove() {
        final Integer[] valueToRemove = {null};
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>() {
            // 重写了onRemoval
            @Override
            protected void onRemoval(Integer value) throws Exception {
                //value calculate
                valueToRemove[0] = value + 1;
            }
        };
        internalThreadLocal.set(1);
        Assertions.assertEquals(1, (int)internalThreadLocal.get(), "get method false!");

        // remove内部最后会调用onRemoval
        internalThreadLocal.remove();
        Assertions.assertEquals(2, (int)valueToRemove[0], "onRemove method failed!");
    }

    @Test
    public void testMultiThreadSetAndGet() throws InterruptedException {
        final Integer testVal1 = 10;
        final Integer testVal2 = 20;
        final InternalThreadLocal<Integer> internalThreadLocal = new InternalThreadLocal<Integer>();
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                // set
                internalThreadLocal.set(testVal1);
                // get看是不是自己设置的值
                Assertions.assertEquals(testVal1, internalThreadLocal.get(), "set is not equals get");
                countDownLatch.countDown();
            }
        });
        t1.start();

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                // 在set的时候，每个线程会拥有自己的InternalThreadLocalMap，所以虽然是同一个internalThreadLocal，但是保存到了自己的map里
                // 所以下面的equals能判断相等
                internalThreadLocal.set(testVal2);
                Assertions.assertEquals(testVal2, internalThreadLocal.get(), "set is not equals get");
                countDownLatch.countDown();
            }
        });
        t2.start();
        countDownLatch.await();
    }

    /**
     * print
     * take[2689]ms
     * <p></p>
     * This test is based on a Machine with 4 core and 16g memory.
     */
    @Test
    public void testPerformanceTradition() {
        final ThreadLocal<String>[] caches1 = new ThreadLocal[PERFORMANCE_THREAD_COUNT];
        final Thread mainThread = Thread.currentThread();
        for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
            caches1[i] = new ThreadLocal<String>();
        }
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
                    caches1[i].set("float.lu");
                }
                long start = System.nanoTime();
                for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
                    for (int j = 0; j < GET_COUNT; j++) {
                        caches1[i].get();
                    }
                }
                long end = System.nanoTime();
                System.out.println("take[" + TimeUnit.NANOSECONDS.toMillis(end - start) +
                        "]ms");
                LockSupport.unpark(mainThread);
            }
        });
        t1.start();
        LockSupport.park(mainThread);
    }

    /**
     * print
     * take[14]ms
     * <p></p>
     * This test is based on a Machine with 4 core and 16g memory.
     */
    @Test
    public void testPerformance() {
        final InternalThreadLocal<String>[] caches = new InternalThreadLocal[PERFORMANCE_THREAD_COUNT];
        final Thread mainThread = Thread.currentThread();
        for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
            caches[i] = new InternalThreadLocal<String>();
        }
        Thread t = new InternalThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
                    caches[i].set("float.lu");
                }
                long start = System.nanoTime();
                for (int i = 0; i < PERFORMANCE_THREAD_COUNT; i++) {
                    for (int j = 0; j < GET_COUNT; j++) {
                        caches[i].get();
                    }
                }
                long end = System.nanoTime();
                System.out.println("take[" + TimeUnit.NANOSECONDS.toMillis(end - start) +
                        "]ms");
                LockSupport.unpark(mainThread);
            }
        });
        t.start();
        LockSupport.park(mainThread);
    }
}


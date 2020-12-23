/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.dubbo.common.threadlocal;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The internal data structure that stores the threadLocal variables for Netty and all {@link InternalThread}s.
 * Note that this class is for internal use only. Use {@link InternalThread}
 * unless you know what you are doing.
 *
 * 存储Netty和所有{@link InternalThread}的threadLocal变量的内部数据结构。
 * 请注意，这个类仅供内部使用。使用{@link InternalThread}
 * 除非你知道自己在做什么。
 */
// OK
public final class InternalThreadLocalMap {

    private Object[] indexedVariables;

    private static ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap = new ThreadLocal<InternalThreadLocalMap>();

    private static final AtomicInteger NEXT_INDEX = new AtomicInteger();

    // 跟下赋值处(newIndexedVariableTable 方法)
    public static final Object UNSET = new Object();

    public static InternalThreadLocalMap getIfSet() {
        Thread thread = Thread.currentThread();
        if (thread instanceof InternalThread) {
            return ((InternalThread) thread).threadLocalMap();
        }
        return slowThreadLocalMap.get();
    }

    public static InternalThreadLocalMap get() {

        // 获取当前线程，判断是否是InternalThread，是的话调用fastGet，否则调用slowGet
        // fast和slow的快慢体现在获取 InternalThreadLocalMap 的快慢，具体快慢怎么体现的两个方法（fastGet、slowGet）进去看
        Thread thread = Thread.currentThread();
        if (thread instanceof InternalThread) {
            // 进去
            return fastGet((InternalThread) thread);
        }
        // 进去
        return slowGet();

        // 前面两个get看完之后回到这里，现在你知道了 fastGet/slowGet 这个两个方法中的快慢，指的是从两个不同的 ThreadLocal 中获取
        // InternalThreadLocalMap 的操作的快慢，而快慢的根本原因是数据结构的差异。
        // (1).InternalThread 的内部使用的是数组（特指InternalThreadLocalMap里面的indexedVariables属性），通过下标定位，非常的快。
        //      如果遇得扩容，直接搞一个扩大一倍的数组，然后copy 原数组，多余位置用指定对象填充，完事。
        // (2).而 ThreadLocal 的内部使用的是 hashCode 去获取值，多了一步计算的过程，而且用 hashCode 必然会遇到 hash 冲突的场景，
        //      ThreadLocal 还得去解决 hash 冲突，如果遇到扩容，扩容之后还得 rehash ,这可不得慢吗？

    }

    public static void remove() {
        Thread thread = Thread.currentThread();
        if (thread instanceof InternalThread) {
            ((InternalThread) thread).setThreadLocalMap(null);
        } else {
            slowThreadLocalMap.remove();
        }
    }

    public static void destroy() {
        slowThreadLocalMap = null;
    }

    public static int nextVariableIndex() {
        //  index 本质上是利用一个 AtomicInteger 赋值的

        // index 每次都是加一，对应的是 InternalThreadLocalMap 里的数组下标
        int index = NEXT_INDEX.getAndIncrement();

        // if 判断 index<0 防止溢出
        if (index < 0) {
            // 如果不把值减回去，加一的代码还在不断的被调用，那么这个 index 理论上讲是有可能又被加到正数的
            NEXT_INDEX.decrementAndGet();
            throw new IllegalStateException("Too many thread-local indexed variables");
        }
        return index;
    }

    public static int lastVariableIndex() {
        return NEXT_INDEX.get() - 1;
    }

    private InternalThreadLocalMap() {
        // 进去
        indexedVariables = newIndexedVariableTable();
    }

    public Object indexedVariable(int index) {
        Object[] lookup = indexedVariables;
        return index < lookup.length ? lookup[index] : UNSET;
    }

    /**
     * @return {@code true} if and only if a new thread-local variable has been created
     */
    public boolean setIndexedVariable(int index, Object value) {
        Object[] lookup = indexedVariables;
        // 数组容量还够，能放进去，那么可以直接设置。
        if (index < lookup.length) {
            Object oldValue = lookup[index];
            lookup[index] = value;
            return oldValue == UNSET;
            //
        } else {
            // 数组容量不够用，需要扩容后并设值，进去
            expandIndexedVariableTableAndSet(index, value);
            return true;
        }
    }

    public Object removeIndexedVariable(int index) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            Object v = lookup[index];
            lookup[index] = UNSET;
            return v;
        } else {
            return UNSET;
        }
    }

    public int size() {
        int count = 0;
        for (Object o : indexedVariables) {
            if (o != UNSET) {
                ++count;
            }
        }

        //the fist element in `indexedVariables` is a set to keep all the InternalThreadLocal to remove
        //look at method `addToVariablesToRemove`
        return count - 1;
    }

    // InternalThreadLocalMap 构造方法会调用下面的方法，可以gx
    private static Object[] newIndexedVariableTable() {
        Object[] array = new Object[32];
        // 用UNSET填充数组，表示未设值、未使用
        Arrays.fill(array, UNSET);
        return array;
        // 1.InternalThreadLocalMap 虽然名字叫做 Map ，但是它挂羊头卖狗肉，其实里面维护的是一个数组，就是indexedVariables属性。
        // 2.数组初始化大小是 32。
    }

    private static InternalThreadLocalMap fastGet(InternalThread thread) {
        // InternalThreadLocalMap 在 InternalThread 里面是一个变量维护的，可以直接通过 threadLocalMap() 获得 ，setThreadLocalMap设置
        // 都是一步到位，操作起来非常的方便。所以体现 get InternalThreadLocalMap 的操作非常 fast ！
        InternalThreadLocalMap threadLocalMap = thread.threadLocalMap();
        if (threadLocalMap == null) {
            // new InternalThreadLocalMap内部会对数组初始化，统一用UNSET填充，进去
            thread.setThreadLocalMap(threadLocalMap = new InternalThreadLocalMap());
        }
        return threadLocalMap;
    }

    private static InternalThreadLocalMap slowGet() {
        // 其实线程上绑定的数据都是放到 InternalThreadLocalMap 里面的，不管你操作什么 ThreadLocal，实际上都是操作的 InternalThreadLocalMap，
        // 看下slowThreadLocalMap就知道了（就是下面返回值）--> ThreadLocal<InternalThreadLocalMap>
        ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap = InternalThreadLocalMap.slowThreadLocalMap;

        // 用的原生ThreadLocal的get，内部为了获取InternalThreadLocalMap调用逻辑就很复杂了，比fastGet复杂多了，内部逻辑就不看了，大概如下
        // 1.计算 hash 值。2.判断通过 hash 值是否能直接获取到目标对象。3.如果没有获取到目标对象则往后遍历，直至获取成功或者循环结束。
        InternalThreadLocalMap ret = slowThreadLocalMap.get();
        if (ret == null) {
            // new InternalThreadLocalMap内部会对数组初始化，统一用UNSET填充，进去
            ret = new InternalThreadLocalMap();
            // 上面get和这里的set都是原生ThreadLocal的方法，断点进去看下set，key是slowThreadLocalMap(tl),value是ret
            slowThreadLocalMap.set(ret);
        }
        return ret;
    }

    // 在 InternalThreadLocalMap 中扩容就是变成原来大小的 2 倍。从 32 到 64，从 64 到 128 这样。扩容完成之后把原数组里面的值拷贝到新的数组里面去。
    // 然后剩下的部分用 UNSET 填充。最后把我们传进来的 value 放到指定位置上。
    private void expandIndexedVariableTableAndSet(int index, Object value) {
        Object[] oldArray = indexedVariables;
        final int oldCapacity = oldArray.length;
        int newCapacity = index;
        newCapacity |= newCapacity >>> 1;
        newCapacity |= newCapacity >>> 2;
        newCapacity |= newCapacity >>> 4;
        newCapacity |= newCapacity >>> 8;
        newCapacity |= newCapacity >>> 16;
        newCapacity++;

        Object[] newArray = Arrays.copyOf(oldArray, newCapacity);
        Arrays.fill(newArray, oldCapacity, newArray.length, UNSET);
        newArray[index] = value;
        indexedVariables = newArray;
    }
}

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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * InternalThreadLocal
 * A special variant of {@link ThreadLocal} that yields higher access performance when accessed from a
 * {@link InternalThread}.
 * <p></p>
 * Internally, a {@link InternalThread} uses a constant index in an array, instead of using hash code and hash table,
 * to look for a variable.  Although seemingly very subtle, it yields slight performance advantage over using a hash
 * table, and it is useful when accessed frequently.
 * <p></p>
 * This design is learning from {@see io.netty.util.concurrent.FastThreadLocal} which is in Netty.
 */
// OK
// InternalThreadLocal和InternalThreadLocalMap类的很多注释内容引用自:https://blog.csdn.net/qq_27243343/article/details/109159012
public class InternalThreadLocal<V> {

    private static final int VARIABLES_TO_REMOVE_INDEX = InternalThreadLocalMap.nextVariableIndex();

    private final int index;

    public InternalThreadLocal() {
        // 其实外界new InternalThreadLocal多少次，这个index就会递增到几（index最小为1），每个InternalThreadLocal有自己的index值，
        // nextVariableIndex方法内部用的就是一个类变量NEXT_INDEX.getAndIncrement()进行++操作
        index = InternalThreadLocalMap.nextVariableIndex();
    }

    /**
     * Removes all {@link InternalThreadLocal} variables bound to the current thread.  This operation is useful when you
     * are in a container environment, and you don't want to leave the thread local variables in the threads you do not
     * manage.
     *
     * 移除所有绑定到当前线程的{@link InternalThreadLocal}变量。当您在容器环境中，并且不想将线程局部变量留在不需要管理的线程中时，此操作非常有用。
     */
    @SuppressWarnings("unchecked")
    public static void removeAll() {
        // 获取线程的InternalThreadLocalMap
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return;
        }

        try {
            // 从数组中取出第 0 个位置的数据，该位置存储了该线程之前set过的所有InternalThreadLocal
            Object v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX);
            if (v != null && v != InternalThreadLocalMap.UNSET) {
                // 强转下
                Set<InternalThreadLocal<?>> variablesToRemove = (Set<InternalThreadLocal<?>>) v;
                // 转化为数组
                InternalThreadLocal<?>[] variablesToRemoveArray =
                        variablesToRemove.toArray(new InternalThreadLocal[variablesToRemove.size()]);
                // 遍历每个InternalThreadLocal
                for (InternalThreadLocal<?> tlv : variablesToRemoveArray) {
                    // remove进去
                    tlv.remove(threadLocalMap);
                }
            }
        } finally {
            // 移除线程的InternalThreadLocalMap属性，进去
            InternalThreadLocalMap.remove();
        }
    }

    /**
     * Returns the number of thread local variables bound to the current thread.
     */
    public static int size() {
        // 获取线程的InternalThreadLocalMap
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return 0;
        } else {
            // 进去
            return threadLocalMap.size();
        }
    }

    public static void destroy() {
        InternalThreadLocalMap.destroy();
    }

    // 这个方法的目的是将 InternalThreadLocal 对象保存到一个 Set 中（这个set会放在InternalThreadLocalMap内部indexedVariables数组的第一个位置），
    // 因为indexedVariables只是一个数组，没有键，所以保存到一个 Set 中，这样就可以判断是否 set 过这个 map。（原生的Thread里面的是ThreadLocalMap，有kv的，k就是ThreadLocal的引用）
    @SuppressWarnings("unchecked")
    private static void addToVariablesToRemove(InternalThreadLocalMap threadLocalMap, InternalThreadLocal<?> variable) {

        // VARIABLES_TO_REMOVE_INDEX是static final，类加载的时候第一次调用nextVariableIndex，能保证 VARIABLE_TO_REMOVE_INDEX 恒等于 0，也就是数组的第一个位置。
        // 第 0 个位置上放的是所有的 InternalThreadLocal 的集合（返回的v其实是一个Collection对象）
        Object v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX);
        Set<InternalThreadLocal<?>> variablesToRemove;

        //如果返回空则创建一个set数据结构
        if (v == InternalThreadLocalMap.UNSET || v == null) {
            // 创建一个基于 IdentityHashMap 的 Set，泛型是 InternalThreadLocal
            variablesToRemove = Collections.newSetFromMap(new IdentityHashMap<InternalThreadLocal<?>, Boolean>());
            // threadLocalMap.indexedVariables[variablesToRemoveIndex]= hashset对象
            threadLocalMap.setIndexedVariable(VARIABLES_TO_REMOVE_INDEX, variablesToRemove);
        } else {
            // 如果存在set，说明这是第二次操作了，则强转赋值给variablesToRemove
            variablesToRemove = (Set<InternalThreadLocal<?>>) v;
        }

        // 把当前对象加入到set当中去，此时的数据结构为 :
        // 当前线程.InternalThreadLocalMap.indexedVariables[variablesToRemoveIndex]=hashset(InternalThreadLocal对象1,
        // InternalThreadLocal对象2,当前对象this);
        variablesToRemove.add(variable);
    }

    //	threadLocalMap.indexedVariables[variablesToRemoveIndex]内的Set<InternalThreadLocal<?>>数据结构中移除自己
    @SuppressWarnings("unchecked")
    private static void removeFromVariablesToRemove(InternalThreadLocalMap threadLocalMap, InternalThreadLocal<?> variable) {

        //根据variablesToRemoveIndex拿出threadLocalMap的indexedVariables数组中的Set<InternalThreadLocal<?>>
        Object v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX);
        //如果返回值为null则结束
        if (v == InternalThreadLocalMap.UNSET || v == null) {
            return;
        }
        //转换为Set结构，移除自己this
        Set<InternalThreadLocal<?>> variablesToRemove = (Set<InternalThreadLocal<?>>) v;
        variablesToRemove.remove(variable);
    }

    /**
     * Returns the current value for the current thread
     */
    @SuppressWarnings("unchecked")
    public final V get() {
        // get内部使用fastGet或slowGet获取了InternalThreadLocalMap，进去
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();

        // 返回的threadLocalMap其实是线程自己的一个属性。如果是原生Thread，那么就是ThreadLocalMap threadLocals属性存储的并取得返回；
        // 如果是InternalThread，那么就是InternalThreadLocalMap threadLocalMap。前者threadLocals是一个HashMap结构存储数据的，后者是一个数组结构。
        // 注意返回的都是InternalThreadLocalMap，因为原生的也是ThreadLocal<InternalThreadLocalMap>包装的，肯定会把InternalThreadLocalMap对象存储到threadLocals

        // 下面定位index位置的值，进去
        Object v = threadLocalMap.indexedVariable(index);
        // 取出来的不是UNSET值，直接返回
        if (v != InternalThreadLocalMap.UNSET) {
            return (V) v;
        }

        // 否则初始化下，进去
        return initialize(threadLocalMap);
    }

    private V initialize(InternalThreadLocalMap threadLocalMap) {
        V v = null;
        try {
            // 这个方法可以new InternalThreadLocal的时候重写
            v = initialValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 设值
        threadLocalMap.setIndexedVariable(index, v);
        //把自己加入到threadLocalMap.indexedVariables[variablesToRemoveIndex]=hashSet(this)当中去,进去
        addToVariablesToRemove(threadLocalMap, this);
        return v;
    }

    /**
     * Sets the value for the current thread.
     */
    public final void set(V value) {
        // 第一个判断好理解，第二个判断表示：如果 set 的对象是 UNSET，我们可以认为是需要把当前位置上的值替换为 UNSET，也就是 remove
        // UNSET是InternalThreadLocalMap的属性，去看下UNSET
        if (value == null || value == InternalThreadLocalMap.UNSET) {
            remove();
        } else {
            // 我们知道原生ThreadLocal本质是获取Thread里面有一个ThreadLocalMap成员，这里带InternalXX的还是这种思想，get进去
            InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
            // 上面get内部使用fastGet和slowGet获取了InternalThreadLocalMap，接下来就是设值，将index位置设值为用户传入的值，我们先看下
            // index属性，然后setIndexedVariable方法进去看下
            if (threadLocalMap.setIndexedVariable(index, value)) {
                // 进去
                addToVariablesToRemove(threadLocalMap, this);
            }
        }
    }

    /**
     * Sets the value to uninitialized; a proceeding call to get() will trigger a call to initialValue().
     */
    @SuppressWarnings("unchecked")
    public final void remove() {
        remove(InternalThreadLocalMap.getIfSet());
    }

    /**
     * Sets the value to uninitialized for the specified thread local map;
     * a proceeding call to get() will trigger a call to initialValue().
     * The specified thread local map must be for the current thread.
     */
    @SuppressWarnings("unchecked")
    public final void remove(InternalThreadLocalMap threadLocalMap) {
        if (threadLocalMap == null) {
            return;
        }

        // 获取当前线程最后一次itl.set的值（itl指的是InternalThreadLocal）
        Object v = threadLocalMap.removeIndexedVariable(index);
        // 移除当前InternalThreadLocal，进去
        removeFromVariablesToRemove(threadLocalMap, this);

        if (v != InternalThreadLocalMap.UNSET) {
            try {
                // 空实现，类似于模板方法的钩子，在new InternalThreadLocal(){可以重写}，详见testOnRemove测试方法
                onRemoval((V) v);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Returns the initial value for this thread-local variable.
     */
    protected V initialValue() throws Exception {
        return null;
    }

    /**
     * Invoked when this thread local variable is removed by {@link #remove()}.
     */
    protected void onRemoval(@SuppressWarnings("unused") V value) throws Exception {
    }
}

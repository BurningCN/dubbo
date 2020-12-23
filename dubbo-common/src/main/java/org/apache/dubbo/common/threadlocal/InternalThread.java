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

/**
 * InternalThread
 */
// OK
// 内部使用的线程，都在内置的四种线程池用到了（就是 FixedThreadPool、limited....）
public class InternalThread extends Thread {

    private InternalThreadLocalMap threadLocalMap;

    public InternalThread() {
    }

    // 不同参数的构造，实际都传给Thread了
    public InternalThread(Runnable target) {
        super(target);
    }

    public InternalThread(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    public InternalThread(String name) {
        super(name);
    }

    public InternalThread(ThreadGroup group, String name) {
        super(group, name);
    }

    public InternalThread(Runnable target, String name) {
        super(target, name);
    }

    public InternalThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    public InternalThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
    }

    /**
     * Returns the internal data structure that keeps the threadLocal variables bound to this thread.
     * Note that this method is for internal use only, and thus is subject to change at any time.
     *
     * 返回保持threadLocal变量绑定到该线程的内部数据结构。
     * 请注意，此方法仅供内部使用，因此可能在任何时候进行更改。
     */

    public final InternalThreadLocalMap threadLocalMap() {
        return threadLocalMap;
    }

    /**
     * Sets the internal data structure that keeps the threadLocal variables bound to this thread.
     * Note that this method is for internal use only, and thus is subject to change at any time.
     *
     * 设置保持threadLocal变量绑定到此线程的内部数据结构。
     * 请注意，此方法仅供内部使用，因此可能在任何时候进行更改。
     */
    public final void setThreadLocalMap(InternalThreadLocalMap threadLocalMap) {
        this.threadLocalMap = threadLocalMap;
    }
}

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

import org.apache.dubbo.common.utils.NamedThreadFactory;

/**
 * NamedInternalThreadFactory
 * This is a threadFactory which produce {@link InternalThread}
 */
// OK
// 看上面注释，重点在 Internal
public class NamedInternalThreadFactory extends NamedThreadFactory {

    public NamedInternalThreadFactory() {
        super();
    }

    public NamedInternalThreadFactory(String prefix) {
        super(prefix, false);
    }

    // gx prefix一般是在url中取到的
    public NamedInternalThreadFactory(String prefix, boolean daemon) {
        // 进去
        super(prefix, daemon);
    }

    // 重写了父类方法，方法内代码除了第二行不一样，其他全一样，第二行就是突出使用InternalThread（类本身extents thread）
    @Override
    public Thread newThread(Runnable runnable) {
        // 能直接使用父类的成员，一猜就知道这些成员是protected修饰的
        String name = mPrefix + mThreadNum.getAndIncrement();
        // 创建线程的时候不一定只能指定runnable、name还可以指定线程组、栈大小，进去
        InternalThread ret = new InternalThread(mGroup, runnable, name, 0);
        ret.setDaemon(mDaemon);
        return ret;
    }
}

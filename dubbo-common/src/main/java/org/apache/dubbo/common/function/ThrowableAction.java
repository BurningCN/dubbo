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
package org.apache.dubbo.common.function;

import java.util.function.Function;

/**
 * A function interface for action with {@link Throwable}
 *
 * @see Function
 * @see Throwable
 * @since 2.7.5
 */
// OK
// 这是一个函数式接口
@FunctionalInterface
public interface ThrowableAction {

    /**
     * Executes the action
     *
     * @throws Throwable if met with error
     */
    // 不接受参数和无返回值
    void execute() throws Throwable;

    /**
     * Executes {@link ThrowableAction}
     *
     * @param action {@link ThrowableAction}
     * @throws RuntimeException wrap {@link Exception} to {@link RuntimeException}
     */
    // 一般外界调用这个方法，其实就是用户利用传入一个lambda然后执行，lambda的内容可能会抛出异常（类的名字就叫ThrowableAction），
    // 但是会自动捕获并转化为RuntimeException再次抛出
    // 1.8的接口可以有方法实现（default或者static，前者只能对象调用，后者可以外界直接类.方式调用）
    static void execute(ThrowableAction action) throws RuntimeException {
        try {
            // 这是lambda的触发点、本质，实际还是函数调用！
            action.execute();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

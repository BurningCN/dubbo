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
package my.common.extension;

import my.common.lang.Prioritized;

// OK
// 加载策略接口，主要是定义从哪些文件目录加载类，以及目录的优先级。共有三个实现类去看下，很简单
// 看下父接口
public interface LoadingStrategy extends Prioritized {

    // 扫描的目录
    String directory();

    // 1.8接口支持，默认方法
    default boolean preferExtensionClassLoader() {
        return false;
    }

    default String[] excludedPackages() {
        return null;
    }

    /**
     * Indicates current {@link LoadingStrategy} supports overriding other lower prioritized instances or not.
     * 表示当前{@link LoadingStrategy}是否支持覆盖其他低优先级实例。
     *
     * @return if supports, return <code>true</code>, or <code>false</code>
     * @since 2.7.7
     */
    // 除了DubboInternalLoadingStrategy，另两个都是return true。表示可以覆盖低优先级的LoadingStrategy实例
    default boolean overridden() {
        return false;
    }
}

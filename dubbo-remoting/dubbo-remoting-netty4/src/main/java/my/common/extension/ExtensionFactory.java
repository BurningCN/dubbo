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

/**
 * ExtensionFactory
 */
// OK
// ExtensionFactory的作用就类似spring框架中的IOC的作用，正是因为JDK的SPI机制比较简单，所以duboo框架才重写了SPI机制，并实现了IOC和AOP的功能。
// IOC功能的代码出现在ExtensionLoader的 injectExtension方法里面
@SPI
public interface ExtensionFactory {

    /**
     * Get extension.
     *
     * @param type object type.
     * @param name object name.
     * @return object instance.
     */
    // 泛型方法
    <T> T getExtension(Class<T> type, String name);

}

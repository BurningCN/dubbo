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
package org.apache.dubbo.common;

/**
 * Node. (API/SPI, Prototype, ThreadSafe)
 */
// Node 这个接口继承者比较多，像 Registry、Monitor、Invoker、Directory
    // 这个接口包含了一个获取配置信息的方法 getUrl，实现该接口的类可以向外提供配置信息
public interface Node {

    /**
     * get url.
     *
     * @return url.
     */
    // 获取节点地址
    URL getUrl();

    /**
     * is available.
     *
     * @return available.
     */
    // 节点是否可用
    boolean isAvailable();

    /**
     * destroy.
     */
    // 销毁节点
    void destroy();

}
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
package org.apache.dubbo.rpc;

/**
 * Exporter. (API/SPI, Prototype, ThreadSafe)
 *
 * @see org.apache.dubbo.rpc.Protocol#export(Invoker)
 * @see org.apache.dubbo.rpc.ExporterListener
 * @see org.apache.dubbo.rpc.protocol.AbstractExporter
 */
// OK
// 结合抽象子类和两个具体子类。明白这两个方法大概的实现过程是这样的：别处通过 new 具体实例 传入invoker、url、exporterMap，然后具体实例第一步调用
// super(invoker)，这样invoker就保存到了AbstractExporter，getInvoker就取到值了。具体实例的unexport触发后，将自己从exporterMap移除
// 并调用super()的unexport方法，内部调用invoket.destroy方法
public interface Exporter<T> {

    /**
     * get invoker.
     *
     * @return invoker
     */
    Invoker<T> getInvoker();

    /**
     * unexport.
     * <p>
     * <code>
     * getInvoker().destroy(); < ---- 注意
     * </code>
     */
    // 可以先进ListenerExporterWrapper，其是包装类
    void unexport();

}
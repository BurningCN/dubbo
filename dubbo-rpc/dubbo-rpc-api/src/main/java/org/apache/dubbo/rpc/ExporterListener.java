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

import org.apache.dubbo.common.extension.SPI;

/**
 * ExporterListener. (SPI, Singleton, ThreadSafe)
 */
// OK
// 这个类的作用主要是监听exporter发生exported或者unexported之后进行怎么样的后置处理动作。可以看两个方法上面的注释就懂了。
// 不过目前没有具体实现类只有一个抽象类ExporterListenerAdapter。如果有需求，你们可以自定义具体类，继承ExporterListenerAdapter
// 和ExporterListener作用差不多，只不过一个监控Exporter的exported和unexported事件，一个监控Invoker的referred和destroyed
@SPI
public interface ExporterListener {

    /**
     * The exporter exported.
     *
     * @param exporter
     * @throws RpcException
     * @see org.apache.dubbo.rpc.Protocol#export(Invoker)
     */
    void exported(Exporter<?> exporter) throws RpcException;

    /**
     * The exporter unexported.
     *
     * @param exporter
     * @throws RpcException
     * @see org.apache.dubbo.rpc.Exporter#unexport()
     */
    void unexported(Exporter<?> exporter);

}
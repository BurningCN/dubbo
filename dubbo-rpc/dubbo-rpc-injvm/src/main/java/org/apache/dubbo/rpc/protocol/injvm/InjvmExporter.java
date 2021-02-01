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
package org.apache.dubbo.rpc.protocol.injvm;

import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.protocol.AbstractExporter;

import java.util.Map;

/**
 * InjvmExporter
 */
// OK
class InjvmExporter<T> extends AbstractExporter<T> {

    private final String key;

    private final Map<String, Exporter<?>> exporterMap;

    // gx 一般会调用下面的方法创建InjvmExporter实例，但是不知道类的exporterMap属性和传进来的exporterMap参数是干嘛的？
    // 难道是每个实例内部通过这个map能知道/记录着对应协议（这里就是InjvmProtocol）暴露的所有exporter？----> 应该是这样的
    InjvmExporter(Invoker<T> invoker, String key, Map<String, Exporter<?>> exporterMap) {
        super(invoker);  // 进去，把invoker实例保存到父类的属性
        this.key = key;
        this.exporterMap = exporterMap;
        exporterMap.put(key, this);// 给参数map添加一个entry，将this填进去
    }

    @Override
    public void unexport() {
        super.unexport();// 调用父类的
        // 从内存移除，前面说过，map能知道/记录着对应协议（这里就是InjvmProtocol）暴露的所有exporter。所以这里的操作就是把自己/this移除（通过key移除，key一般是接口全限定名）
        exporterMap.remove(key);
    }

}

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
package org.apache.dubbo.rpc.listener;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.ExporterListener;
import org.apache.dubbo.rpc.Invoker;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * ListenerExporter
 */
// OK
public class ListenerExporterWrapper<T> implements Exporter<T> {

    private static final Logger logger = LoggerFactory.getLogger(ListenerExporterWrapper.class);

    private final Exporter<T> exporter;

    private final List<ExporterListener> listeners;


    // gx
    // 下面方法不会因为spi机制自动包装其他Exporter实例而得到调用what，因为下面的方法不是拷贝构造函数(去看isWrapperClass方法)，该方法需要手动调用，gx
    public ListenerExporterWrapper(Exporter<T> exporter, List<ExporterListener> listeners) {
        if (exporter == null) {
            throw new IllegalArgumentException("exporter == null");
        }
        this.exporter = exporter;
        this.listeners = listeners;
        if (CollectionUtils.isNotEmpty(listeners)) {
            RuntimeException exception = null;
            for (ExporterListener listener : listeners) {
                if (listener != null) {
                    try {
                        // exporter代表暴露[完毕]返回的对象，如果有监听器，挨个调用他们的处理逻辑
                        listener.exported(this);
                    } catch (RuntimeException t) {
                        logger.error(t.getMessage(), t);
                        exception = t;
                    }
                }
            }
            if (exception != null) {
                // 前面先把异常记录到日志，然后这里再抛出。
                throw exception;
            }
        }
    }

    @Override
    public Invoker<T> getInvoker() {
        return exporter.getInvoker();
    }

    @Override
    public void unexport() {
        try {
            // ListenerExporterWrapper本身是一个Wrapper，所以本质还需要调用exporter.unexport(); 这个exporter可能是InjvmExporter或者DubboExporter
            exporter.unexport();
        } finally {
            if (CollectionUtils.isNotEmpty(listeners)) {
                RuntimeException exception = null;
                for (ExporterListener listener : listeners) {
                    if (listener != null) {
                        try {
                            // 和前面ListenerExporterWrapper内部类似......通知监听器exporter发生了unexport动作
                            listener.unexported(this);
                        } catch (RuntimeException t) {
                            logger.error(t.getMessage(), t);
                            exception = t;
                        }
                    }
                }
                if (exception != null) {
                    throw exception;
                }
            }
        }
    }

}

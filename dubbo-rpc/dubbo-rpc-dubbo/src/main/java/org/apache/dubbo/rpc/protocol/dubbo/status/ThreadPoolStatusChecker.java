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
package org.apache.dubbo.rpc.protocol.dubbo.status;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.status.Status;
import org.apache.dubbo.common.status.StatusChecker;
import org.apache.dubbo.common.store.DataStore;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ThreadPoolStatusChecker
 */
// OK
@Activate
public class ThreadPoolStatusChecker implements StatusChecker {

    @Override
    public Status check() {
        DataStore dataStore = ExtensionLoader.getExtensionLoader(DataStore.class).getDefaultExtension();
        Map<String, Object> executors = dataStore.get(CommonConstants.EXECUTOR_SERVICE_COMPONENT_KEY);

        StringBuilder msg = new StringBuilder();
        Status.Level level = Status.Level.OK;
        for (Map.Entry<String, Object> entry : executors.entrySet()) {
            String port = entry.getKey();// 端口
            ExecutorService executor = (ExecutorService) entry.getValue(); // 线程池   --- > 一个端口一个线程池

            if (executor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor tp = (ThreadPoolExecutor) executor;
                boolean ok = tp.getActiveCount() < tp.getMaximumPoolSize() - 1; //  getActiveCount 活跃的线程数
                Status.Level lvl = Status.Level.OK;
                if (!ok) {
                    level = Status.Level.WARN;
                    lvl = Status.Level.WARN;
                }

                if (msg.length() > 0) {
                    msg.append(";");
                }
                // 几个线程池api注意下
                // maximumPoolSize:是一个静态变量,在变量初始化的时候,有构造函数指定.
                // largestPoolSize: 是一个动态变量,是记录Poll曾经达到的最高值,也就是 largestPoolSize<= maximumPoolSize.
                msg.append("Pool status:").append(lvl).append(", max:").append(tp.getMaximumPoolSize()).append(", core:")
                        .append(tp.getCorePoolSize()).append(", largest:").append(tp.getLargestPoolSize()).append(", active:")
                        .append(tp.getActiveCount()).append(", task:").append(tp.getTaskCount()).append(", service port: ").append(port);
            }
        }
        return msg.length() == 0 ? new Status(Status.Level.UNKNOWN) : new Status(level, msg.toString());
    }

}

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
package org.apache.dubbo.rpc.cluster.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.apache.dubbo.rpc.cluster.support.wrapper.AbstractCluster;
import org.apache.dubbo.rpc.support.MockInvoker;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link FailoverClusterInvoker}
 */
public class FailoverCluster extends AbstractCluster {

    public final static String NAME = "failover";

    @Override
    public <T> AbstractClusterInvoker<T> doJoin(Directory<T> directory) throws RpcException {
        // 创建并返回 FailoverClusterInvoker 对象
        return new FailoverClusterInvoker<>(directory);
    }

    public static void main(String[] args) {
        List<Invoker<FailoverCluster>> invokers = new ArrayList<>();
        URL url = URL.valueOf("test://test:12/test?cluster=zone-aware");
        invokers.add(new MockInvoker<>(url,FailoverCluster.class));
        StaticDirectory<FailoverCluster> staticDirectory = new StaticDirectory(url,invokers);
        FailoverCluster failoverCluster = new FailoverCluster();
        Invoker<FailoverCluster> join = failoverCluster.join(staticDirectory);
        RpcInvocation rpcInvocation = new RpcInvocation();
        join.invoke(rpcInvocation);
    }
}

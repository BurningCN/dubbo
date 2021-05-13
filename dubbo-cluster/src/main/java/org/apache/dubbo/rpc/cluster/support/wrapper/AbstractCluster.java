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
package org.apache.dubbo.rpc.cluster.support.wrapper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.cluster.interceptor.ClusterInterceptor;
import org.apache.dubbo.rpc.cluster.support.AbstractClusterInvoker;

import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.REFERENCE_INTERCEPTOR_KEY;

// OK
public abstract class AbstractCluster implements Cluster {

    private <T> Invoker<T> buildClusterInterceptors(AbstractClusterInvoker<T> clusterInvoker, String key) {
        AbstractClusterInvoker<T> last = clusterInvoker;
        List<ClusterInterceptor> interceptors = ExtensionLoader.getExtensionLoader(ClusterInterceptor.class).getActivateExtension(clusterInvoker.getUrl(), key);
        // 根据需要包装ClusterInvoker, 使用切面的方式进行拦截器接入，按先后依次强入拦截器.
        // 已有的两个拦截器都是给消费端使用的，默认会返回 ConsumerContextClusterInterceptor
        if (!interceptors.isEmpty()) {
            // 从后往前遍历
            for (int i = interceptors.size() - 1; i >= 0; i--) {
                final ClusterInterceptor interceptor = interceptors.get(i);
                final AbstractClusterInvoker<T> next = last;
                // 使用内部类进行包装拦截器。每个InterceptorInvokerNode的第一个参数都是同一个，这个是目标invoker，之所以需要这个是因为
                // InterceptorInvokerNode本身是继承AbstractClusterInvoker，实现的方法需要直接委托第一个参数

                // 先后顺序如: beforeC -> beforeB -> beforeA (spring中还有Around) -> afterA -> afterB -> afterC (spring中还有afterReturn)
                last = new InterceptorInvokerNode<>(clusterInvoker, interceptor, next);
            }
        }
        //last = {AbstractCluster$InterceptorInvokerNode@6331} "interface samples.servicediscovery.demo.DemoService -> dubbo://30.25.58.166/samples.servicediscovery.demo.DemoService?application=demo-consumer&check=false&dubbo=2.0.2&init=false&interface=samples.servicediscovery.demo.DemoService&mapping-type=metadata&mapping.type=metadata&metadata-type=remote&methods=sayHello&pid=42737&provided-by=demo-provider&register.ip=30.25.58.166&side=consumer&sticky=false&timestamp=1620452275520"
        // clusterInvoker = {FailoverClusterInvoker@6330} "interface samples.servicediscovery.demo.DemoService -> dubbo://30.25.58.166/samples.servicediscovery.demo.DemoService?application=demo-consumer&check=false&dubbo=2.0.2&init=false&interface=samples.servicediscovery.demo.DemoService&mapping-type=metadata&mapping.type=metadata&metadata-type=remote&methods=sayHello&pid=42737&provided-by=demo-provider&register.ip=30.25.58.166&side=consumer&sticky=false&timestamp=1620452275520"
        // interceptor = {ConsumerContextClusterInterceptor@6288}
        // next = {FailoverClusterInvoker@6330} "interface samples.servicediscovery.demo.DemoService -> dubbo://30.25.58.166/samples.servicediscovery.demo.DemoService?application=demo-consumer&check=false&dubbo=2.0.2&init=false&interface=samples.servicediscovery.demo.DemoService&mapping-type=metadata&mapping.type=metadata&metadata-type=remote&methods=sayHello&pid=42737&provided-by=demo-provider&register.ip=30.25.58.166&side=consumer&sticky=false&timestamp=1620452275520"
        // this$0 = {FailoverCluster@6265}
        // directory = null
        // availablecheck = false
        // destroyed = {AtomicBoolean@6333} "false"
        // stickyInvoker = null
        return last;
    }

    @Override
    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
        // ClusterInvoker 调用入口, 让具体策略实现 doJoin(), 并在其基础上进行包装拦截器, 依据来源 reference.interceptor=xxx
        return buildClusterInterceptors(doJoin(directory), directory.getUrl().getParameter(REFERENCE_INTERCEPTOR_KEY));
    }

    protected abstract <T> AbstractClusterInvoker<T> doJoin(Directory<T> directory) throws RpcException;

    protected class InterceptorInvokerNode<T> extends AbstractClusterInvoker<T> {

        private AbstractClusterInvoker<T> clusterInvoker;
        private ClusterInterceptor interceptor;
        private AbstractClusterInvoker<T> next;

        public InterceptorInvokerNode(AbstractClusterInvoker<T> clusterInvoker,
                                      ClusterInterceptor interceptor,
                                      AbstractClusterInvoker<T> next) {
            this.clusterInvoker = clusterInvoker;
            this.interceptor = interceptor;
            this.next = next;
        }

        @Override
        public Class<T> getInterface() {
            return clusterInvoker.getInterface();
        }

        @Override
        public URL getUrl() {
            return clusterInvoker.getUrl();
        }

        @Override
        public boolean isAvailable() {
            return clusterInvoker.isAvailable();
        }

        // 假设顺序是 ConsumerContextClusterInterceptor -> ZoneAwareClusterInterceptor -> FailbackClusterInvoker
        // 记作abc，外界拿到AbstractClusterInvoker调用invoke的时候(记作a.invoke)，走如下代码，然后b.before，然后b.intercept
        // ->c.invoke->b.after->a.after

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            Result asyncResult;
            try {
                // 拦截器的具体处理逻辑
                // 有个 intercept() 的默认方法，其为调用 clusterInvoker.invoke(invocation);  从而实现链式调用
                interceptor.before(next, invocation);
                asyncResult = interceptor.intercept(next, invocation);
            } catch (Exception e) {
                // onError callback
                if (interceptor instanceof ClusterInterceptor.Listener) {
                    ClusterInterceptor.Listener listener = (ClusterInterceptor.Listener) interceptor;
                    listener.onError(e, clusterInvoker, invocation);
                }
                throw e;
            } finally {
                interceptor.after(next, invocation);
            }
            // 注意传入的BiConsumer，注意这里是直接调用了whenCompleteWithContext方法，biConsumer的调用时机在AsyncRPCResult
            return asyncResult.whenCompleteWithContext((r, t) -> {
                // onResponse callback
                if (interceptor instanceof ClusterInterceptor.Listener) {
                    ClusterInterceptor.Listener listener = (ClusterInterceptor.Listener) interceptor;
                    if (t == null) {
                        listener.onMessage(r, clusterInvoker, invocation);
                    } else {
                        listener.onError(t, clusterInvoker, invocation);
                    }
                }
            });
        }

        @Override
        public void destroy() {
            clusterInvoker.destroy();
        }

        @Override
        public String toString() {
            return clusterInvoker.toString();
        }

        @Override
        protected Result doInvoke(Invocation invocation, List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
            // The only purpose is to build a interceptor chain, so the cluster related logic doesn't matter.
            // 唯一的目的是建立一个拦截链，因此集群相关的逻辑无关紧要。
            return null;
        }
    }
}

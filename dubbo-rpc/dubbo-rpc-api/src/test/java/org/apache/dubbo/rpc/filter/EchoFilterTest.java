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
package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.support.DemoService;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// OK
public class EchoFilterTest {

    Filter echoFilter = new EchoFilter();

    @SuppressWarnings("unchecked")
    @Test
    public void testEcho() {
        Invocation invocation = mock(Invocation.class);
        given(invocation.getMethodName()).willReturn("$echo");
        given(invocation.getParameterTypes()).willReturn(new Class<?>[]{Enum.class});
        given(invocation.getArguments()).willReturn(new Object[]{"hello"});
        given(invocation.getObjectAttachments()).willReturn(null);

        Invoker<DemoService> invoker = mock(Invoker.class);
        given(invoker.isAvailable()).willReturn(true);
        given(invoker.getInterface()).willReturn(DemoService.class);
        AppResponse result = new AppResponse();
        result.setValue("High");
        // 很多given(x.y).willReturn(z)的操作的意思就是在发起对x.y的方法调用的时候返回z（下面的echoFilter.invoke内部就有这个x.y调用）
        given(invoker.invoke(invocation)).willReturn(result);
        URL url = URL.valueOf("test://test:11/test?group=dubbo&version=1.1");
        given(invoker.getUrl()).willReturn(url);

        // 进去
        Result filterResult = echoFilter.invoke(invoker, invocation);
        // 输出hello，而不是前面的High，这是因为echoFilter.invoke走得是if那个分支，如果走得if之后的逻辑，即return invoker.invoke(inv);
        // 肯定返回High了，getValue进去
        assertEquals("hello", filterResult.getValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNonEcho() {
        // EchoFilter只识别$echo方法

        Invocation invocation = mock(Invocation.class);
        given(invocation.getMethodName()).willReturn("echo");
        given(invocation.getParameterTypes()).willReturn(new Class<?>[]{Enum.class});
        given(invocation.getArguments()).willReturn(new Object[]{"hello"});
        given(invocation.getObjectAttachments()).willReturn(null);

        Invoker<DemoService> invoker = mock(Invoker.class);
        given(invoker.isAvailable()).willReturn(true);
        given(invoker.getInterface()).willReturn(DemoService.class);
        AppResponse result = new AppResponse();
        result.setValue("High");
        given(invoker.invoke(invocation)).willReturn(result);
        URL url = URL.valueOf("test://test:11/test?group=dubbo&version=1.1");
        given(invoker.getUrl()).willReturn(url);

        Result filterResult = echoFilter.invoke(invoker, invocation);
        // 输出的High，而不是hello，因为 echoFilter.invoke(走得是if外的逻辑
        assertEquals("High", filterResult.getValue());
    }
}
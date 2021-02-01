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
package org.apache.dubbo.rpc.cluster.configurator.absent;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.rpc.cluster.configurator.consts.UrlConstant;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * todo need pr 下面的注释不对
 * OverrideConfiguratorTest
 */
// OK
// 注意是Absent，不是Abstract
public class AbsentConfiguratorTest {


    // 覆盖Application
    @Test
    public void testOverrideApplication() {
        // 如下直接new了，一般不会这样，而是通过AbsentConfigurator对应的AbsentConfiguratorFactory工厂创建的，当然也不是直接拿到工厂就..了
        // 而是先获取ConfiguratorFactory的自适应扩展类，根据url取出对应的工厂，详见Configurator#toConfigurators
        // 进去
        AbsentConfigurator configurator = new AbsentConfigurator(URL.valueOf("override://foo@0.0.0.0/com.foo.BarService?timeout=200"));

        // 下面这个操作就是用configurator内部的url来覆盖/填充部分参数 给传configure方法的url，进去
        URL url = configurator.configure(URL.valueOf(UrlConstant.URL_CONSUMER));
        // configure前：dubbo://10.20.153.10:20880/com.foo.BarService?application=foo&side=consumer
        // configure后：dubbo://10.20.153.10:20880/com.foo.BarService?application=foo&side=consumer&timeout=200

        Assertions.assertEquals("200", url.getParameter("timeout"));

        url = configurator.configure(URL.valueOf(UrlConstant.URL_ONE));
        // configure前：dubbo://10.20.153.10:20880/com.foo.BarService?application=foo&timeout=1000&side=consumer
        // configure后：dubbo://10.20.153.10:20880/com.foo.BarService?application=foo&side=consumer&timeout=1000
        Assertions.assertEquals("1000", url.getParameter("timeout"));

        url = configurator.configure(URL.valueOf(UrlConstant.APPLICATION_BAR_SIDE_CONSUMER_11));
        // configure前：dubbo://10.20.153.11:20880/com.foo.BarService?application=bar&side=consumer
        // configure后：dubbo://10.20.153.11:20880/com.foo.BarService?application=bar&side=consumer
        Assertions.assertNull(url.getParameter("timeout"));

        url = configurator.configure(URL.valueOf(UrlConstant.TIMEOUT_1000_SIDE_CONSUMER_11));
        // configure前：dubbo://10.20.153.11:20880/com.foo.BarService?application=bar&timeout=1000&side=consumer
        // configure后：dubbo://10.20.153.11:20880/com.foo.BarService?application=bar&side=consumer&timeout=1000
        Assertions.assertEquals("1000", url.getParameter("timeout"));
    }

    @Test
    public void testOverrideHost() {
        AbsentConfigurator configurator = new AbsentConfigurator(URL.valueOf("override://" + NetUtils.getLocalHost() + "/com.foo.BarService?timeout=200"));

        URL url = configurator.configure(URL.valueOf(UrlConstant.URL_CONSUMER));
        // configure前：dubbo://10.20.153.10:20880/com.foo.BarService?application=foo&side=consumer
        // configure后：dubbo://10.20.153.10:20880/com.foo.BarService?application=foo&side=consumer&timeout=200

        Assertions.assertEquals("200", url.getParameter("timeout"));

        url = configurator.configure(URL.valueOf(UrlConstant.URL_ONE));
        // configure前：dubbo://10.20.153.10:20880/com.foo.BarService?application=foo&timeout=1000&side=consumer
        // configure后：dubbo://10.20.153.10:20880/com.foo.BarService?application=foo&side=consumer&timeout=1000
        Assertions.assertEquals("1000", url.getParameter("timeout"));

        AbsentConfigurator configurator1 = new AbsentConfigurator(URL.valueOf(UrlConstant.SERVICE_TIMEOUT_200));

        url = configurator1.configure(URL.valueOf(UrlConstant.APPLICATION_BAR_SIDE_CONSUMER_10));
        // configure前：dubbo://10.20.153.10:20880/com.foo.BarService?application=bar&side=consumer
        // configure后：dubbo://10.20.153.10:20880/com.foo.BarService?application=bar&side=consumer
        Assertions.assertNull(url.getParameter("timeout"));

        url = configurator1.configure(URL.valueOf(UrlConstant.TIMEOUT_1000_SIDE_CONSUMER_10));
        // configure前：dubbo://10.20.153.10:20880/com.foo.BarService?application=bar&timeout=1000&side=consumer
        // configure后：dubbo://10.20.153.10:20880/com.foo.BarService?application=bar&side=consumer&timeout=1000
        Assertions.assertEquals("1000", url.getParameter("timeout"));
    }

}

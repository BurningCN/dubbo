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
package org.apache.dubbo.metadata.report.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2018/9/14
 */
public class AbstractMetadataReportFactoryTest {

    private AbstractMetadataReportFactory metadataReportFactory = new AbstractMetadataReportFactory() {
        @Override
        protected MetadataReport createMetadataReport(URL url) {
            return new MetadataReport() {

                @Override
                public void storeProviderMetadata(MetadataIdentifier providerMetadataIdentifier, ServiceDefinition serviceDefinition) {
                    store.put(providerMetadataIdentifier.getIdentifierKey(), JSON.toJSONString(serviceDefinition));
                }

                @Override
                public String getServiceDefinition(MetadataIdentifier metadataIdentifier) {
                    return null;
                }

                @Override
                public void storeConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, Map<String, String> serviceParameterMap) {

                }

                @Override
                public List<String> getExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
                    return null;
                }

                @Override
                public void saveServiceMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {

                }

                @Override
                public void removeServiceMetadata(ServiceMetadataIdentifier metadataIdentifier) {

                }

                @Override
                public void saveSubscribedData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, Set<String> urls) {

                }

                @Override
                public List<String> getSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier) {
                    return null;
                }

                Map<String, String> store = new ConcurrentHashMap<>();


            };
        }
    };

    // 下面主要测试缓存，两个url取出来的MetadataReport是否相同，取决于作为SERVICE_STORE_MAP缓存的key，即url.toServiceString()值是否一致
    @Test
    public void testGetOneMetadataReport() {
        URL url = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        MetadataReport metadataReport1 = metadataReportFactory.getMetadataReport(url);
        MetadataReport metadataReport2 = metadataReportFactory.getMetadataReport(url);
        Assertions.assertEquals(metadataReport1, metadataReport2);
    }

    @Test
    public void testGetOneMetadataReportForIpFormat() {
        String hostName = NetUtils.getLocalAddress().getHostName();
        String ip = NetUtils.getIpByHost(hostName);
        URL url1 = URL.valueOf("zookeeper://" + hostName + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        URL url2 = URL.valueOf("zookeeper://" + ip + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        // 实际上面的url1和url2是一样的，所以下面能取出一样的
        MetadataReport metadataReport1 = metadataReportFactory.getMetadataReport(url1);
        MetadataReport metadataReport2 = metadataReportFactory.getMetadataReport(url2);
        Assertions.assertEquals(metadataReport1, metadataReport2);
    }

    @Test
    public void testGetForDiffService() {
        URL url1 = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService1?version=1.0.0&application=vic");
        URL url2 = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService2?version=1.0.0&application=vic");
        // path不同无所谓，因为内部第一步就把url的path替换为MetadataReport.class.getName了
        MetadataReport metadataReport1 = metadataReportFactory.getMetadataReport(url1);
        MetadataReport metadataReport2 = metadataReportFactory.getMetadataReport(url2);
        Assertions.assertEquals(metadataReport1, metadataReport2);
    }

    @Test
    public void testGetForDiffGroup() {
        URL url1 = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic&group=aaa");
        URL url2 = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic&group=bbb");
        // url.toServiceString()的值分别为，注意url.toServiceString()内部拼path拼的是(满足useService=true)getServiceKey()（group、path、version）
        // zookeeper://30.25.58.119:4444/aaa/my.metadata.api.report.MetadataReport:1.0.0
        //zookeeper://30.25.58.119:4444/bbb/my.metadata.api.report.MetadataReport:1.0.0
        MetadataReport metadataReport1 = metadataReportFactory.getMetadataReport(url1);
        MetadataReport metadataReport2 = metadataReportFactory.getMetadataReport(url2);
        Assertions.assertNotEquals(metadataReport1, metadataReport2);
    }
}

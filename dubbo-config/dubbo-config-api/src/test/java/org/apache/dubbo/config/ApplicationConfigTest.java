/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dubbo.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.DUMP_DIRECTORY;
import static org.apache.dubbo.common.constants.QosConstants.ACCEPT_FOREIGN_IP;
import static org.apache.dubbo.common.constants.QosConstants.QOS_ENABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

// OK
// 对应<dubbo:application>标签，可以配置的属性在dubbo.xsd说明了，全局搜<xsd:complexType name="applicationType">也可以
public class ApplicationConfigTest {
    @Test
    public void testName() throws Exception {
        ApplicationConfig application = new ApplicationConfig();
        // 进去(内部name=id，id可以通过setId赋值)
        application.setName("app");
        assertThat(application.getName(), equalTo("app"));

        application = new ApplicationConfig("app2");
        assertThat(application.getName(), equalTo("app2"));

        Map<String, String> parameters = new HashMap<String, String>();
        // 进去
        ApplicationConfig.appendParameters(parameters, application);
        // parameters相当于是一个值-结果参数，拿到ApplicationConfig里面的属性值
        // application -> app2
        assertThat(parameters, hasEntry(APPLICATION_KEY, "app2"));
    }



    @Test
    public void testVersion() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setVersion("1.0.0");
        assertThat(application.getVersion(), equalTo("1.0.0"));
        Map<String, String> parameters = new HashMap<String, String>();
        ApplicationConfig.appendParameters(parameters, application);
        // "application.version" -> "1.0.0"
        assertThat(parameters, hasEntry("application.version", "1.0.0"));
        System.out.println(application);
    }
    // easy
    @Test
    public void testOwner() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setOwner("owner");
        assertThat(application.getOwner(), equalTo("owner"));
    }

    // easy
    @Test
    public void testOrganization() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setOrganization("org");
        assertThat(application.getOrganization(), equalTo("org"));
    }

    // easy
    @Test
    public void testArchitecture() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setArchitecture("arch");
        assertThat(application.getArchitecture(), equalTo("arch"));
    }

    @Test
    public void testEnvironment1() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        // 设置环境，进去
        application.setEnvironment("develop");
        assertThat(application.getEnvironment(), equalTo("develop"));

        application.setEnvironment("test");
        assertThat(application.getEnvironment(), equalTo("test"));

        application.setEnvironment("product");
        assertThat(application.getEnvironment(), equalTo("product"));
    }




    @Test
    public void testEnvironment2() throws Exception {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            ApplicationConfig application = new ApplicationConfig("app");
            // 设置一个不允许设置的环境变量
            application.setEnvironment("illegal-env");
        });
    }

    @Test
    public void testRegistry() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        RegistryConfig registry = new RegistryConfig();
        // 进去
        application.setRegistry(registry);
        // 进去
        assertThat(application.getRegistry(), sameInstance(registry));
        // 进去
        // 和前面的setRegistry区分开，一个是传RegistryConfig，一个是传List<RegistryConfig>
        application.setRegistries(Collections.singletonList(registry));
        assertThat(application.getRegistries(), contains(registry));
        assertThat(application.getRegistries(), hasSize(1));
    }

    @Test
    public void testMonitor() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        // 进去
        application.setMonitor(new MonitorConfig("monitor-addr"));
        assertThat(application.getMonitor().getAddress(), equalTo("monitor-addr"));
        // 进去
        application.setMonitor("monitor-addr");
        assertThat(application.getMonitor().getAddress(), equalTo("monitor-addr"));
        // RegistryConfig、MonitorConfig都可以作为ApplicationConfig的属性
    }

    @Test
    public void testLogger() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setLogger("log4j");
        assertThat(application.getLogger(), equalTo("log4j"));
    }

    @Test
    public void testDefault() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setDefault(true);
        assertThat(application.isDefault(), is(true));
    }

    @Test
    public void testDumpDirectory() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setDumpDirectory("/dump");
        // 进去
        assertThat(application.getDumpDirectory(), equalTo("/dump"));
        Map<String, String> parameters = new HashMap<String, String>();
        ApplicationConfig.appendParameters(parameters, application);
        // "application" -> "app"
        // "dump.directory" -> "/dump"
        assertThat(parameters, hasEntry(DUMP_DIRECTORY, "/dump"));
    }

    // easy
    @Test
    public void testQosEnable() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setQosEnable(true);
        assertThat(application.getQosEnable(), is(true));
        Map<String, String> parameters = new HashMap<String, String>();
        ApplicationConfig.appendParameters(parameters, application);
        assertThat(parameters, hasEntry(QOS_ENABLE, "true"));
    }

    // easy
    @Test
    public void testQosPort() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setQosPort(8080);
        assertThat(application.getQosPort(), equalTo(8080));
    }

    // easy
    @Test
    public void testQosAcceptForeignIp() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setQosAcceptForeignIp(true);
        assertThat(application.getQosAcceptForeignIp(), is(true));
        Map<String, String> parameters = new HashMap<String, String>();
        ApplicationConfig.appendParameters(parameters, application);
        assertThat(parameters, hasEntry(ACCEPT_FOREIGN_IP, "true"));
    }

    @Test
    public void testParameters() throws Exception {
        ApplicationConfig application = new ApplicationConfig("app");
        application.setQosAcceptForeignIp(true);
        Map<String, String> parameters = new HashMap<String, String>();
        // 可以放自己的kv
        parameters.put("k1", "v1");
        ApplicationConfig.appendParameters(parameters, application);
        assertThat(parameters, hasEntry("k1", "v1"));
        assertThat(parameters, hasEntry(ACCEPT_FOREIGN_IP, "true"));
    }

    @Test
    public void testAppendEnvironmentProperties() {
        try {
            ApplicationConfig application = new ApplicationConfig("app");
            // setProperty 两个参数 kv
            System.setProperty("dubbo.labels", "tag1=value1;tag2=value2 ; tag3 = value3");
            // 进去
            application.refresh();
            // 此时parameters 为
            //"tag1" -> "value1"
            //"tag2" -> "value2"
            //"tag3" -> "value3"
            Map<String, String> parameters = application.getParameters();
            Assertions.assertEquals("value1", parameters.get("tag1"));
            Assertions.assertEquals("value2", parameters.get("tag2"));
            Assertions.assertEquals("value3", parameters.get("tag3"));

            ApplicationConfig application1 = new ApplicationConfig("app");
            System.setProperty("dubbo.env.keys", "tag1, tag2,tag3");
            // mock environment variables
            System.setProperty("tag1", "value1");
            System.setProperty("tag2", "value2");
            System.setProperty("tag3", "value3");
            application1.refresh();
            Map<String, String> parameters1 = application1.getParameters();
            Assertions.assertEquals("value1", parameters1.get("tag1"));
            Assertions.assertEquals("value2", parameters1.get("tag2"));
            Assertions.assertEquals("value3", parameters1.get("tag3"));

            Map<String, String> urlParameters = new HashMap<>();
            ApplicationConfig.appendParameters(urlParameters, application1);
            Assertions.assertEquals("value1", urlParameters.get("tag1"));
            Assertions.assertEquals("value2", urlParameters.get("tag2"));
            Assertions.assertEquals("value3", urlParameters.get("tag3"));
        } finally {
            // setProperty、getProperty、clearProperty三个api注意下
            System.clearProperty("dubbo.labels");
            System.clearProperty("dubbo.keys");
        }
    }
}

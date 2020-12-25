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
package org.apache.dubbo.common.utils;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.threadpool.ThreadPool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

// OK
public class ConfigUtilsTest {
    @BeforeEach
    public void setUp() throws Exception {
        ConfigUtils.setProperties(null);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ConfigUtils.setProperties(null);
    }

    @Test
    public void testIsNotEmpty() throws Exception {
        // 进去
        assertThat(ConfigUtils.isNotEmpty("abc"), is(true));
    }

    @Test
    public void testIsEmpty() throws Exception {
        // 下面的几种值都被当做empty
        assertThat(ConfigUtils.isEmpty(null), is(true));
        assertThat(ConfigUtils.isEmpty(""), is(true));
        assertThat(ConfigUtils.isEmpty("false"), is(true));
        assertThat(ConfigUtils.isEmpty("FALSE"), is(true));
        assertThat(ConfigUtils.isEmpty("0"), is(true));
        assertThat(ConfigUtils.isEmpty("null"), is(true));
        assertThat(ConfigUtils.isEmpty("NULL"), is(true));
        assertThat(ConfigUtils.isEmpty("n/a"), is(true));
        assertThat(ConfigUtils.isEmpty("N/A"), is(true));
    }

    @Test
    public void testIsDefault() throws Exception {
        // 下面的几种值都被当做default，不区分大小写 进去
        assertThat(ConfigUtils.isDefault("true"), is(true));
        assertThat(ConfigUtils.isDefault("TRUE"), is(true));
        assertThat(ConfigUtils.isDefault("default"), is(true));
        assertThat(ConfigUtils.isDefault("DEFAULT"), is(true));
    }

    @Test
    public void testMergeValues() {
        // 合并扩展名的，将cfg根据","切割和后面list合并，注意第二个参数list中的default.limited会被过滤掉，因为其不是ThreadPool的扩展类
        // 进去
        List<String> merged = ConfigUtils.mergeValues(ThreadPool.class, "aaa,bbb,default.custom",
                asList("fixed", "default.limited", "cached"));
        assertEquals(asList("fixed", "cached", "aaa", "bbb", "default.custom"), merged);
    }

    @Test
    public void testMergeValuesAddDefault() {
        // 注意default被过滤了，进去
        List<String> merged = ConfigUtils.mergeValues(ThreadPool.class, "aaa,bbb,default,zzz",
                asList("fixed", "default.limited", "cached"));
        assertEquals(asList("aaa", "bbb", "fixed", "cached", "zzz"), merged);
    }

    @Test
    public void testMergeValuesDeleteDefault() {
        // 第二个全被过滤
        List<String> merged = ConfigUtils.mergeValues(ThreadPool.class, "-default", asList("fixed", "default.limited", "cached"));
        assertEquals(asList(), merged);
    }

    @Test
    public void testMergeValuesDeleteDefault_2() {
        // 第二个全被过滤 只剩aaa
        List<String> merged = ConfigUtils.mergeValues(ThreadPool.class, "-default,aaa", asList("fixed", "default.limited", "cached"));
        assertEquals(asList("aaa"), merged);
    }

    /**
     * The user configures -default, which will delete all the default parameters
     */
    @Test
    public void testMergeValuesDelete() {
        // 第二个参数list中的default.limited会被过滤掉，因为其不是ThreadPool的扩展类，第一个参数含有-fixed，所以fixed和-fixed都被过滤
        List<String> merged = ConfigUtils.mergeValues(ThreadPool.class, "-fixed,aaa", asList("fixed", "default.limited", "cached"));
        assertEquals(asList("cached", "aaa"), merged);
    }

    @Test
    public void testReplaceProperty() throws Exception {
        // ${a.b.c}是占位符，进去
        String s = ConfigUtils.replaceProperty("1${a.b.c}2${a.b.c}3", Collections.singletonMap("a.b.c", "ABC"));
        assertEquals(s, "1ABC2ABC3");
        s = ConfigUtils.replaceProperty("1${a.b.c}2${a.b.c}3", Collections.<String, String>emptyMap());
        assertEquals(s, "123");
    }


    @Test
    public void testGetProperties1() throws Exception {
        try {
            // 给系统属性设置值，ConfigUtils内部会获取系统属性CommonConstants.DUBBO_PROPERTIES_KEY的值然后读取文件内容赋值到Properties
            System.setProperty(CommonConstants.DUBBO_PROPERTIES_KEY, "properties.load");
            // 进去
            Properties p = ConfigUtils.getProperties();
            // properties.load文件的数据都填充到Properties了
            assertThat((String) p.get("a"), equalTo("12"));
            assertThat((String) p.get("b"), equalTo("34"));
            assertThat((String) p.get("c"), equalTo("56"));
        } finally {
            System.clearProperty(CommonConstants.DUBBO_PROPERTIES_KEY);
        }
    }

    @Test
    public void testGetProperties2() throws Exception {
        // 清除DUBBO_PROPERTIES_KEY这个系统属性，内部会用默认的值作为属性文件名（dubbo.properties）
        System.clearProperty(CommonConstants.DUBBO_PROPERTIES_KEY);
        Properties p = ConfigUtils.getProperties();
        // 默认属性文件dubbo.properties的内容就是dubbo=properties
        assertThat((String) p.get("dubbo"), equalTo("properties"));
    }

    @Test
    public void testAddProperties() throws Exception {
        Properties p = new Properties();
        p.put("key1", "value1");
        // 进去
        ConfigUtils.addProperties(p);
        assertThat((String) ConfigUtils.getProperties().get("key1"), equalTo("value1"));
    }

    @Test
    public void testLoadPropertiesNoFile() throws Exception {
        Properties p = ConfigUtils.loadProperties("notExisted", true);
        Properties expected = new Properties();
        assertEquals(expected, p);

        p = ConfigUtils.loadProperties("notExisted", false);
        assertEquals(expected, p);
    }

    // easy
    @Test
    public void testGetProperty() throws Exception {
        // 进去
        assertThat(ConfigUtils.getProperty("dubbo"), equalTo("properties"));
    }
    // easy
    @Test
    public void testGetPropertyDefaultValue() throws Exception {
        assertThat(ConfigUtils.getProperty("not-exist", "default"), equalTo("default"));
    }

    @Test
    public void testGetPropertyFromSystem() throws Exception {
        try {
            // 设置系统属性
            System.setProperty("dubbo", "system");
            // 内部会优先从系统属性获取
            assertThat(ConfigUtils.getProperty("dubbo"), equalTo("system"));
        } finally {
            System.clearProperty("dubbo");
        }
    }

    @Test
    public void testGetSystemProperty() throws Exception {
        try {
            System.setProperty("dubbo", "system-only");
            // 进去
            assertThat(ConfigUtils.getSystemProperty("dubbo"), equalTo("system-only"));
        } finally {
            System.clearProperty("dubbo");
        }
    }

    // easy
    @Test
    public void testLoadProperties() throws Exception {
        Properties p = ConfigUtils.loadProperties("dubbo.properties");
        assertThat((String)p.get("dubbo"), equalTo("properties"));
    }

    // easy
    @Test
    public void testLoadPropertiesOneFile() throws Exception {
        Properties p = ConfigUtils.loadProperties("properties.load", false);

        Properties expected = new Properties();
        expected.put("a", "12");
        expected.put("b", "34");
        expected.put("c", "56");

        assertEquals(expected, p);
    }

    // easy
    @Test
    public void testLoadPropertiesOneFileAllowMulti() throws Exception {
        Properties p = ConfigUtils.loadProperties("properties.load", true);

        Properties expected = new Properties();
        expected.put("a", "12");
        expected.put("b", "34");
        expected.put("c", "56");

        assertEquals(expected, p);
    }

    //
    @Test
    public void testLoadPropertiesOneFileNotRootPath() throws Exception {
        // 唯一文件，内部也是会检查到不存在，利用线程上下文加载器加载
        // 看到测试方法名称叫做不是根路径，也就是不是根路径的都必须用线程上下文加载器才能加载到
        Properties p = ConfigUtils.loadProperties("META-INF/dubbo/internal/org.apache.dubbo.common.threadpool.ThreadPool", false);

        Properties expected = new Properties();
        expected.put("fixed", "org.apache.dubbo.common.threadpool.support.fixed.FixedThreadPool");
        expected.put("cached", "org.apache.dubbo.common.threadpool.support.cached.CachedThreadPool");
        expected.put("limited", "org.apache.dubbo.common.threadpool.support.limited.LimitedThreadPool");
        expected.put("eager", "org.apache.dubbo.common.threadpool.support.eager.EagerThreadPool");

        assertEquals(expected, p);
    }


    // 测试的时候记得把下面的@Disabled注释掉
    //@Disabled("Not know why disabled, the original link explaining this was reachable.")
    @Test
    public void testLoadPropertiesMultiFileNotRootPathException() throws Exception {
        try {
            // 这个是别的模块的，所以加载不到
            ConfigUtils.loadProperties("META-INF/services/org.apache.dubbo.common.status.StatusChecker", false);
            Assertions.fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("only 1 META-INF/services/org.apache.dubbo.common.status.StatusChecker file is expected, but 2 dubbo.properties files found on class path:"));
        }
    }

    @Test
    public void testLoadPropertiesMultiFileNotRootPath() throws Exception {

        // 这个文件是在common模块下 ，（利用线程上下文加载器）肯定能加载到，且注意有两个同名文件一个在main下，一个在test下。
        // 下面的p里面放了两个文件的内容
        Properties p = ConfigUtils.loadProperties("META-INF/dubbo/internal/org.apache.dubbo.common.status.StatusChecker", true);

        Properties expected = new Properties();
        expected.put("memory", "org.apache.dubbo.common.status.support.MemoryStatusChecker");
        expected.put("load", "org.apache.dubbo.common.status.support.LoadStatusChecker");
        expected.put("aa", "12");

        assertEquals(expected, p);
    }

    @Test
    public void testGetPid() throws Exception {
        // 获取pid，进程id
        assertThat(ConfigUtils.getPid(), greaterThan(0));
    }

    // With Structed Value 结构化的数据也能获取
    @Test
    public void testPropertiesWithStructedValue() throws Exception {
        Properties p = ConfigUtils.loadProperties("parameters.properties", false);

        Properties expected = new Properties();
        // 第二个参数就是Structed Value
        expected.put("dubbo.parameters", "[{a:b},{c_.d: r*}]");

        assertEquals(expected, p);
    }
}

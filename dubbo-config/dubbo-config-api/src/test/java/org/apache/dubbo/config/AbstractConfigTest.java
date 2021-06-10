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
package org.apache.dubbo.config;

import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.config.api.Greeting;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.rpc.model.ApplicationModel;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// OK
public class AbstractConfigTest {

    //FIXME
    /*@Test
    public void testAppendProperties1() throws Exception {
        try {
            System.setProperty("dubbo.properties.i", "1");
            System.setProperty("dubbo.properties.c", "c");
            System.setProperty("dubbo.properties.b", "2");
            System.setProperty("dubbo.properties.d", "3");
            System.setProperty("dubbo.properties.f", "4");
            System.setProperty("dubbo.properties.l", "5");
            System.setProperty("dubbo.properties.s", "6");
            System.setProperty("dubbo.properties.str", "dubbo");
            System.setProperty("dubbo.properties.bool", "true");
            PropertiesConfig config = new PropertiesConfig();
            AbstractConfig.appendProperties(config);
            Assertions.assertEquals(1, config.getI());
            Assertions.assertEquals('c', config.getC());
            Assertions.assertEquals((byte) 0x02, config.getB());
            Assertions.assertEquals(3d, config.getD());
            Assertions.assertEquals(4f, config.getF());
            Assertions.assertEquals(5L, config.getL());
            Assertions.assertEquals(6, config.getS());
            Assertions.assertEquals("dubbo", config.getStr());
            Assertions.assertTrue(config.isBool());
        } finally {
            System.clearProperty("dubbo.properties.i");
            System.clearProperty("dubbo.properties.c");
            System.clearProperty("dubbo.properties.b");
            System.clearProperty("dubbo.properties.d");
            System.clearProperty("dubbo.properties.f");
            System.clearProperty("dubbo.properties.l");
            System.clearProperty("dubbo.properties.s");
            System.clearProperty("dubbo.properties.str");
            System.clearProperty("dubbo.properties.bool");
        }
    }

    @Test
    public void testAppendProperties2() throws Exception {
        try {
            System.setProperty("dubbo.properties.two.i", "2");
            PropertiesConfig config = new PropertiesConfig("two");
            AbstractConfig.appendProperties(config);
            Assertions.assertEquals(2, config.getI());
        } finally {
            System.clearProperty("dubbo.properties.two.i");
        }
    }

    @Test
    public void testAppendProperties3() throws Exception {
        try {
            Properties p = new Properties();
            p.put("dubbo.properties.str", "dubbo");
            ConfigUtils.setProperties(p);
            PropertiesConfig config = new PropertiesConfig();
            AbstractConfig.appendProperties(config);
            Assertions.assertEquals("dubbo", config.getStr());
        } finally {
            System.clearProperty(Constants.DUBBO_PROPERTIES_KEY);
            ConfigUtils.setProperties(null);
        }
    }*/

    @Test
    public void testAppendParameters1() throws Exception {
        Map<String, String> parameters = new HashMap<String, String>();
        // 加了这个，appendParameters内部处理config对象的getXX方法计算出来的key如果是num的话，那么会用ONE拼接getXX的返回值
        parameters.put("num", "ONE");
        // 第三个prefix注意
        AbstractConfig.appendParameters(parameters,
                new ParameterConfig(1, "hello/world", 30, "password"), "prefix");
//"prefix.key.2" -> "two"            // 这2个是ParameterConfig对象里面的getParameters里面填充的两个entry，只不过在原有entry的key前面拼接了prefix
//"prefix.key.1" -> "one"
//"prefix.key-2" -> "two"            // 在处理getParameters返回结果的map的时候，如果发现有-连接的会新生成一个.连接的
//"prefix.num" -> "ONE,1"            // getNumber上面的@Parameter(key=num)所以是prefix.num，value部分的原因看上面第二行代码上面注释
//"num" -> "ONE"                     // 第二行代码加的
//"prefix.age" -> "30"               // getAge()没有@Paramater注解，所以key就是prefix.age
//"prefix.naming" -> "hello%2Fworld" // @Parameter(key = "naming") 所以key为prefix.naming，value部分有%2F因为@Parameter(...escaped=true..)所以会encode编码
        // 且注意getPassword上面的@Parameter(excluded = true) 所以没有填充到结果集

        Assertions.assertEquals("one", parameters.get("prefix.key.1"));
        Assertions.assertEquals("two", parameters.get("prefix.key.2"));
        Assertions.assertEquals("ONE,1", parameters.get("prefix.num"));
        Assertions.assertEquals("hello%2Fworld", parameters.get("prefix.naming"));
        Assertions.assertEquals("30", parameters.get("prefix.age"));
        Assertions.assertTrue(parameters.containsKey("prefix.key-2"));
        Assertions.assertTrue(parameters.containsKey("prefix.key.2"));
        Assertions.assertFalse(parameters.containsKey("prefix.secret"));
    }

    @Test
    public void testAppendParameters2() throws Exception {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            Map<String, String> parameters = new HashMap<String, String>();
            // 传递空参数构造的对象，导致getNaming返回null，但是方法上面注解@Parmeter(...required=true)，即必须这个属性有值，那么内部肯定抛异常
            AbstractConfig.appendParameters(parameters, new ParameterConfig());
        });
    }

    @Test
    public void testAppendParameters3() throws Exception {
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractConfig.appendParameters(parameters, null);
        assertTrue(parameters.isEmpty());
    }

    @Test
    public void testAppendParameters4() throws Exception {
        Map<String, String> parameters = new HashMap<String, String>();
        AbstractConfig.appendParameters(parameters, new ParameterConfig(1, "hello/world", 30, "password"));
        Assertions.assertEquals("one", parameters.get("key.1"));
        Assertions.assertEquals("two", parameters.get("key.2"));
        // 和第一个测试程序的区别就是如下。因为没有先前put("num",xxx)
        Assertions.assertEquals("1", parameters.get("num"));
        Assertions.assertEquals("hello%2Fworld", parameters.get("naming"));
        Assertions.assertEquals("30", parameters.get("age"));
    }

    @Test
    public void testAppendAttributes1() throws Exception {
        Map<String, Object> parameters = new HashMap<String, Object>();
        //  这个仅处理带有@Parameter注解的，并且注解的attribute属性值必须为true（默认是false的）、
        //  map的value不限于String类型，进去
        AbstractConfig.appendAttributes(parameters, new AttributeConfig('l', true, (byte) 0x01), "prefix");
        Assertions.assertEquals('l', parameters.get("prefix.let"));
        Assertions.assertEquals(true, parameters.get("prefix.activate"));
        Assertions.assertFalse(parameters.containsKey("prefix.flag"));
    }

    @Test
    public void testAppendAttributes2() throws Exception {
        Map<String, Object> parameters = new HashMap<String, Object>();
        // 不加prefix
        AbstractConfig.appendAttributes(parameters, new AttributeConfig('l', true, (byte) 0x01));
        Assertions.assertEquals('l', parameters.get("let"));
        Assertions.assertEquals(true, parameters.get("activate"));
        Assertions.assertFalse(parameters.containsKey("flag"));
    }

    @Test
    public void checkExtension() throws Exception {
        Assertions.assertThrows(IllegalStateException.class, () ->
                // 检查Greeting这个spi接口有没有扩展名为world的扩展类，进去
                ConfigValidationUtils.checkExtension(Greeting.class, "hello", "world"));
    }

    @Test
    public void checkMultiExtension1() throws Exception {
        Assertions.assertThrows(IllegalStateException.class, () ->
                // 检查多个扩展名
                ConfigValidationUtils.checkMultiExtension(Greeting.class, "hello", "default,world"));
    }

    @Test
    public void checkMultiExtension2() throws Exception {
        Assertions.assertThrows(IllegalStateException.class, () ->
                ConfigValidationUtils.checkMultiExtension(Greeting.class, "hello", "default,-world"));
    }

    @Test
    public void checkLength() throws Exception {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i <= 200; i++) {
                builder.append("a");
            }
            // 长度不超过200，builder的长度是201，进去
            ConfigValidationUtils.checkLength("hello", builder.toString());
        });
    }

    @Test
    public void checkPathLength() throws Exception {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i <= 200; i++) {
                builder.append("a");
            }
            // path的长度依然最大为200
            ConfigValidationUtils.checkPathLength("hello", builder.toString());
        });
    }

    @Test
    public void checkName() throws Exception {
        Assertions.assertThrows(IllegalStateException.class, () ->
                // 通配符不满足
                ConfigValidationUtils.checkName("hello", "world%"));
    }

    // 下面几个check暂时不看，直接跳到appendAnnotation
    @Test
    public void checkNameHasSymbol() throws Exception {
        try {
            ConfigValidationUtils.checkNameHasSymbol("hello", ":*,/ -0123\tabcdABCD");
            ConfigValidationUtils.checkNameHasSymbol("mock", "force:return world");
        } catch (Exception e) {
            fail("the value should be legal.");
        }
    }

    @Test
    public void checkKey() throws Exception {
        try {
            ConfigValidationUtils.checkKey("hello", "*,-0123abcdABCD");
        } catch (Exception e) {
            fail("the value should be legal.");
        }
    }

    @Test
    public void checkMultiName() throws Exception {
        try {
            ConfigValidationUtils.checkMultiName("hello", ",-._0123abcdABCD");
        } catch (Exception e) {
            fail("the value should be legal.");
        }
    }

    @Test
    public void checkPathName() throws Exception {
        try {
            ConfigValidationUtils.checkPathName("hello", "/-$._0123abcdABCD");
        } catch (Exception e) {
            fail("the value should be legal.");
        }
    }

    @Test
    public void checkMethodName() throws Exception {
        try {
            ConfigValidationUtils.checkMethodName("hello", "abcdABCD0123abcd");
        } catch (Exception e) {
            fail("the value should be legal.");
        }

        try {
            ConfigValidationUtils.checkMethodName("hello", "0a");
            fail("the value should be illegal.");
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    public void checkParameterName() throws Exception {
        Map<String, String> parameters = Collections.singletonMap("hello", ":*,/-._0123abcdABCD");
        try {
            ConfigValidationUtils.checkParameterName(parameters);
        } catch (Exception e) {
            fail("the value should be legal.");
        }
    }

    // 注意filter是"f1, f2" ，不是{"f1","f2"}
    @Test
    @Config(interfaceClass = Greeting.class, filter = {"f1, f2"}, listener = {"l1, l2"},
            parameters = {"k1", "v1", "k2", "v2"})
    public void appendAnnotation() throws Exception {
        Config config = getClass().getMethod("appendAnnotation").getAnnotation(Config.class);
        // 这个是Test的一个内部类，继承AbstractConfig的
        AnnotationConfig annotationConfig = new AnnotationConfig();
        // 进去
        annotationConfig.appendAnnotation(Config.class, config);
        // 上面的方法会把config注解对象里面的属性值填充annotationConfig的对应属性中，此时从annotationConfig都能取出来了
        Assertions.assertSame(Greeting.class, annotationConfig.getInterface());
        Assertions.assertEquals("f1, f2", annotationConfig.getFilter());
        Assertions.assertEquals("l1, l2", annotationConfig.getListener());
        Assertions.assertEquals(2, annotationConfig.getParameters().size());
        Assertions.assertEquals("v1", annotationConfig.getParameters().get("k1"));
        Assertions.assertEquals("v2", annotationConfig.getParameters().get("k2"));
        // toString方法进去
        assertThat(annotationConfig.toString(), Matchers.containsString("filter=\"f1, f2\" "));
        assertThat(annotationConfig.toString(), Matchers.containsString("listener=\"l1, l2\" "));
    }

    @Test
    public void testRefreshAll() {
        try {

            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setAddress("override-config://127.0.0.1:2181");
            overrideConfig.setProtocol("override-config");
            overrideConfig.setEscape("override-config://"); // 3 ConfigConfigurationAdapter（OverrideConfig的属性会填充到此Configuration）
            overrideConfig.setExclude("override-config");
            // dubbo.override.prefix: 7 ConfigConfigurationAdapter

            // toString方法去看下
            // <dubbo:override escape="override-config://" exclude="override-config" address="override-config://127.0.0.1:2181" protocol="override-config" />


            Map<String, String> external = new HashMap<>();
            external.put("dubbo.override.address", "external://127.0.0.1:2181");
            // @Parameter(exclude=true)
            external.put("dubbo.override.exclude", "external"); // 4 InmemoryConfiguration（external会填充到此Configuration）
            // @Parameter(key="key1", useKeyAsProperty=false)
            external.put("dubbo.override.key", "external");// 5 InmemoryConfiguration
            // @Parameter(key="key2", useKeyAsProperty=true)
            external.put("dubbo.override.key2", "external");

            // 额外说一下上面的@Parameter(..useKeyAsProperty=false..)的含义，可以去看下OverrideConfig类的，正好有两个getXx方法
            // 上面的useKeyAsProperty一个是true，一个是false，他们其实影响ConfigConfigurationAdapter的metaData这个map的key！
            // 因为metaData的生成是依赖AbstractConfig的getMetaData方法调用，getMetaData方法内部就会处理这个useKeyAsProperty，
            // 如果为true，那么就使用@Parameter(key=xx)的xx作为metaData 这个 map的key，否则根据属性本身生成key
            // 因为是做refresh方法内部的属性覆盖实验，所以后两个external.put的key是特意指定的（这里就不展开了...）


            // getEnvironment内部就会创建Environment对象，注意其构造方法。external这个map会赋值给Environment对象的externalConfigurationMap属性
            ApplicationModel.getEnvironment().setExternalConfigMap(external);
            // 内部其实就是把externalConfigurationMap属性填充InmemoryConfiguration类型对象（属性名称叫externalConfiguration）的store属性中，进去
            ApplicationModel.getEnvironment().initialize();

            System.setProperty("dubbo.override.address", "system://127.0.0.1:2181"); // 1 SystemConfiguration
            System.setProperty("dubbo.override.protocol", "system");// 2 SystemConfiguration
            // this will not override, use 'key' instead, @Parameter(key="key1", useKeyAsProperty=false)
            System.setProperty("dubbo.override.key1", "system");
            System.setProperty("dubbo.override.key2", "system");// 6 SystemConfiguration

            // 前面之所以是dubbo.override作为prefix是因为AbstractConfig的getPrefix是有默认值的，这个会作为CompositeConfiguration的prefix值
            // refresh内部属性获取的顺序在上面用数字写出来了，以及是从哪个Configuration取出来的
            // Load configuration from  system properties -> externalConfiguration -> RegistryConfig -> dubbo.properties
            overrideConfig.refresh();

            /*overrideConfig = {AbstractConfigTest$OverrideConfig@1983}
            "<dubbo:override escape="override-config://" useKeyAsProperty="system" exclude="external" address="system://127.0.0.1:2181" key="external" protocol="system" />"
            address = "system://127.0.0.1:2181"
            protocol = "system"
            exclude = "external"
            key = "external"
            useKeyAsProperty = "system"
            escape = "override-config://"
            prefix = "dubbo.override"
            refreshed = {AtomicBoolean@2273} "false"*/

            // 前面refresh内部触发了对应的setXx方法，将属性赋了值，但是注意Configuration的优先级，先前声明的key被后面声明（但是优先级更高的）同名key覆盖掉
            // 就比如代码最开始先声明overrideConfig.address属性的值肯定是"override-config://127.0.0.1:2181"，但是SystemConfiguration的优先级比
            // ConfigConfigurationAdapter的高，所以address的值肯定是dubbo.override.address
            // fix 上面说的不对，而是Sys的靠前，在CompositeConfiguration内部遍历到一个满足会直接break，而SysCfg靠前所以先取到，然后赋值

            Assertions.assertEquals("system://127.0.0.1:2181", overrideConfig.getAddress());
            Assertions.assertEquals("system", overrideConfig.getProtocol());
            Assertions.assertEquals("override-config://", overrideConfig.getEscape());
            Assertions.assertEquals("external", overrideConfig.getKey());
            Assertions.assertEquals("system", overrideConfig.getUseKeyAsProperty());
        } finally {
            System.clearProperty("dubbo.override.address");
            System.clearProperty("dubbo.override.protocol");
            System.clearProperty("dubbo.override.key1");
            System.clearProperty("dubbo.override.key2");
            ApplicationModel.getEnvironment().clearExternalConfigs();
        }
    }

    // 前面 testRefreshAll 这里 testRefreshSystem肯定更好理解了
    @Test
    public void testRefreshSystem() {
        try {
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setAddress("override-config://127.0.0.1:2181");
            overrideConfig.setProtocol("override-config");
            overrideConfig.setEscape("override-config://");
            overrideConfig.setExclude("override-config");

            System.setProperty("dubbo.override.address", "system://127.0.0.1:2181");
            System.setProperty("dubbo.override.protocol", "system");
            System.setProperty("dubbo.override.key", "system");

            overrideConfig.refresh();

            // 下两行对应的属性虽然在OverrideConfig配置了（在refresh内部填充到ConfigConfigurationAdapter），
            // 但是SystemConfiguration的优先级更高，所以同名key优先取其的
            Assertions.assertEquals("system://127.0.0.1:2181", overrideConfig.getAddress());
            Assertions.assertEquals("system", overrideConfig.getProtocol());
            // 只在SystemConfiguration配置过
            Assertions.assertEquals("system", overrideConfig.getKey());
            // 只在OverrideConfig（ConfigConfigurationAdapter）配置过
            Assertions.assertEquals("override-config://", overrideConfig.getEscape());

        } finally {
            // 恢复现场
            System.clearProperty("dubbo.override.address");
            System.clearProperty("dubbo.override.protocol");
            System.clearProperty("dubbo.override.key1");
            ApplicationModel.getEnvironment().clearExternalConfigs();
        }
    }

    // easy
    @Test
    public void testRefreshProperties() throws Exception {
        try {
            ApplicationModel.getEnvironment().setExternalConfigMap(new HashMap<>());
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setAddress("override-config://127.0.0.1:2181");
            overrideConfig.setProtocol("override-config");
            overrideConfig.setEscape("override-config://");

            // todo need pr 下面可以删掉，上面第一行内部就加载了/dubbo.properties
            Properties properties = new Properties();
            properties.load(this.getClass().getResourceAsStream("/dubbo.properties"));
            ConfigUtils.setProperties(properties);

            overrideConfig.refresh();

            Assertions.assertEquals("override-config://127.0.0.1:2181", overrideConfig.getAddress());
            Assertions.assertEquals("override-config", overrideConfig.getProtocol());
            Assertions.assertEquals("override-config://", overrideConfig.getEscape());
            //Assertions.assertEquals("properties", overrideConfig.getUseKeyAsProperty());
        } finally {
            ApplicationModel.getEnvironment().clearExternalConfigs();
            // todo need pr 下面可以删掉
            ConfigUtils.setProperties(null);
        }
    }

    // easy
    @Test
    public void testRefreshExternal() {
        try {
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setAddress("override-config://127.0.0.1:2181");
            overrideConfig.setProtocol("override-config");
            overrideConfig.setEscape("override-config://");
            overrideConfig.setExclude("override-config");

            Map<String, String> external = new HashMap<>();
            external.put("dubbo.override.address", "external://127.0.0.1:2181");
            external.put("dubbo.override.protocol", "external");
            external.put("dubbo.override.escape", "external://");
            // @Parameter(exclude=true)
            external.put("dubbo.override.exclude", "external");
            // @Parameter(key="key1", useKeyAsProperty=false)
            external.put("dubbo.override.key", "external");
            // @Parameter(key="key2", useKeyAsProperty=true)
            external.put("dubbo.override.key2", "external");
            ApplicationModel.getEnvironment().setExternalConfigMap(external);
            ApplicationModel.getEnvironment().initialize();

            overrideConfig.refresh();

            // 有的key在 ConfigConfigurationAdapter（OverrideConfig）和InmemoryConfiguration（external map）同名了，
            // 但是和InmemoryConfiguration的优先级更高，所以取其的
            Assertions.assertEquals("external://127.0.0.1:2181", overrideConfig.getAddress());
            Assertions.assertEquals("external", overrideConfig.getProtocol());
            Assertions.assertEquals("external://", overrideConfig.getEscape());
            Assertions.assertEquals("external", overrideConfig.getExclude());
            Assertions.assertEquals("external", overrideConfig.getKey());
            Assertions.assertEquals("external", overrideConfig.getUseKeyAsProperty());
        } finally {
            ApplicationModel.getEnvironment().clearExternalConfigs();
        }
    }

    @Test
    public void testRefreshById() {
        try {
            OverrideConfig overrideConfig = new OverrideConfig();
            // 这里含有id，那么后面的refresh的前缀就是prefix+id = dubbo.override.override-id.
            // 且注意下面的属性在getPrefixedConfiguration方法中会被封装到ConfigConfigurationAdapter中，且也是自动装配前缀的（下面的external需要自己填前缀、env和system-property以及dubbo.properties也是需要自己加前缀）
            overrideConfig.setId("override-id");
            overrideConfig.setAddress("override-config://127.0.0.1:2181");
            overrideConfig.setProtocol("override-config");
            overrideConfig.setEscape("override-config://");
            overrideConfig.setExclude("override-config");

            Map<String, String> external = new HashMap<>();
            external.put("dubbo.override.override-id.address", "external-override-id://127.0.0.1:2181");
            external.put("dubbo.override.address", "external://127.0.0.1:2181");
            // @Parameter(exclude=true)
            external.put("dubbo.override.exclude", "external");
            // @Parameter(key="key1", useKeyAsProperty=false)
            external.put("dubbo.override.key", "external");
            // @Parameter(key="key2", useKeyAsProperty=true)
            external.put("dubbo.override.key2", "external");
            ApplicationModel.getEnvironment().setExternalConfigMap(external);
            ApplicationModel.getEnvironment().initialize();

            ConfigCenterConfig configCenter = new ConfigCenterConfig();
            overrideConfig.setConfigCenter(configCenter);
            // Load configuration from  system properties -> externalConfiguration -> RegistryConfig -> dubbo.properties
            // 进去 注意里面的prefix+id
            // CompositeConfiguration#getProperty 如果 value = getInternalProperty(prefix + id + "." + key); 为 null，那么会用getInternalProperty(prefix + key);再去找一遍
            overrideConfig.refresh();

            Assertions.assertEquals("external-override-id://127.0.0.1:2181", overrideConfig.getAddress());
            Assertions.assertEquals("override-config", overrideConfig.getProtocol());
            Assertions.assertEquals("override-config://", overrideConfig.getEscape());
            Assertions.assertEquals("external", overrideConfig.getKey());
            Assertions.assertEquals("external", overrideConfig.getUseKeyAsProperty());
        } finally {
            ApplicationModel.getEnvironment().clearExternalConfigs();
        }
    }

    @Test
    public void testRefreshParameters() {
        try {
            Map<String, String> parameters = new HashMap<>();
            parameters.put("key1", "value1");
            parameters.put("key2", "value2");
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setParameters(parameters);


            Map<String, String> external = new HashMap<>();
            // refresh内部StringUtils.parseParameters(value)会处理成map
            external.put("dubbo.override.parameters", "[{key3:value3},{key4:value4},{key2:value5}]");
            ApplicationModel.getEnvironment().setExternalConfigMap(external);
            // 内部会把external填充到 InmemoryConfiguration externalConfiguration对象的store属性中
            ApplicationModel.getEnvironment().initialize();

            ConfigCenterConfig configCenter = new ConfigCenterConfig();
            overrideConfig.setConfigCenter(configCenter);
            // Load configuration from  system properties -> externalConfiguration -> RegistryConfig -> dubbo.properties
            overrideConfig.refresh();

            Assertions.assertEquals("value1", overrideConfig.getParameters().get("key1"));
            // 上面配置了两个key2，不过refresh内部会用第二个key的v覆盖掉前一个，因为后者的优先级更高
            Assertions.assertEquals("value5", overrideConfig.getParameters().get("key2"));
            Assertions.assertEquals("value3", overrideConfig.getParameters().get("key3"));
            Assertions.assertEquals("value4", overrideConfig.getParameters().get("key4"));

            // SystemConfiguration的优先级更高了，肯定取其的，而不会取InmemoryConfiguration（external）的了
            System.setProperty("dubbo.override.parameters", "[{key3:value6}]");
            overrideConfig.refresh();
            // 所以这里的key3 = value6
            Assertions.assertEquals("value6", overrideConfig.getParameters().get("key3"));
            Assertions.assertEquals("value4", overrideConfig.getParameters().get("key4"));
            // 注意了InmemoryConfiguration的parameters参数还是会取的，所以下面通过，我们说SystemConfiguration的优先级更高是体现在，前后
            // 两种Configuration有key值相同的情况取优先级更高的val
            Assertions.assertEquals("value5", overrideConfig.getParameters().get("key2"));
        } finally {
            System.clearProperty("dubbo.override.parameters");
            ApplicationModel.getEnvironment().clearExternalConfigs();
        }
    }

    @Test
    public void testOnlyPrefixedKeyTakeEffect() {
        try {
            OverrideConfig overrideConfig = new OverrideConfig();
            overrideConfig.setNotConflictKey("value-from-config");

            Map<String, String> external = new HashMap<>();
            external.put("notConflictKey", "value-from-external");// 没有 prefix前缀
            ApplicationModel.getEnvironment().setExternalConfigMap(external);
            ApplicationModel.getEnvironment().initialize();

            try {
                Map<String, String> map = new HashMap<>();
                map.put("notConflictKey", "value-from-env");
                map.put("dubbo.override.notConflictKey2", "value-from-env");
                // 设置到env（env的优先级在第二，仅次于system）
                setOsEnv(map);
            } catch (Exception e) {
                // ignore
                e.printStackTrace();
            }

            overrideConfig.refresh();

            // 内部会寻找 dubbn.override.notConflictKey，虽然前面External和env配置了，但是不带前缀，内部处理是优先获取前缀的，前缀走一轮
            // 按照前缀循环一轮获取不到（循环指的是 CompositeConfiguration#getInternalProperty方法） 才会进行prefix+key进行循环第二轮，而OverrideConfig正好有（具体看 CompositeConfiguration#getProperty）
            Assertions.assertEquals("value-from-config", overrideConfig.getNotConflictKey());
            // 内部会寻找 dubbn.override.notConflictKey2，正好从env找到
            Assertions.assertEquals("value-from-env", overrideConfig.getNotConflictKey2());
        } finally {
            ApplicationModel.getEnvironment().clearExternalConfigs();

        }
    }

    @Test
    public void tetMetaData() {
        OverrideConfig overrideConfig = new OverrideConfig();
        overrideConfig.setId("override-id");
        overrideConfig.setAddress("override-config://127.0.0.1:2181");
        overrideConfig.setProtocol("override-config");
        overrideConfig.setEscape("override-config://");
        overrideConfig.setExclude("override-config");

        // 进去 ，这里是不会拼接前缀的。前缀的拼接在构建ConfigConfigurationAdapter的时候
        Map<String, String> metaData = overrideConfig.getMetaData();
        Assertions.assertEquals("override-config://127.0.0.1:2181", metaData.get("address"));
        Assertions.assertEquals("override-config", metaData.get("protocol"));
        Assertions.assertEquals("override-config://", metaData.get("escape"));
        Assertions.assertEquals("override-config", metaData.get("exclude"));
        Assertions.assertNull(metaData.get("key"));
        Assertions.assertNull(metaData.get("key2"));
    }

    @Test
    public void testEquals() {
        // 先去看AbstractConfig的equals方法

        ApplicationConfig application1 = new ApplicationConfig();
        ApplicationConfig application2 = new ApplicationConfig();
        application1.setName("app1");
        application2.setName("app2");
        // 不等，因为equals内部必须要保证name(所有属性值)相同（如果有parameter注解，要保证excluded=false才会比较）
        Assertions.assertNotEquals(application1, application2);


        application1.setName("sameName");
        application2.setName("sameName");
        Assertions.assertEquals(application1, application2);

        ProtocolConfig protocol1 = new ProtocolConfig();
        // 因为其getHost方法上面@Parameter(excluded = true)，不参与一些属性的操作，比如内部的equals，所以即使这个属性不等，但是其他属性是等的，那么还是equals的
        protocol1.setHost("127.0.0.1");// excluded
        protocol1.setName("dubbo");
        ProtocolConfig protocol2 = new ProtocolConfig();
        protocol2.setHost("127.0.0.2");// excluded
        protocol2.setName("dubbo");
        Assertions.assertEquals(protocol1, protocol2);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE})// 元注解
    public @interface ConfigField {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    //@ConfigField  // 元注解也可以放在这里
    public @interface Config {
        Class<?> interfaceClass() default void.class;

        String interfaceName() default "";

        String[] filter() default {};

        String[] listener() default {};

        String[] parameters() default {};

        // 元注解也可以作为返回值
        ConfigField[] configFields() default {};

        ConfigField configField() default @ConfigField;
    }

    private static class OverrideConfig extends AbstractInterfaceConfig {
        public String address;
        public String protocol;
        public String exclude;
        public String key;
        public String useKeyAsProperty;
        public String escape;
        public String notConflictKey;
        public String notConflictKey2;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        @Parameter(excluded = true)
        public String getExclude() {
            return exclude;
        }

        public void setExclude(String exclude) {
            this.exclude = exclude;
        }

        @Parameter(key = "key1", useKeyAsProperty = false)
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        @Parameter(key = "key2", useKeyAsProperty = true)
        public String getUseKeyAsProperty() {
            return useKeyAsProperty;
        }

        public void setUseKeyAsProperty(String useKeyAsProperty) {
            this.useKeyAsProperty = useKeyAsProperty;
        }

        @Parameter(escaped = true)
        public String getEscape() {
            return escape;
        }

        public void setEscape(String escape) {
            this.escape = escape;
        }

        public String getNotConflictKey() {
            return notConflictKey;
        }

        public void setNotConflictKey(String notConflictKey) {
            this.notConflictKey = notConflictKey;
        }

        public String getNotConflictKey2() {
            return notConflictKey2;
        }

        public void setNotConflictKey2(String notConflictKey2) {
            this.notConflictKey2 = notConflictKey2;
        }
    }

    private static class PropertiesConfig extends AbstractConfig {
        private char c;
        private boolean bool;
        private byte b;
        private int i;
        private long l;
        private float f;
        private double d;
        private short s;
        private String str;

        PropertiesConfig() {
        }

        PropertiesConfig(String id) {
            this.id = id;
        }

        public char getC() {
            return c;
        }

        public void setC(char c) {
            this.c = c;
        }

        public boolean isBool() {
            return bool;
        }

        public void setBool(boolean bool) {
            this.bool = bool;
        }

        public byte getB() {
            return b;
        }

        public void setB(byte b) {
            this.b = b;
        }

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }

        public long getL() {
            return l;
        }

        public void setL(long l) {
            this.l = l;
        }

        public float getF() {
            return f;
        }

        public void setF(float f) {
            this.f = f;
        }

        public double getD() {
            return d;
        }

        public void setD(double d) {
            this.d = d;
        }

        public String getStr() {
            return str;
        }

        public void setStr(String str) {
            this.str = str;
        }

        public short getS() {
            return s;
        }

        public void setS(short s) {
            this.s = s;
        }
    }

    private static class ParameterConfig {
        private int number;
        private String name;
        private int age;
        private String secret;

        ParameterConfig() {
        }

        ParameterConfig(int number, String name, int age, String secret) {
            this.number = number;
            this.name = name;
            this.age = age;
            this.secret = secret;
        }

        @Parameter(key = "num", append = true)
        public int getNumber() {
            return number;
        }

        public void setNumber(int number) {
            this.number = number;
        }

        @Parameter(key = "naming", append = true, escaped = true, required = true)
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        @Parameter(excluded = true)
        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Map getParameters() {
            Map<String, String> map = new HashMap<String, String>();
            map.put("key.1", "one");
            map.put("key-2", "two");
            return map;
        }
    }

    private static class AttributeConfig {
        private char letter;
        private boolean activate;
        private byte flag;

        public AttributeConfig(char letter, boolean activate, byte flag) {
            this.letter = letter;
            this.activate = activate;
            this.flag = flag;
        }

        @Parameter(attribute = true, key = "let")
        public char getLetter() {
            return letter;
        }

        public void setLetter(char letter) {
            this.letter = letter;
        }

        @Parameter(attribute = true)
        public boolean isActivate() {
            return activate;
        }

        public void setActivate(boolean activate) {
            this.activate = activate;
        }

        public byte getFlag() {
            return flag;
        }

        public void setFlag(byte flag) {
            this.flag = flag;
        }
    }

    private static class AnnotationConfig extends AbstractConfig {
        private Class interfaceClass;
        private String filter;
        private String listener;
        private Map<String, String> parameters;
        private String[] configFields;

        public Class getInterface() {
            return interfaceClass;
        }

        public void setInterface(Class interfaceName) {
            this.interfaceClass = interfaceName;
        }

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

        public String getListener() {
            return listener;
        }

        public void setListener(String listener) {
            this.listener = listener;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        public String[] getConfigFields() {
            return configFields;
        }

        public void setConfigFields(String[] configFields) {
            this.configFields = configFields;
        }
    }

    protected static void setOsEnv(Map<String, String> newenv) throws Exception {
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newenv);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newenv);
        } catch (NoSuchFieldException e) {
            Class[] classes = Collections.class.getDeclaredClasses();
            Map<String, String> env = System.getenv();
            for (Class cl : classes) {
                if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                    Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    Object obj = field.get(env);
                    Map<String, String> map = (Map<String, String>) obj;
                    map.clear();
                    map.putAll(newenv);
                }
            }
        }
    }
}

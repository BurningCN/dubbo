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
package org.apache.dubbo.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The type Environment configuration test.
 */
// OK
class EnvironmentConfigurationTest {

    private static final String MOCK_KEY = "DUBBO_KEY";
    private static final String MOCK_VALUE = "mockValue";

    /**
     * Init.
     */
    @BeforeEach
    public void init() {

    }

    @Test
    public void testGetInternalProperty() {
        Map<String, String> map = new HashMap<>();
        map.put(MOCK_KEY, MOCK_VALUE);
        try {
            // 进去
            setEnv(map);
            EnvironmentConfiguration configuration = new EnvironmentConfiguration();
            // this UT maybe only works on particular platform, assert only when value is not null.
            // 内部先用dubbo.key查找，查找不到的话会会将dubbo.key转化为DUBBO_KEY再去查找，而上面put 了，所以能查到
            Assertions.assertEquals(MOCK_VALUE, configuration.getInternalProperty("dubbo.key"));
            // 内部会将dubbo.key转化为DUBBO_KEY
            Assertions.assertEquals(MOCK_VALUE, configuration.getInternalProperty("key"));
            // 内部会将dubbo.key转化为DUBBO_KEY
            Assertions.assertEquals(MOCK_VALUE, configuration.getInternalProperty("dubbo_key"));
            Assertions.assertEquals(MOCK_VALUE, configuration.getInternalProperty(MOCK_KEY));
        } catch (Exception e) {
            // skip test.
            e.printStackTrace();
        }
    }

    protected static void setEnv(Map<String, String> newenv) throws Exception {
        try {
            // 加载ProcessEnvironment类
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            // 反射获取theEnvironment字段
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            // 获取字段的值（传null说明是静态字段）
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            // 填充自己的map环境变量
            env.putAll(newenv);
            // 取环境变量直接System.getEnv("xx")即可，设置的话没有setEnv，需要上面的方式
            // env的结果（当前的环境变量）如下，当然用getEnv也是下面的结果
            //{ProcessEnvironment$Variable@1606} "PATH" -> {ProcessEnvironment$Value@1607} "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/Users/gy821075/mvn/apache-maven-3.6.3/bin:/Users/gy821075/mongodb-macos-x86_64-4.4.1/bin"
            //{ProcessEnvironment$Variable@1608} "SHELL" -> {ProcessEnvironment$Value@1609} "/bin/zsh"
            //{ProcessEnvironment$Variable@1610} "OLDPWD" -> {ProcessEnvironment$Value@1611} "/"
            //{ProcessEnvironment$Variable@1612} "USER" -> {ProcessEnvironment$Value@1613} "gy821075"
            //"DUBBO_KEY" -> "mockValue"--->这是自己填充的
            //{ProcessEnvironment$Variable@1616} "TMPDIR" -> {ProcessEnvironment$Value@1617} "/var/folders/6s/_gz_922s2bs2ss1xfz335zl80000gp/T/"
            //{ProcessEnvironment$Variable@1618} "MVN_HOME" -> {ProcessEnvironment$Value@1619} "/Users/gy821075/mvn/apache-maven-3.6.3"
            //{ProcessEnvironment$Variable@1620} "SSH_AUTH_SOCK" -> {ProcessEnvironment$Value@1621} "/private/tmp/com.apple.launchd.B8CXeEq7cX/Listeners"
            //{ProcessEnvironment$Variable@1622} "XPC_FLAGS" -> {ProcessEnvironment$Value@1623} "0x0"
            //{ProcessEnvironment$Variable@1624} "VERSIONER_PYTHON_VERSION" -> {ProcessEnvironment$Value@1625} "2.7"
            //{ProcessEnvironment$Variable@1626} "__CF_USER_TEXT_ENCODING" -> {ProcessEnvironment$Value@1627} "0x1F6:0x19:0x34"
            //{ProcessEnvironment$Variable@1628} "LOGNAME" -> {ProcessEnvironment$Value@1629} "gy821075"
            //{ProcessEnvironment$Variable@1630} "JAVA_MAIN_CLASS_19689" -> {ProcessEnvironment$Value@1631} "com.intellij.rt.junit.JUnitStarter"
            //{ProcessEnvironment$Variable@1632} "LC_CTYPE" -> {ProcessEnvironment$Value@1633} "en_US.UTF-8"
            //{ProcessEnvironment$Variable@1634} "PWD" -> {ProcessEnvironment$Value@1635} "/Users/gy821075/IdeaProjects/dubbo/dubbo-common"  -------- 注意
            //{ProcessEnvironment$Variable@1636} "XPC_SERVICE_NAME" -> {ProcessEnvironment$Value@1637} "com.jetbrains.intellij.12904"
            //{ProcessEnvironment$Variable@1638} "HOME" -> {ProcessEnvironment$Value@1639} "/Users/gy821075"

            // 下面和上面类似
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

    private static void updateEnv(String name, String val) throws ReflectiveOperationException {
        Map<String, String> env = System.getenv();
        Field field = env.getClass().getDeclaredField("m");
        field.setAccessible(true);
        ((Map<String, String>) field.get(env)).put(name, val);
    }
    /**
     * Clean.
     */
    @AfterEach
    public void clean(){

    }

}
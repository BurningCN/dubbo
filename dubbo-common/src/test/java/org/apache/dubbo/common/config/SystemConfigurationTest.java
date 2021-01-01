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

/**
 * The type System configuration test.
 */
// OK
class SystemConfigurationTest {

    private static SystemConfiguration sysConfig;
    private static final String MOCK_KEY = "mockKey";
    private static final String MOCK_STRING_VALUE = "mockValue";
    private static final Boolean MOCK_BOOL_VALUE = Boolean.FALSE;
    private static final Integer MOCK_INT_VALUE = Integer.MAX_VALUE;
    private static final Long MOCK_LONG_VALUE = Long.MIN_VALUE;
    private static final Short MOCK_SHORT_VALUE = Short.MIN_VALUE;
    private static final Float MOCK_FLOAT_VALUE = Float.MIN_VALUE;
    private static final Double MOCK_DOUBLE_VALUE = Double.MIN_VALUE;
    private static final Byte MOCK_BYTE_VALUE = Byte.MIN_VALUE;
    private static final String NOT_EXIST_KEY = "NOTEXIST";

    /**
     * Init.
     */
    @BeforeEach
    public void init() {

        sysConfig = new SystemConfiguration();
    }

    /**
     * Test get sys property.
     */
    @Test
    public void testGetSysProperty() {
        Assertions.assertNull(sysConfig.getInternalProperty(MOCK_KEY));
        Assertions.assertFalse(sysConfig.containsKey(MOCK_KEY));
        Assertions.assertNull(sysConfig.getString(MOCK_KEY));
        Assertions.assertNull(sysConfig.getProperty(MOCK_KEY));
        // 从这里开始看
        System.setProperty(MOCK_KEY, MOCK_STRING_VALUE);
        // 进去
        Assertions.assertTrue(sysConfig.containsKey(MOCK_KEY));
        // 进去
        Assertions.assertEquals(MOCK_STRING_VALUE, sysConfig.getInternalProperty(MOCK_KEY));
        // 进去（外界一般调用这个）
        Assertions.assertEquals(MOCK_STRING_VALUE, sysConfig.getString(MOCK_KEY, MOCK_STRING_VALUE));
        // 进去
        Assertions.assertEquals(MOCK_STRING_VALUE, sysConfig.getProperty(MOCK_KEY, MOCK_STRING_VALUE));
    }

    /**
     * Test convert.
     */
    @Test
    public void testConvert() {
        // 返回默认值
        Assertions.assertEquals(
            MOCK_STRING_VALUE, sysConfig.convert(String.class, NOT_EXIST_KEY, MOCK_STRING_VALUE));

        // 不同类型的变量值MOCK_XX_VALUE且都是String.valueOf转化过了的，这就说明setProperty的kv都必须是String类型
        System.setProperty(MOCK_KEY, String.valueOf(MOCK_BOOL_VALUE));
        // 进去
        Assertions.assertEquals(MOCK_BOOL_VALUE, sysConfig.convert(Boolean.class, MOCK_KEY, null));

        System.setProperty(MOCK_KEY, String.valueOf(MOCK_STRING_VALUE));
        // 进去
        Assertions.assertEquals(MOCK_STRING_VALUE, sysConfig.convert(String.class, MOCK_KEY, null));

        System.setProperty(MOCK_KEY, String.valueOf(MOCK_INT_VALUE));
        Assertions.assertEquals(MOCK_INT_VALUE, sysConfig.convert(Integer.class, MOCK_KEY, null));

        System.setProperty(MOCK_KEY, String.valueOf(MOCK_LONG_VALUE));
        Assertions.assertEquals(MOCK_LONG_VALUE, sysConfig.convert(Long.class, MOCK_KEY, null));

        System.setProperty(MOCK_KEY, String.valueOf(MOCK_SHORT_VALUE));
        Assertions.assertEquals(MOCK_SHORT_VALUE, sysConfig.convert(Short.class, MOCK_KEY, null));

        System.setProperty(MOCK_KEY, String.valueOf(MOCK_FLOAT_VALUE));
        Assertions.assertEquals(MOCK_FLOAT_VALUE, sysConfig.convert(Float.class, MOCK_KEY, null));

        System.setProperty(MOCK_KEY, String.valueOf(MOCK_DOUBLE_VALUE));
        Assertions.assertEquals(MOCK_DOUBLE_VALUE, sysConfig.convert(Double.class, MOCK_KEY, null));

        System.setProperty(MOCK_KEY, String.valueOf(MOCK_BYTE_VALUE));
        Assertions.assertEquals(MOCK_BYTE_VALUE, sysConfig.convert(Byte.class, MOCK_KEY, null));

        System.setProperty(MOCK_KEY, String.valueOf(ConfigMock.MockOne));
        Assertions.assertEquals(ConfigMock.MockOne, sysConfig.convert(ConfigMock.class, MOCK_KEY, null));
    }

    /**
     * Clean.
     */
    @AfterEach
    public void clean() {
        if (null != System.getProperty(MOCK_KEY)) {
            System.clearProperty(MOCK_KEY);
        }
    }

    /**
     * The enum Config mock.
     */
    enum ConfigMock {
        /**
         * Mock one config mock.
         */
        MockOne,
        /**
         * Mock two config mock.
         */
        MockTwo
    }

}
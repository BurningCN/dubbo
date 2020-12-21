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
package org.apache.dubbo.common.convert;

import org.junit.jupiter.api.Test;

import static org.apache.dubbo.common.convert.Converter.convertIfPossible;
import static org.apache.dubbo.common.convert.Converter.getConverter;
import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link Converter} Test-Cases
 *
 * @since 2.7.8
 */
// OK
public class ConverterTest {

    // 先看第二个测试方法，再看下面这个
    @Test
    public void testGetConverter() {
        // 注意多了test程序的三个扩展类（带2的）:String2BooleanConverter、String2DoubleConverter、String2IntegerConverter
        getExtensionLoader(Converter.class)
                .getSupportedExtensionInstances()
                .forEach(converter -> {
                    assertSame(converter, getConverter(converter.getSourceType(), converter.getTargetType()));
                });
    }

    @Test
    public void testConvertIfPossible() {
        // 进去
        assertEquals(Integer.valueOf(2), convertIfPossible("2", Integer.class));
        assertEquals(Boolean.FALSE, convertIfPossible("false", Boolean.class));
        assertEquals(Double.valueOf(1), convertIfPossible("1", Double.class));
    }
}

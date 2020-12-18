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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.extension.compatible.CompatibleExt;
import org.apache.dubbo.common.extension.compatible.impl.CompatibleExtImpl1;
import org.apache.dubbo.common.extension.compatible.impl.CompatibleExtImpl2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

// OK
public class ExtensionLoader_Compatible_Test {


    @Test
    public void test_getExtension() throws Exception {
        // 注意下CompatibleExtImpl1的类头有一个@Extension("impl1")注解，这个是废弃的/过时的注解，当做扩展名使用的
        // 即如果发现SPI文件只有v，没有k=的时候（没有扩展名），那么就会先看看有没有@Extension("yyy")，有的话扩展名就是yyy，
        // 没有的话根据类名构建一个扩展名（比如实现类是xxxType，那么扩展名就是xxx）--->详见findAnnotationName方法
        assertTrue(ExtensionLoader.getExtensionLoader(CompatibleExt.class).getExtension("impl1") instanceof CompatibleExtImpl1);
        assertTrue(ExtensionLoader.getExtensionLoader(CompatibleExt.class).getExtension("impl2") instanceof CompatibleExtImpl2);
    }
}
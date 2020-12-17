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

import org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt;
import org.apache.dubbo.common.utils.IOUtils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdaptiveClassCodeGenerator} Test
 *
 * @since 2.7.5
 */
// OK
public class AdaptiveClassCodeGeneratorTest {

    @Test
    public void testGenerate() throws IOException {
        // 注意利用AdaptiveClassCodeGenerator生成自适应扩展类，有一些条件和注意点

        // 1.保证传入的接口比如这里的HasAdaptiveExt里面至少含有一个带@Adaptive注解标记的方法(不带标记的内部会生成抛异常代码)

        // 2.在上面条件的前提下，方法必须带有URL类型参数或者方法参数内部有getUrl等方法

        // 3-(1)@Adaptive注解有值的话，比如 @Adaptive({Constants.EXCHANGER_KEY})，那么就以Constants.EXCHANGER_KEY做为key，如果没有值那么
        //      就把SPI接口名按照驼峰解析，比如HasAdaptiveExt解析为has.adaptive.ext，把这个做为key。
        // 3-(2)然后String extName=url.getParameter(key,defaultValue)，这里的defaultValue就比如
        //      下面方法的第二个参数值adaptive，返回的extName，用以获取扩展类，HasAdaptiveExt xx = ExtensionLoader.getExtensionLoader(HasAdaptiveExt.class).getExtension(extName);
        AdaptiveClassCodeGenerator generator = new AdaptiveClassCodeGenerator(HasAdaptiveExt.class, "adaptive");
        // 进去
        String value = generator.generate();
        URL url = getClass().getResource("/org/apache/dubbo/common/extension/adaptive/HasAdaptiveExt$Adaptive");
        try (InputStream inputStream = url.openStream()) {
            // 字节流转字符流，read进去
            String content = IOUtils.read(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            // in Windows platform content get from resource contains \r delimiter
            content = content.replaceAll("\r", "");
            assertTrue(content.contains(value));
        }

        // 格式化下
        /*
        package org.apache.dubbo.common.extension.adaptive;
        import org.apache.dubbo.common.extension.ExtensionLoader;
        public class HasAdaptiveExt$Adaptive implements org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt {
            public java.lang.String echo(org.apache.dubbo.common.URL arg0, java.lang.String arg1) {
                if (arg0 == null)
                    throw new IllegalArgumentException("url == null");
                org.apache.dubbo.common.URL url = arg0;
                String extName = url.getParameter("has.adaptive.ext", "adaptive");
                if (extName == null)
                    throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt) name from url (" + url.toString() + ") use keys([has.adaptive.ext])");
                org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt extension =
                        (org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt) ExtensionLoader.getExtensionLoader(
                                org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt.class).getExtension(extName);
                return extension.echo(arg0, arg1);
            }
        }
         */
    }
}

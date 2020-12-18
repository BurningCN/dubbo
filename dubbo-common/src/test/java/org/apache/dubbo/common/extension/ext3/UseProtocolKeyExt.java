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
package org.apache.dubbo.common.extension.ext3;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

@SPI("impl1")
public interface UseProtocolKeyExt {
    // protocol key is the second
    @Adaptive({"key1", "protocol"})
    String echo(URL url, String s);

    // protocol key is the first
    @Adaptive({"protocol", "key2"})
    String yell(URL url, String s);
}
// 自动生成的自适应类源码如下
/*
    package org.apache.dubbo.common.extension.ext3;
    import org.apache.dubbo.common.extension.ExtensionLoader;
    public class UseProtocolKeyExt$Adaptive implements org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt {

    public java.lang.String yell(org.apache.dubbo.common.URL arg0, java.lang.String arg1)  {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg0;

        // 注意下面这行，具体可以看方法generateExtNameAssignment，是把@Activate的值赋值给String[] value数组，并从后向前遍历的，所以是下面那这个德行
        String extName = url.getProtocol() == null ? (url.getParameter("key2", "impl1")) : url.getProtocol();
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt) name from url (" + url.toString() + ") use keys([protocol, key2])");
        org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt extension = (org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt)ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt.class).getExtension(extName);
        return extension.yell(arg0, arg1);
    }

    public java.lang.String echo(org.apache.dubbo.common.URL arg0, java.lang.String arg1)  {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg0;

        // 注意下面这行
        String extName = url.getParameter("key1", ( url.getProtocol() == null ? "impl1" : url.getProtocol() ));
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt) name from url (" + url.toString() + ") use keys([key1, protocol])");
        org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt extension = (org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt)ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt.class).getExtension(extName);
        return extension.echo(arg0, arg1);
    }
    }*/
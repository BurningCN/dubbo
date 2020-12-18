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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt;
import org.apache.dubbo.common.extension.adaptive.impl.HasAdaptiveExt_ManualAdaptive;
import org.apache.dubbo.common.extension.ext1.SimpleExt;
import org.apache.dubbo.common.extension.ext2.Ext2;
import org.apache.dubbo.common.extension.ext2.UrlHolder;
import org.apache.dubbo.common.extension.ext3.UseProtocolKeyExt;
import org.apache.dubbo.common.extension.ext4.NoUrlParamExt;
import org.apache.dubbo.common.extension.ext5.NoAdaptiveMethodExt;
import org.apache.dubbo.common.extension.ext6_inject.Ext6;
import org.apache.dubbo.common.extension.ext6_inject.impl.Ext6Impl2;
import org.apache.dubbo.common.utils.LogUtil;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// OK
public class ExtensionLoader_Adaptive_Test {

    // √
    @Test
    public void test_useAdaptiveClass() throws Exception {
        ExtensionLoader<HasAdaptiveExt> loader = ExtensionLoader.getExtensionLoader(HasAdaptiveExt.class);
        // 本身就有自适应扩展类，HasAdaptiveExt_ManualAdaptive
        HasAdaptiveExt ext = loader.getAdaptiveExtension();
        assertTrue(ext instanceof HasAdaptiveExt_ManualAdaptive);
    }

    // √
    @Test
    public void test_getAdaptiveExtension_defaultAdaptiveKey() throws Exception {
        {
            // 注意这个是自动生成的 SimpleExt$Adaptive
            SimpleExt ext = ExtensionLoader.getExtensionLoader(SimpleExt.class).getAdaptiveExtension();

            Map<String, String> map = new HashMap<String, String>();
            URL url = new URL("p1", "1.2.3.4", 1010, "path1", map);

            // 自动生成的自适应扩展类的echo方法是大概这样的，String extName = url.getParameter("simple.ext",impl1)
            // 因为url没有simple.ext参数，所以extName = impl1，然后ExtensionLoader.getExtensionLoader(SimpleExt.class).getExtension("impl1")
            // 返回的肯定是SimpleExtImpl1扩展子类，然后调用其echo方法
            String echo = ext.echo(url, "haha");
            assertEquals("Ext1Impl1-echo", echo);
        }

        {
            SimpleExt ext = ExtensionLoader.getExtensionLoader(SimpleExt.class).getAdaptiveExtension();

            Map<String, String> map = new HashMap<String, String>();
            // 多了这个kv
            map.put("simple.ext", "impl2");
            URL url = new URL("p1", "1.2.3.4", 1010, "path1", map);

            // 前面说过了，此时url.getParameter("simple.ext")肯定能取出来了，此时extName = impl2，取出来的是Ext1Impl2实例.......
            String echo = ext.echo(url, "haha");
            assertEquals("Ext1Impl2-echo", echo);
        }
    }


    /*
    package org.apache.dubbo.common.extension.ext1;
    import org.apache.dubbo.common.extension.ExtensionLoader;

    public class SimpleExt$Adaptive implements org.apache.dubbo.common.extension.ext1.SimpleExt {
        public java.lang.String bang(org.apache.dubbo.common.URL arg0, int arg1) {
            throw new UnsupportedOperationException("The method public abstract java.lang.String org.apache.dubbo.common.extension.ext1.SimpleExt.bang(org.apache.dubbo.common.URL,int) of interface org.apache.dubbo.common.extension.ext1.SimpleExt is not adaptive method!");
        }

        public java.lang.String yell(org.apache.dubbo.common.URL arg0, java.lang.String arg1) {
            if (arg0 == null) throw new IllegalArgumentException("url == null");
            org.apache.dubbo.common.URL url = arg0;
            // 根据@Adaptive注解里的值来的
            String extName = url.getParameter("key1", url.getParameter("key2", "impl1"));
            if (extName == null)
                throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.ext1.SimpleExt) name from url (" + url.toString() + ") use keys([key1, key2])");
            org.apache.dubbo.common.extension.ext1.SimpleExt extension = (org.apache.dubbo.common.extension.ext1.SimpleExt) ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.ext1.SimpleExt.class).getExtension(extName);
            return extension.yell(arg0, arg1);
        }

        public java.lang.String echo(org.apache.dubbo.common.URL arg0, java.lang.String arg1) {
            if (arg0 == null) throw new IllegalArgumentException("url == null");
            org.apache.dubbo.common.URL url = arg0;
            String extName = url.getParameter("simple.ext", "impl1");
            if (extName == null)
                throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.ext1.SimpleExt) name from url (" + url.toString() + ") use keys([simple.ext])");
            org.apache.dubbo.common.extension.ext1.SimpleExt extension = (org.apache.dubbo.common.extension.ext1.SimpleExt) ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.ext1.SimpleExt.class).getExtension(extName);
            return extension.echo(arg0, arg1);
        }
    }
    */

    // √
    @Test
    public void test_getAdaptiveExtension_customizeAdaptiveKey() throws Exception {
        // 扩展类源码看上面大块注释
        SimpleExt ext = ExtensionLoader.getExtensionLoader(SimpleExt.class).getAdaptiveExtension();

        Map<String, String> map = new HashMap<String, String>();
        // 注意这里的key2
        map.put("key2", "impl2");
        URL url = new URL("p1", "1.2.3.4", 1010, "path1", map);

        // 去看下接口的yell方法，上面的注解是含有值的，我们说过在生成code str(自适应源代码)的时候，generateMethodContent方法内部会
        // 获取 Adaptive 注解值，如果没有的话，那么就是对类名进行转化（比如AddExt1，返回的就是add.ext1）赋值给String[] value属性，
        // 这个value会作为key从url获取值，赋值给extName，比如：String extName = url.getParameter("[add.ext1]/[Adaptive的注解值]", "adaptive");
        // yell上面的注解是含有值的，所以会生成  String extName = url.getParameter("key1", url.getParameter("key2", "impl1"));

        // 注意啊！这里的ext一直是SimpleExt$Adaptive类，只是里面的yell方法会获取到Ext1Impl2实例（上面一段话）并调用其yell方法
        String echo = ext.yell(url, "haha");
        assertEquals("Ext1Impl2-yell", echo);

        // 和前面一样，只是此时获取了Ext1Impl3
        url = url.addParameter("key1", "impl3"); // note: URL is value's type
        echo = ext.yell(url, "haha");
        assertEquals("Ext1Impl3-yell", echo);
    }


    // √
    @Test
    public void test_getAdaptiveExtension_protocolKey() throws Exception {
        // 看下UseProtocolKeyExt接口，注意带有@Adaptive注解的值
        UseProtocolKeyExt ext = ExtensionLoader.getExtensionLoader(UseProtocolKeyExt.class).getAdaptiveExtension();

        {
            // 自适应类echo方法内部某行代码如下:
            // String extName = url.getParameter("key1", ( url.getProtocol() == null ? "impl1" : url.getProtocol() ));

            // 调用echo的时候内部肯定extName = impl1
            String echo = ext.echo(URL.valueOf("1.2.3.4:20880"), "s");
            assertEquals("Ext3Impl1-echo", echo); // default value

            Map<String, String> map = new HashMap<String, String>();
            URL url = new URL("impl3", "1.2.3.4", 1010, "path1", map);

            // extName = url.getProtocol()  = impl3
            echo = ext.echo(url, "s");
            assertEquals("Ext3Impl3-echo", echo); // use 2nd key, protocol

            // extName = url.getParameter(key1) = impl2
            url = url.addParameter("key1", "impl2");
            echo = ext.echo(url, "s");
            assertEquals("Ext3Impl2-echo", echo); // use 1st key, key1
        }

        // 下面调用自适应类的yell方法测试
        {

            // 自适应类echo方法内部某行代码如下:
            // String extName = url.getProtocol() == null ? (url.getParameter("key2", "impl1")) : url.getProtocol();

            Map<String, String> map = new HashMap<String, String>();
            URL url = new URL(null, "1.2.3.4", 1010, "path1", map);
            // extName = impl1
            String yell = ext.yell(url, "s");
            assertEquals("Ext3Impl1-yell", yell); // default value

            url = url.addParameter("key2", "impl2"); // use 2nd key, key2
            // extName = url.getParameter("key2", "impl1") = impl2;
            yell = ext.yell(url, "s");
            assertEquals("Ext3Impl2-yell", yell);

            url = url.setProtocol("impl3"); // use 1st key, protocol
            // extName = url.getProtocol() = impl3
            yell = ext.yell(url, "d");
            assertEquals("Ext3Impl3-yell", yell);
        }
    }

    // √ 测试url npe 空指针异常
    @Test
    public void test_getAdaptiveExtension_UrlNpe() throws Exception {
        SimpleExt ext = ExtensionLoader.getExtensionLoader(SimpleExt.class).getAdaptiveExtension();

        try {
            // SimpleExt$Adaptive自适应类echo方法内部有一行 if (arg0 == null) throw new IllegalArgumentException("url == null");
            ext.echo(null, "haha");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("url == null", e.getMessage());
        }
    }

    // √
    @Test
    public void test_getAdaptiveExtension_ExceptionWhenNoAdaptiveMethodOnInterface() throws Exception {
        try {
            ExtensionLoader.getExtensionLoader(NoAdaptiveMethodExt.class).getAdaptiveExtension();
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(),
                    allOf(containsString("Can't create adaptive extension interface org.apache.dubbo.common.extension.ext5.NoAdaptiveMethodExt"),
                            // 注意这里的日志（创建自适应扩展类必须保证SPI接口至少有一个方法含有@Adaptive注解）
                            containsString("No adaptive method exist on extension org.apache.dubbo.common.extension.ext5.NoAdaptiveMethodExt, refuse to create the adaptive class")));
        }
        // report same error when get is invoked for multiple times
        try {
            ExtensionLoader.getExtensionLoader(NoAdaptiveMethodExt.class).getAdaptiveExtension();
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(),
                    allOf(containsString("Can't create adaptive extension interface org.apache.dubbo.common.extension.ext5.NoAdaptiveMethodExt"),
                            containsString("No adaptive method exist on extension org.apache.dubbo.common.extension.ext5.NoAdaptiveMethodExt, refuse to create the adaptive class")));
        }
    }

    // √
    @Test
    public void test_getAdaptiveExtension_ExceptionWhenNotAdaptiveMethod() throws Exception {
        SimpleExt ext = ExtensionLoader.getExtensionLoader(SimpleExt.class).getAdaptiveExtension();

        Map<String, String> map = new HashMap<String, String>();
        URL url = new URL("p1", "1.2.3.4", 1010, "path1", map);

        try {
            // 自适应扩展类对不含有@Adaptive注解的方法（比如这里的bang）内部的实现内容直接是 throw new UnsupportedOperationException(xxx)
            ext.bang(url, 33);
            fail();
        } catch (UnsupportedOperationException expected) {
            assertThat(expected.getMessage(), containsString("method "));
            assertThat(
                    expected.getMessage(),
                    // 日志
                    containsString("of interface org.apache.dubbo.common.extension.ext1.SimpleExt is not adaptive method!"));
        }
    }

    // √
    @Test
    public void test_getAdaptiveExtension_ExceptionWhenNoUrlAttribute() throws Exception {
        try {
            // 创建自适应扩展类的又一个条件，带@Adaptive注解的方法，必须含有URL参数，或者参数的类内部有getUrl方法
            // 看下NoUrlParamExt
            ExtensionLoader.getExtensionLoader(NoUrlParamExt.class).getAdaptiveExtension();
            fail();
        } catch (Exception expected) {
            assertThat(expected.getMessage(), containsString("Failed to create adaptive class for interface "));
            // 看这里日志
            assertThat(expected.getMessage(), containsString(": not found url parameter or url attribute in parameters of method "));
        }
    }


    /*

    Ext2的自使用扩展类源码如下：

    public class Ext2$Adaptive implements org.apache.dubbo.common.extension.ext2.Ext2 {
        public java.lang.String echo(org.apache.dubbo.common.extension.ext2.UrlHolder arg0, java.lang.String arg1)  {
            if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.common.extension.ext2.UrlHolder argument == null");
            if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.common.extension.ext2.UrlHolder argument getUrl() == null");
            org.apache.dubbo.common.URL url = arg0.getUrl();

            // ext2的来源是根据接口名Ext2转化的 ，注意接口的SPI注解没有值，所以直接是url.getParameter("ext2");不是url.getParameter("ext2",defaultExtName);
            String extName = url.getParameter("ext2");
            if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.ext2.Ext2) name from url (" + url.toString() + ") use keys([ext2])");
            org.apache.dubbo.common.extension.ext2.Ext2 extension = (org.apache.dubbo.common.extension.ext2.Ext2)ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.ext2.Ext2.class).getExtension(extName);
            return extension.echo(arg0, arg1);
        }
        public java.lang.String bang(org.apache.dubbo.common.URL arg0, int arg1)  {
            throw new UnsupportedOperationException("The method public abstract java.lang.String org.apache.dubbo.common.extension.ext2.Ext2.bang(org.apache.dubbo.common.URL,int) of interface org.apache.dubbo.common.extension.ext2.Ext2 is not adaptive method!");
        }
    }

    */
    // √
    @Test
    public void test_urlHolder_getAdaptiveExtension() throws Exception {
        // Ext2的echo方法参数是UrlHolder，里面有getUrl方法，生成的code str见上面
        Ext2 ext = ExtensionLoader.getExtensionLoader(Ext2.class).getAdaptiveExtension();

        Map<String, String> map = new HashMap<String, String>();
        map.put("ext2", "impl1");
        URL url = new URL("p1", "1.2.3.4", 1010, "path1", map);

        UrlHolder holder = new UrlHolder();
        holder.setUrl(url);

        // 内部extName = url.getParameter("ext2") = impl1
        String echo = ext.echo(holder, "haha");
        assertEquals("Ext2Impl1-echo", echo);
    }

    // √
    @Test
    public void test_urlHolder_getAdaptiveExtension_noExtension() throws Exception {
        Ext2 ext = ExtensionLoader.getExtensionLoader(Ext2.class).getAdaptiveExtension();

        URL url = new URL("p1", "1.2.3.4", 1010, "path1");

        UrlHolder holder = new UrlHolder();
        holder.setUrl(url);

        try {
            // 因为url对象没有添加ext2=xx这个kv参数对，所以内部取出来的extName = null
            ext.echo(holder, "haha");
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("Failed to get extension"));
        }

        url = url.addParameter("ext2", "XXX");
        holder.setUrl(url);
        try {
            // extName = url.getParameter("ext2") = XXX，但是ExtensionLoader没有这个XXX扩展类，肯定也会抛异常
            ext.echo(holder, "haha");
            fail();
        } catch (IllegalStateException expected) {
            // 日志
            assertThat(expected.getMessage(), containsString("No such extension"));
        }
    }

    // √
    @Test
    public void test_urlHolder_getAdaptiveExtension_UrlNpe() throws Exception {
        Ext2 ext = ExtensionLoader.getExtensionLoader(Ext2.class).getAdaptiveExtension();

        try {
            // 没有holder，echo方法内有这行代码
            // if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.common.extension.ext2.UrlHolder argument == null");
            ext.echo(null, "haha");
            fail();
        } catch (IllegalArgumentException e) {
            // 日志
            assertEquals("org.apache.dubbo.common.extension.ext2.UrlHolder argument == null", e.getMessage());
        }

        try {
            // 有holder，但是getUrl为null，肯定也会抛异常，echo方法内有这行代码
            // if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.common.extension.ext2.UrlHolder argument getUrl() == null");
            ext.echo(new UrlHolder(), "haha");
            fail();
        } catch (IllegalArgumentException e) {
            // 日志
            assertEquals("org.apache.dubbo.common.extension.ext2.UrlHolder argument getUrl() == null", e.getMessage());
        }
    }

    // √
    @Test
    public void test_urlHolder_getAdaptiveExtension_ExceptionWhenNotAdativeMethod() throws Exception {
        Ext2 ext = ExtensionLoader.getExtensionLoader(Ext2.class).getAdaptiveExtension();

        Map<String, String> map = new HashMap<String, String>();
        URL url = new URL("p1", "1.2.3.4", 1010, "path1", map);

        try {
            // Ext2的bang方法上面是没有@Adaptive参数的，bang方法内部是如下：
            //        public java.lang.String bang(org.apache.dubbo.common.URL arg0, int arg1)  {
            //            throw new UnsupportedOperationException("The method public abstract java.lang.String org.apache.dubbo.common.extension.ext2.Ext2.bang(org.apache.dubbo.common.URL,int) of interface org.apache.dubbo.common.extension.ext2.Ext2 is not adaptive method!");
            //        }
            ext.bang(url, 33);
            fail();
        } catch (UnsupportedOperationException expected) {
            assertThat(expected.getMessage(), containsString("method "));
            assertThat(
                    expected.getMessage(),
                    containsString("of interface org.apache.dubbo.common.extension.ext2.Ext2 is not adaptive method!"));
        }
    }

    // √
    @Test
    public void test_urlHolder_getAdaptiveExtension_ExceptionWhenNameNotProvided() throws Exception {
        Ext2 ext = ExtensionLoader.getExtensionLoader(Ext2.class).getAdaptiveExtension();

        URL url = new URL("p1", "1.2.3.4", 1010, "path1");

        UrlHolder holder = new UrlHolder();
        holder.setUrl(url);

        try {
            // 自适应类的echo方法内部有String extName = url.getParameter("ext2");（注意没有defaultExtName）
            // 然后getExtension(extName)肯定取不到
            ext.echo(holder, "impl1");
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("Failed to get extension"));
        }

        // 添加这个参数没啥用，因为是url.getParameter("ext2");都没关注key1
        url = url.addParameter("key1", "impl1");
        holder.setUrl(url);
        try {
            ext.echo(holder, "haha");
            fail();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), containsString("Failed to get extension (org.apache.dubbo.common.extension.ext2.Ext2) name from url"));
        }
    }

    /*
    Ext6的自适应扩展类如下：

    package org.apache.dubbo.common.extension.ext6_inject;
    import org.apache.dubbo.common.extension.ExtensionLoader;
    public class Ext6$Adaptive implements org.apache.dubbo.common.extension.ext6_inject.Ext6 {
        public java.lang.String echo(org.apache.dubbo.common.URL arg0, java.lang.String arg1)  {
            if (arg0 == null) throw new IllegalArgumentException("url == null");
            org.apache.dubbo.common.URL url = arg0;
            String extName = url.getParameter("ext6");
            if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.ext6_inject.Ext6) name from url (" + url.toString() + ") use keys([ext6])");
            org.apache.dubbo.common.extension.ext6_inject.Ext6 extension =
                    (org.apache.dubbo.common.extension.ext6_inject.Ext6)ExtensionLoader.getExtensionLoader(
                            org.apache.dubbo.common.extension.ext6_inject.Ext6.class).getExtension(extName);
            return extension.echo(arg0, arg1);
        }
    } */

    // √
    @Test
    public void test_getAdaptiveExtension_inject() throws Exception {
        LogUtil.start();
        // 扩展类源码看上面
        Ext6 ext = ExtensionLoader.getExtensionLoader(Ext6.class).getAdaptiveExtension();

        URL url = new URL("p1", "1.2.3.4", 1010, "path1");
        url = url.addParameters("ext6", "impl1");

        // Ext6$Adaptive#echo内部 extName = url.getParameter(ext6) = impl1,即Ext6Impl1。
        // 而Ext6Impl1是有 setExt1(SimpleExt ext1) 方法的，会inject SimpleExt接口的扩展实例
        // 且注意注入的是自适应扩展类实例，即SimpleExt$Adaptive，正好本身SimpleExt接口的方法也是带有@Adaptive注解，肯定能自动创建成功。
        // 特别要注意这点：dubbo的spi进行inject/ioc的时候都是注入的自适应扩展类实例，详见SpiExtensionFactory
        assertEquals("Ext6Impl1-echo-Ext1Impl1-echo", ext.echo(url, "ha"));

        Assertions.assertTrue(LogUtil.checkNoError(), "can not find error.");
        LogUtil.stop();

        url = url.addParameters("simple.ext", "impl2");
        // Ext6$Adaptive#echo方法内部，最后一行会调用Ext6Impl1类对象的echo方法，内部又会调用SimpleExt#echo(url,s)方法，
        // SimpleExt实际是SimpleExt$Adaptive，其echo再根据传来的url...取出SimpleExt的impl2扩展名实例，即这里的Ext1Impl2
        // 所以输出是[Ext6Impl1-echo]-[Ext1Impl2-echo]
        assertEquals("Ext6Impl1-echo-Ext1Impl2-echo", ext.echo(url, "ha"));

    }

    // √
    @Test
    public void test_getAdaptiveExtension_InjectNotExtFail() throws Exception {
        // Ext6Impl2的setList方法的参数类型虽然不是原生类型(详见Reflecttion#isPrimitives方法)，但是SPI文件没有list=xxx的配置(list是Ext6Impl2的属性名)
        // 所以无法注入依赖的扩展类实例
        Ext6 ext = ExtensionLoader.getExtensionLoader(Ext6.class).getExtension("impl2");

        Ext6Impl2 impl = (Ext6Impl2) ext;
        assertNull(impl.getList());
    }

}

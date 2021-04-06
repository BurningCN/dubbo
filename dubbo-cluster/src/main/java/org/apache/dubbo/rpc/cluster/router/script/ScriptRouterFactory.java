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
package org.apache.dubbo.rpc.cluster.router.script;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.cluster.Router;
import org.apache.dubbo.rpc.cluster.RouterFactory;

import java.util.List;
import java.util.Set;

/**
 * ScriptRouterFactory
 * <p>
 * Example URLS used by Script Router Factory：
 * <ol>
 * <li> script://registryAddress?type=js&rule=xxxx
 * <li> script:///path/to/routerfile.js?type=js&rule=xxxx
 * <li> script://D:\path\to\routerfile.js?type=js&rule=xxxx
 * <li> script://C:/path/to/routerfile.js?type=js&rule=xxxx
 * </ol>
 * The host value in URL points out the address of the source content of the Script Router，Registry、File etc
 * URL中的host值指出脚本路由器、注册表、文件等的源内容的地址
 *
 */
// OK
public class ScriptRouterFactory implements RouterFactory {

    public static final String NAME = "script";

    @Override
    public Router getRouter(URL url) {
        return new ScriptRouter(url);
    }

    // 下面演示了有的spi扩展实例没有@Activate修饰，有的有，他们在使用不同的api方法才产生区别的，如下
    public static void main(String[] args) {
        Set<String> supportedExtensions = ExtensionLoader.getExtensionLoader(RouterFactory.class).getSupportedExtensions();
        List<RouterFactory> xx = ExtensionLoader.getExtensionLoader(RouterFactory.class).getActivateExtension(URL.valueOf("test:///xxx"), "xx");
        // supportedExtensions = {Collections$UnmodifiableSet@905}  size = 7
        // 0 = "app"
        // 1 = "condition"
        // 2 = "file"
        // 3 = "mock"
        // 4 = "script"
        // 5 = "service"
        // 6 = "tag"
        //
        //
        //
        //xx = {ArrayList@911}  size = 4
        // 0 = {MockRouterFactory@921}
        // 1 = {TagRouterFactory@922}
        // 2 = {AppRouterFactory@923}
        // 3 = {ServiceRouterFactory@924}
    }
}

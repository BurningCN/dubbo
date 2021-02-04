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
package my.common.lang;

import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static java.util.Collections.sort;
import static org.apache.dubbo.common.function.ThrowableAction.execute;

/**
 * The compose {@link ShutdownHookCallback} class to manipulate one and more {@link ShutdownHookCallback} instances
 *
 * @since 2.7.5
 */
// OK
// 上面英文注解很好懂
public class ShutdownHookCallbacks {

    // static修饰 单例
    public static final ShutdownHookCallbacks INSTANCE = new ShutdownHookCallbacks();

    // callback列表
    private final List<ShutdownHookCallback> callbacks = new LinkedList<>();

    // default默认级别，外界不能直接new，确保单例
    ShutdownHookCallbacks() {
        // 进去
        loadCallbacks();
    }

    // gx
    public ShutdownHookCallbacks addCallback(ShutdownHookCallback callback) {
        // sync锁
        synchronized (this) {
            this.callbacks.add(callback);
        }
        // 返回this用以链式调用
        return this;
    }

    // gx
    public Collection<ShutdownHookCallback> getCallbacks() {
        synchronized (this) {
            // 排个序
            sort(this.callbacks);
            return this.callbacks;
        }
    }

    public void clear() {
        synchronized (this) {
            callbacks.clear();
        }
    }

    // gx
    private void loadCallbacks() {
        // 获取ShutdownHookCallback加载器，看下ShutdownHookCallback接口
        // ExtensionLoader类是多实例的，不是所有type公用的，注意了。一个type（@SPI接口）一个ExtensionLoader。
        ExtensionLoader<ShutdownHookCallback> loader =
                ExtensionLoader.getExtensionLoader(ShutdownHookCallback.class);
        // 获取所有实例，并调用addCallback（其实就是获取根据SPI机制ShutdownHookCallback的子类，存到该类的list容器）
        //  目前测试程序有一个，可以看下ShutdownHookCallback的PSI文件
        loader.getSupportedExtensionInstances().forEach(this::addCallback);
    }

    public void callback() {
        getCallbacks().forEach(callback -> execute(callback::callback));
    }
}
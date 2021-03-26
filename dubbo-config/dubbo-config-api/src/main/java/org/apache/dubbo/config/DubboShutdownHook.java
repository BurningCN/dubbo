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

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.lang.ShutdownHookCallbacks;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.config.event.DubboServiceDestroyedEvent;
import org.apache.dubbo.config.event.DubboShutdownHookRegisteredEvent;
import org.apache.dubbo.config.event.DubboShutdownHookUnregisteredEvent;
import org.apache.dubbo.event.Event;
import org.apache.dubbo.event.EventDispatcher;
import org.apache.dubbo.registry.support.AbstractRegistryFactory;
import org.apache.dubbo.rpc.Protocol;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The shutdown hook thread to do the clean up stuff.
 * This is a singleton in order to ensure there is only one shutdown hook registered.
 * Because {@link //ApplicationShutdownHooks} use {@link java.util.IdentityHashMap}
 * to store the shutdown hooks.
 */
// OK
public class DubboShutdownHook extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(DubboShutdownHook.class);

    // 线程对象单例模式（饿汉），进去
    private static final DubboShutdownHook DUBBO_SHUTDOWN_HOOK = new DubboShutdownHook("DubboShutdownHook");

    // 回调对象单例模式（饿汉），进去  注意这里没有static修饰，上面的有，想想为什么
    private final ShutdownHookCallbacks callbacks = ShutdownHookCallbacks.INSTANCE;

    /**
     * Has it already been registered or not?
     */
    private final AtomicBoolean registered = new AtomicBoolean(false);

    /**
     * Has it already been destroyed or not?
     */
    private static final AtomicBoolean destroyed = new AtomicBoolean(false);

    private final EventDispatcher eventDispatcher = EventDispatcher.getDefaultExtension();

    // 单例模式-需要设计成私有构造防止外部直接new
    private DubboShutdownHook(String name) {
        super(name);
    }

    // 单例模式-给外界访问内部实例的公有方法
    public static DubboShutdownHook getDubboShutdownHook() {
        return DUBBO_SHUTDOWN_HOOK;
    }

    @Override
    public void run() {
        if (logger.isInfoEnabled()) {
            logger.info("Run shutdown hook now.");
        }
        // 该钩子的主要作用就是在jvm关闭的时候触发回调函数以及销毁一些资源，只是doDestroy目前来看仅仅是派发了事件，而该类的destroyAll方法调用是通过
        // 注册callback（在callback内部调用的），可以跟下后面register方法的调用点就知道了

        // 进去
        callback();
        // 进去
        doDestroy();
    }

    /**
     * For testing purpose
     */
    void clear() {
        callbacks.clear();
    }

    private void callback() {
        // callbacks去看下
        callbacks.callback();
    }

    /**
     * Register the ShutdownHook
     */
    // gx
    public void register() {
        // 只注册一次
        if (registered.compareAndSet(false, true)) {
            // 进去
            DubboShutdownHook dubboShutdownHook = getDubboShutdownHook();
            // 添加dubboShutdownHook，addShutdownHook本身就需要一个线程对象，然后再jvm关闭的时候会调用其run方法。去看run
            Runtime.getRuntime().addShutdownHook(dubboShutdownHook);
            // 派遣、发送"DubboShutdownHook Registered Event"事件，进去
            dispatch(new DubboShutdownHookRegisteredEvent(dubboShutdownHook));
        }
    }

    /**
     * Unregister the ShutdownHook
     */
    public void unregister() {
        // 对比上面的方法
        if (registered.compareAndSet(true, false)) {
            DubboShutdownHook dubboShutdownHook = getDubboShutdownHook();
            Runtime.getRuntime().removeShutdownHook(dubboShutdownHook);
            dispatch(new DubboShutdownHookUnregisteredEvent(dubboShutdownHook));
        }
    }

    /**
     * Destroy all the resources, including registries and protocols.
     */
    public void doDestroy() {
        // dispatch the DubboDestroyedEvent @since 2.7.5
        dispatch(new DubboServiceDestroyedEvent(this));
    }

    private void dispatch(Event event) {
        // eventDispatcher是DirectEventDispatcher类实例，dispatch进去
        eventDispatcher.dispatch(event);
    }

    public boolean getRegistered() {
        return registered.get();
    }

    public static void destroyAll() {
        if (destroyed.compareAndSet(false, true)) {
            // 两个都进去看下

            AbstractRegistryFactory.destroyAll();
            destroyProtocols();
        }
    }

    /**
     * Destroy all the protocols.
     */
    public static void destroyProtocols() {
        ExtensionLoader<Protocol> loader = ExtensionLoader.getExtensionLoader(Protocol.class);
        for (String protocolName : loader.getLoadedExtensions()) {
            try {
                Protocol protocol = loader.getLoadedExtension(protocolName);
                if (protocol != null) {
                    protocol.destroy();
                }
            } catch (Throwable t) {
                logger.warn(t.getMessage(), t);
            }
        }
    }
}

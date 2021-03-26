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
package org.apache.dubbo.event;

import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Collections.sort;
import static java.util.Collections.unmodifiableList;
import static org.apache.dubbo.event.EventListener.findEventType;

/**
 * The abstract {@link EventDispatcher} providers the common implementation.
 *
 * @see EventDispatcher
 * @see Listenable
 * @see ServiceLoader
 * @see EventListener
 * @see Event
 * @since 2.7.5
 */
// OK
public abstract class AbstractEventDispatcher implements EventDispatcher {

    private final Object mutex = new Object();

    private final ConcurrentMap<Class<? extends Event>, List<EventListener>> listenersCache = new ConcurrentHashMap<>();

    private final Executor executor;

    /**
     * Constructor with an instance of {@link Executor}
     *
     * @param executor {@link Executor}
     * @throws NullPointerException <code>executor</code> is <code>null</code>
     */
    protected AbstractEventDispatcher(Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor must not be null");
        }
        this.executor = executor;
        // 进去
        this.loadEventListenerInstances();
    }

    @Override
    public void addEventListener(EventListener<?> listener) throws NullPointerException, IllegalArgumentException {
        Listenable.assertListener(listener);
        // 第二个参数是Consumer（listeners是入参，是一个容器），doInListener进去
        doInListener(listener, listeners -> {
            // 把listener添加到listeners
            addIfAbsent(listeners, listener);
        });
    }

    @Override
    public void removeEventListener(EventListener<?> listener) throws NullPointerException, IllegalArgumentException {
        Listenable.assertListener(listener);
        doInListener(listener, listeners -> listeners.remove(listener));
    }

    @Override
    public List<EventListener<?>> getAllEventListeners() {
        List<EventListener<?>> listeners = new LinkedList<>();

        sortedListeners().forEach(listener -> {
            addIfAbsent(listeners, listener);
        });

        return unmodifiableList(listeners);
    }

    protected Stream<EventListener> sortedListeners() {
        return sortedListeners(e -> true);
    }

    protected Stream<EventListener> sortedListeners(Predicate<Map.Entry<Class<? extends Event>, List<EventListener>>> predicate) {

        // 看Predicate<>泛型就知道一个Event有多个监听器（保存在listenersCache容器中，如下）
        return listenersCache
                .entrySet()
                .stream()
                .filter(predicate)
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .sorted();
    }

    private <E> void addIfAbsent(Collection<E> collection, E element) {
        if (!collection.contains(element)) {
            collection.add(element);
        }
    }

    @Override
    public void dispatch(Event event) {

        // 进去
        Executor executor = getExecutor();

        // execute in sequential or parallel execution model
        // 按顺序或并行执行模型执行，取决于使用DirectEventDispatcher还是ParallelEventDispatcher，两个传了不同的线程池
        executor.execute(() -> {
            // 参数是predicate，此predicate表示从listenersCache容器取出关注event的那些监听器，进去
            sortedListeners(entry -> entry.getKey().isAssignableFrom(event.getClass()))
                    .forEach(listener -> {
                        // 是否是条件化的监听器，是的话，调用其accpet方法看是否接受这种event
                        if (listener instanceof ConditionalEventListener) {
                            ConditionalEventListener predicateEventListener = (ConditionalEventListener) listener;
                            if (!predicateEventListener.accept(event)) { // No accept
                                return;
                            }
                        }
                        // Handle the event 关键点
                        listener.onEvent(event);
                    });
        });
    }

    /**
     * @return the non-null {@link Executor}
     */
    @Override
    public final Executor getExecutor() {
        // 这个是在构造方法传进来赋值的，而构造方法的调用是DirectEventDispatch的构造函数super(DIRECT_EXECUTOR);传进来一个Runnable::run 线程池
        return executor;
    }

    protected void doInListener(EventListener<?> listener, Consumer<Collection<EventListener>> consumer) {
        // 进去
        Class<? extends Event> eventType = findEventType(listener);
        if (eventType != null) {
            synchronized (mutex) {
                List<EventListener> listeners = listenersCache.computeIfAbsent(eventType, e -> new LinkedList<>());
                // consume
                consumer.accept(listeners);
                // sort
                sort(listeners);
            }
        }
    }

    /**
     * Default, load the instances of {@link EventListener event listeners} by {@link ServiceLoader}
     * <p>
     * It could be override by the sub-class
     *
     * @see EventListener
     * @see ServiceLoader#load(Class)
     */
    protected void loadEventListenerInstances() {
        ExtensionLoader<EventListener> loader = ExtensionLoader.getExtensionLoader(EventListener.class);
        // 获取所有的事件监听器扩展类实例，调用addEventListener添加到listenersCache容器
        loader.getSupportedExtensionInstances().forEach(this::addEventListener);
    }
}

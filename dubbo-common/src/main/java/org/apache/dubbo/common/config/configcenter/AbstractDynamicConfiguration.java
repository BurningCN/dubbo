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
package org.apache.dubbo.common.config.configcenter;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * The abstract implementation of {@link DynamicConfiguration}
 *
 * @since 2.7.5
 */
// OK
public abstract class AbstractDynamicConfiguration implements DynamicConfiguration {

    public static final String PARAM_NAME_PREFIX = "dubbo.config-center.";

    public static final String THREAD_POOL_PREFIX_PARAM_NAME = PARAM_NAME_PREFIX + "thread-pool.prefix";

    public static final String DEFAULT_THREAD_POOL_PREFIX = PARAM_NAME_PREFIX + "workers";

    public static final String THREAD_POOL_SIZE_PARAM_NAME = PARAM_NAME_PREFIX + "thread-pool.size";

    /**
     * The keep alive time in milliseconds for threads in {@link ThreadPoolExecutor}
     */
    public static final String THREAD_POOL_KEEP_ALIVE_TIME_PARAM_NAME = PARAM_NAME_PREFIX + "thread-pool.keep-alive-time";

    /**
     * The parameter name of group for config-center
     *
     * @since 2.7.8
     */
    public static final String GROUP_PARAM_NAME = PARAM_NAME_PREFIX + GROUP_KEY;

    /**
     * The parameter name of timeout for config-center
     *
     * @since 2.7.8
     */
    public static final String TIMEOUT_PARAM_NAME = PARAM_NAME_PREFIX + TIMEOUT_KEY;

    public static final int DEFAULT_THREAD_POOL_SIZE = 1;

    /**
     * Default keep alive time in milliseconds for threads in {@link ThreadPoolExecutor} is 1 minute( 60 * 1000 ms)
     */
    // 这种api记住，就不需要自己 60*1000了
    public static final long DEFAULT_THREAD_POOL_KEEP_ALIVE_TIME = TimeUnit.MINUTES.toMillis(1);

    /**
     * Logger
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * The thread pool for workers who executes the tasks
     */
    private final ThreadPoolExecutor workersThreadPool;

    private final String group;

    // 和前面线程池配合。submit返回的future，进行get(timeout)限定超时的
    private final long timeout;

    public AbstractDynamicConfiguration(URL url) {
        // 几个参数都是从url获取，this进去
        // eg:default://?dubbo.config-center.thread-pool.keep-alive-time=100&dubbo.config-center.thread-pool.prefix=test&dubbo.config-center.thread-pool.size=10
        // 上面?后面的截取出来的参数对如下：
        // dubbo.config-center.thread-pool.keep-alive-time=100
        // dubbo.config-center.thread-pool.prefix=test
        // dubbo.config-center.thread-pool.size=10
        // 三个get方法进去看看
        this(getThreadPoolPrefixName(url), getThreadPoolSize(url), getThreadPoolKeepAliveTime(url),
                // 下两个方法进去
                getGroup(url),
                getTimeout(url));
    }

    public AbstractDynamicConfiguration(String threadPoolPrefixName,
                                        int threadPoolSize,
                                        long keepAliveTime,
                                        String group,
                                        long timeout) {
        // 创建工作线程池，进去
        this.workersThreadPool = initWorkersThreadPool(threadPoolPrefixName, threadPoolSize, keepAliveTime);
        // 默认值为dubbo
        this.group = group;
        // 默认值为-1
        this.timeout = timeout;
    }

    @Override
    public void addListener(String key, String group, ConfigurationListener listener) {
    }

    @Override
    public void removeListener(String key, String group, ConfigurationListener listener) {
    }

    @Override
    public final String getConfig(String key, String group, long timeout) throws IllegalStateException {
        // 以key+group为键获取值，execute使用线程去执行这个获取值的任务，且有超时时间，lambda表达式->右边是有返回值的，正好execute方法的参数
        // 就是Callable。execute进去
        return execute(() -> doGetConfig(key, group), timeout);
    }

    @Override
    public Object getInternalProperty(String key) {
        return null;
    }

    @Override
    public final void close() throws Exception {
        try {
            // 子类实现
            doClose();
        } finally {
            // 进去
            doFinally();
        }
    }

    @Override
    public boolean removeConfig(String key, String group) {
        // 使用线程进行移除，移除成功后返回true，execute进去
        return Boolean.TRUE.equals(execute(() -> doRemoveConfig(key, group), -1L));
    }

    /**
     * @return the default group
     * @since 2.7.8
     */
    @Override
    public String getDefaultGroup() {
        return getGroup();
    }

    /**
     * @return the default timeout
     * @since 2.7.8
     */
    @Override
    public long getDefaultTimeout() {
        return getTimeout();
    }

    /**
     * Get the content of configuration in the specified key and group
     *
     * @param key   the key
     * @param group the group
     * @return if found, return the content of configuration
     * @throws Exception If met with some problems
     */
    protected abstract String doGetConfig(String key, String group) throws Exception;

    /**
     * Close the resources if necessary
     *
     * @throws Exception If met with some problems
     */
    protected abstract void doClose() throws Exception;

    /**
     * Remove the config in the specified key and group
     *
     * @param key   the key
     * @param group the group
     * @return If successful, return <code>true</code>, or <code>false</code>
     * @throws Exception
     * @since 2.7.8
     */
    protected abstract boolean doRemoveConfig(String key, String group) throws Exception;

    /**
     * Executes the {@link Runnable} with the specified timeout
     *
     * @param task    the {@link Runnable task}
     * @param timeout timeout in milliseconds
     */
    protected final void execute(Runnable task, long timeout) {
        // runnable的任务，转化为callable
        execute(() -> {
            task.run();
            return null;// 返回null即可
        }, timeout);
    }

    /**
     * Executes the {@link Callable} with the specified timeout
     *
     * @param task    the {@link Callable task}
     * @param timeout timeout in milliseconds
     * @param <V>     the type of computing result
     * @return the computing result
     */
    protected final <V> V execute(Callable<V> task, long timeout) {
        V value = null;
        try {

            if (timeout < 1) {
                // 如果超时时间<=0，那么就表示调用放不需要设置超时，直接使用当前调用者线程执行call即可，一直阻塞
                value = task.call();
            } else {
                Future<V> future = workersThreadPool.submit(task);
                value = future.get(timeout, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error(e.getMessage(), e);
            }
        }
        return value;
    }

    protected ThreadPoolExecutor getWorkersThreadPool() {
        return workersThreadPool;
    }

    private void doFinally() {
        // 进去
        shutdownWorkersThreadPool();
    }

    private void shutdownWorkersThreadPool() {
        if (!workersThreadPool.isShutdown()) {
            workersThreadPool.shutdown();
        }
    }

    protected ThreadPoolExecutor initWorkersThreadPool(String threadPoolPrefixName,
                                                       int threadPoolSize,
                                                       long keepAliveTime) {
        return new ThreadPoolExecutor(threadPoolSize, threadPoolSize, keepAliveTime,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new NamedThreadFactory(threadPoolPrefixName, true));
    }

    protected static String getThreadPoolPrefixName(URL url) {
        // 进去
        return getParameter(url, THREAD_POOL_PREFIX_PARAM_NAME, DEFAULT_THREAD_POOL_PREFIX);
    }

    protected static int getThreadPoolSize(URL url) {
        return getParameter(url, THREAD_POOL_SIZE_PARAM_NAME, DEFAULT_THREAD_POOL_SIZE);
    }

    protected static long getThreadPoolKeepAliveTime(URL url) {
        return getParameter(url, THREAD_POOL_KEEP_ALIVE_TIME_PARAM_NAME, DEFAULT_THREAD_POOL_KEEP_ALIVE_TIME);
    }

    protected static String getParameter(URL url, String name, String defaultValue) {
        if (url != null) {
            return url.getParameter(name, defaultValue);
        }
        return defaultValue;
    }

    protected static int getParameter(URL url, String name, int defaultValue) {
        if (url != null) {
            return url.getParameter(name, defaultValue);
        }
        return defaultValue;
    }

    protected static long getParameter(URL url, String name, long defaultValue) {
        if (url != null) {
            return url.getParameter(name, defaultValue);
        }
        return defaultValue;
    }


    protected String getGroup() {
        return group;
    }

    protected long getTimeout() {
        return timeout;
    }

    /**
     * Get the group from {@link URL the specified connection URL}
     *
     * @param url {@link URL the specified connection URL}
     * @return non-null
     * @since 2.7.8
     */
    protected static String getGroup(URL url) {
        String group = getParameter(url, GROUP_PARAM_NAME, null);
        return StringUtils.isBlank(group) ? getParameter(url, GROUP_KEY, DEFAULT_GROUP) : group;
        // 缺省值为dubbo
    }

    /**
     * Get the timeout from {@link URL the specified connection URL}
     *
     * @param url {@link URL the specified connection URL}
     * @return non-null
     * @since 2.7.8
     */
    protected static long getTimeout(URL url) {
        return getParameter(url, TIMEOUT_PARAM_NAME, -1L);
        // 缺省值为-1
    }
}

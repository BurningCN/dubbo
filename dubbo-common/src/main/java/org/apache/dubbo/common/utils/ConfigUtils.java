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
package org.apache.dubbo.common.utils;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOVE_VALUE_PREFIX;

// OK
public class ConfigUtils {

    private static final Logger logger = LoggerFactory.getLogger(ConfigUtils.class);
    private static Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\$\\s*\\{?\\s*([\\._0-9a-zA-Z]+)\\s*\\}?"); // ${}
    private static volatile Properties PROPERTIES;
    private static int PID = -1;

    // 私有，工具类不可实例化
    private ConfigUtils() {
    }

    public static boolean isNotEmpty(String value) {
        // 设计技巧，isEmpty和isNotEmpty实现一个即可，另一个直接取反
        return !isEmpty(value);
    }

    public static boolean isEmpty(String value) {
        // 下面的几种值都被当做empty，不区分大小写
        return StringUtils.isEmpty(value)
                || "false".equalsIgnoreCase(value)
                || "0".equalsIgnoreCase(value)
                || "null".equalsIgnoreCase(value)
                || "N/A".equalsIgnoreCase(value);
    }

    public static boolean isDefault(String value) {
        // 下面的几种值都被当做default，不区分大小写
        return "true".equalsIgnoreCase(value)
                || "default".equalsIgnoreCase(value);
    }

    /**
     * Insert default extension into extension list.
     * <p>
     * Extension list support<ul>
     * <li>Special value <code><strong>default</strong></code>, means the location for default extensions.
     * <li>Special symbol<code><strong>-</strong></code>, means remove. <code>-foo1</code> will remove default extension 'foo'; <code>-default</code> will remove all default extensions.
     * </ul>
     *
     * @param type Extension type  ---> eg:ThreadPool.class
     * @param cfg  Extension name list
     * @param def  Default extension list
     * @return result extension list
     */
    // 如果cfg里面含有default，会被过滤掉，def里面不是type的扩展类也会被过滤掉
    public static List<String> mergeValues(Class<?> type, String cfg, List<String> def) {

        // 过滤掉def容器中不属于type扩展类的
        List<String> defaults = new ArrayList<String>();
        if (def != null) {
            for (String name : def) {
                if (ExtensionLoader.getExtensionLoader(type).hasExtension(name)) {
                    defaults.add(name);
                }
            }
        }

        List<String> names = new ArrayList<String>();

        // 根据COMMA_SPLIT_PATTERN切割，逗号切割。常见的就是两种：直接str.split(",")或这Pattern.split(str)
        String[] configs = (cfg == null || cfg.trim().length() == 0) ? new String[0] : COMMA_SPLIT_PATTERN.split(cfg);
        for (String config : configs) {
            if (config != null && config.trim().length() > 0) {
                names.add(config);
            }
        }

        // 下面这个操作主要是看names里面是否含有"-default"有的话，表示外层方法第二个参数的全部不要，即defaults全部不要
        // 没有的话，查看names是否有"default"，有的话删除，并把defaults合并到names
        if (!names.contains(REMOVE_VALUE_PREFIX + DEFAULT_KEY)) {
            // 判断names是否含有"default"
            int i = names.indexOf(DEFAULT_KEY);
            if (i > 0) {
                // 把defaults添加到第i个位置，注意此时"default"还没有被移除
                names.addAll(i, defaults);
            } else {
                names.addAll(0, defaults);
            }
            // 移除"default"
            names.remove(DEFAULT_KEY);
        } else {
            // 如果names含有"-default"，那么defaults全部不要
            names.remove(DEFAULT_KEY);
        }

        // names是否含有-xxx，有的话把-xxx和xxx从names这个list移除
        for (String name : new ArrayList<String>(names)) {
            if (name.startsWith(REMOVE_VALUE_PREFIX)) {
                names.remove(name);
                names.remove(name.substring(1));
            }
        }
        return names;

        // 玛德，这个方法较为复杂，但是只被test调用了
    }

    // ${}占位符的解析和替换
    public static String replaceProperty(String expression, Map<String, String> params) {
        if (expression == null || expression.length() == 0 || expression.indexOf('$') < 0) {
            return expression;
        }
        // Matcher、Pattern、matcher、find、group、appendReplacement、appendTail语法等
        Matcher matcher = VARIABLE_PATTERN.matcher(expression);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            // 优先使用系统属性值，如果为null在使用map里的
            String value = System.getProperty(key);
            if (value == null && params != null) {
                value = params.get(key);
            }
            if (value == null) {
                value = "";
            }
            // 替换${a.b.c}的a.b.c为实际值
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static Properties getProperties() {
        // PROPERTIES是private static volatile
        // 使用volatile+syn确保PROPERTIES的单例 （ 实现了延迟加载的功能，因为加载文件内容涉及到io操作，相对耗时）
        if (PROPERTIES == null) {
            // 因为是静态属性，双重检查加锁必须要加类锁
            synchronized (ConfigUtils.class) {
                if (PROPERTIES == null) {
                    // 获取系统属性key为dubbo.properties.file的值
                    String path = System.getProperty(CommonConstants.DUBBO_PROPERTIES_KEY);
                    if (path == null || path.length() == 0) {
                        // 系统属性获取不到的话，从环境变量获取
                        path = System.getenv(CommonConstants.DUBBO_PROPERTIES_KEY);
                        if (path == null || path.length() == 0) {
                            // 都获取不到赋值默认的，即dubbo.properties
                            path = CommonConstants.DEFAULT_DUBBO_PROPERTIES;
                        }
                    }
                    // 进去
                    PROPERTIES = ConfigUtils.loadProperties(path, false, true);
                }
            }
        }
        return PROPERTIES;
    }

    public static void setProperties(Properties properties) {
        PROPERTIES = properties;
    }

    public static void addProperties(Properties properties) {
        if (properties != null) {
            // 在原有属性的基础上添加新的属性
            getProperties().putAll(properties);
        }
    }

    public static String getProperty(String key) {
        // 进去
        return getProperty(key, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String getProperty(String key, String defaultValue) {
        // 先从系统属性获取
        String value = System.getProperty(key);
        if (value != null && value.length() > 0) {
            return value;
        }
        // 然后加载属性文件到Properties后再看有没有
        Properties properties = getProperties();
        return replaceProperty(properties.getProperty(key, defaultValue), (Map) properties);
    }

    /**
     * System environment -> System properties
     *
     * @param key key
     * @return value
     */
    public static String getSystemProperty(String key) {
        String value = System.getenv(key);
        if (StringUtils.isEmpty(value)) {
            value = System.getProperty(key);
        }
        return value;
    }

    public static Properties loadProperties(String fileName) {
        return loadProperties(fileName, false, false);
    }

    public static Properties loadProperties(String fileName, boolean allowMultiFile) {
        return loadProperties(fileName, allowMultiFile, false);
    }

    /**
     * Load properties file to {@link Properties} from class path.
     *
     * @param fileName       properties file name. for example: <code>dubbo.properties</code>, <code>METE-INF/conf/foo.properties</code>
     * @param allowMultiFile if <code>false</code>, throw {@link IllegalStateException} when found multi file on the class path.
     * @param optional       is optional. if <code>false</code>, log warn when properties config file not found!s
     * @return loaded {@link Properties} content. <ul>
     * <li>return empty Properties if no file found.
     * <li>merge multi properties file if found multi file
     * </ul>
     * @throws IllegalStateException not allow multi-file, but multi-file exist on class path.
     */
    public static Properties loadProperties(String fileName, boolean allowMultiFile, boolean optional) {
        Properties properties = new Properties();
        // add scene judgement in windows environment Fix 2557
        // 检查文件是否存在，进去
        if (checkFileNameExist(fileName)) {
            try {
                FileInputStream input = new FileInputStream(fileName);
                try {
                    // 直接load
                    properties.load(input);
                } finally {
                    input.close();
                }
            } catch (Throwable e) {
                logger.warn("Failed to load " + fileName + " file from " + fileName + "(ignore this file): " + e.getMessage(), e);
            }
            return properties;
        }

        // 比如properties.load文件是在common模块的test/resources目录下，而当前类是main包下，上面肯定检查到文件不存在，就会返回false，
        // 但是使用线程上下文加载器是可以加载到的！！

        // 其实上面说的不对，并不只是不同包，因为同main/resources目录下文件也是会检查到不存在，比如META-INF/dubbo/internal/org.apache.dubbo.common.threadpool.ThreadPool
        // 所以说只要是resources的都会检查到不存在！都需要利用线程上下文加载器加载
        List<java.net.URL> list = new ArrayList<java.net.URL>();
        try {
            // 这里是getResources，返回 URL集合，并转化为List
            // 比如properties.load文件就会返回 file:/Users/gy821075/IdeaProjects/dubbo/dubbo-common/target/test-classes/properties.load
            Enumeration<java.net.URL> urls = ClassUtils.getClassLoader().getResources(fileName);
            list = new ArrayList<java.net.URL>();
            while (urls.hasMoreElements()) {
                list.add(urls.nextElement());
            }
        } catch (Throwable t) {
            logger.warn("Fail to load " + fileName + " file: " + t.getMessage(), t);
        }

        if (list.isEmpty()) {
            if (!optional) {
                // 如果URL list为空，传进来 false 打日志
                logger.warn("No " + fileName + " found on the class path.");
            }
            return properties;
        }

        if (!allowMultiFile) {
            if (list.size() > 1) {
                // 不允许多个文件，但是加载出了多个，可能参数填入的是文件夹，所以打日志
                String errMsg = String.format("only 1 %s file is expected, but %d dubbo.properties files found on class path: %s",
                        fileName, list.size(), list.toString());
                logger.warn(errMsg);
            }

            // fall back to use method getResourceAsStream
            try {
                // 前面就是打了一堆日志，这里才是真正加载 getResourceAsStream
                properties.load(ClassUtils.getClassLoader().getResourceAsStream(fileName));
            } catch (Throwable e) {
                logger.warn("Failed to load " + fileName + " file from " + fileName + "(ignore this file): " + e.getMessage(), e);
            }
            return properties;
        }

        logger.info("load " + fileName + " properties file from " + list);
        // 到这里说明允许多个文件，比如META-INF/dubbo/internal/org.apache.dubbo.common.status.StatusChecker，
        // 在common模块就有两个同名文件一个在main/resources下，一个在test/resources下。
        for (java.net.URL url : list) {
            try {
                Properties p = new Properties();
                // api
                InputStream input = url.openStream();
                if (input != null) {
                    try {
                        // 读到临时的p
                        p.load(input);
                        // 填充到结果里
                        properties.putAll(p);
                    } finally {
                        try {
                            input.close();
                        } catch (Throwable t) {
                        }
                    }
                }
            } catch (Throwable e) {
                logger.warn("Fail to load " + fileName + " file from " + url + "(ignore this file): " + e.getMessage(), e);
            }
        }

        return properties;
    }

    /**
     * check if the fileName can be found in filesystem
     *
     * @param fileName
     * @return
     */
    private static boolean checkFileNameExist(String fileName) {
        File file = new File(fileName);
        return file.exists();
    }


    public static int getPid() {
        if (PID < 0) {
            try {
                RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
                // format: "pid@hostname"
                // mac电脑为例：16525@B-RHDTJG5H-2145.local
                String name = runtime.getName();
                PID = Integer.parseInt(name.substring(0, name.indexOf('@')));
            } catch (Throwable e) {
                PID = 0;
            }
        }
        return PID;
    }
}

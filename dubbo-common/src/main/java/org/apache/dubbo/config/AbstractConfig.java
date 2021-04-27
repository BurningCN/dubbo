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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.CompositeConfiguration;
import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.MethodUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.AsyncMethodInfo;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.utils.ReflectUtils.findMethodByMethodSignature;

/**
 * Utility methods and public methods for parsing configuration
 * 用于解析配置的实用方法和公共方法
 * @export
 */
// OK
public abstract class AbstractConfig implements Serializable {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractConfig.class);
    private static final long serialVersionUID = 4267533505537413570L;

    /**
     * The legacy properties container
     */
    // 遗留属性容器,发现除了下面的put没有其他地方使用到
    private static final Map<String, String> LEGACY_PROPERTIES = new HashMap<String, String>();

    /**
     * The suffix container
     */
    //  getTagName用到，去尾巴
    private static final String[] SUFFIXES = new String[]{"Config", "Bean", "ConfigBase"};

    static {
        LEGACY_PROPERTIES.put("dubbo.protocol.name", "dubbo.service.protocol");
        LEGACY_PROPERTIES.put("dubbo.protocol.host", "dubbo.service.server.host");
        LEGACY_PROPERTIES.put("dubbo.protocol.port", "dubbo.service.server.port");
        LEGACY_PROPERTIES.put("dubbo.protocol.threads", "dubbo.service.max.thread.pool.size");
        LEGACY_PROPERTIES.put("dubbo.consumer.timeout", "dubbo.service.invoke.timeout");
        LEGACY_PROPERTIES.put("dubbo.consumer.retries", "dubbo.service.max.retry.providers");
        LEGACY_PROPERTIES.put("dubbo.consumer.check", "dubbo.service.allow.no.provider");
        LEGACY_PROPERTIES.put("dubbo.service.url", "dubbo.service.address");
    }

    /**
     * The config id
     */
    // AbstractConfig任何子类都具备的两个属性（当然不是必须提供的，prefix有默认值，通过getPrefix生成默认值）
    protected String id;
    protected String prefix;

    // 没调用点
    protected final AtomicBoolean refreshed = new AtomicBoolean(false);

    // 没调用点
    private static String convertLegacyValue(String key, String value) {
        if (value != null && value.length() > 0) {
            if ("dubbo.service.max.retry.providers".equals(key)) {
                return String.valueOf(Integer.parseInt(value) - 1);
            } else if ("dubbo.service.allow.no.provider".equals(key)) {
                return String.valueOf(!Boolean.parseBoolean(value));
            }
        }
        return value;
    }

    // gx
    public static String getTagName(Class<?> cls) {
        String tag = cls.getSimpleName();
        //SUFFIXES = {String[3]@5914}
        // 0 = "Config"
        // 1 = "Bean"
        // 2 = "ConfigBase"
        for (String suffix : SUFFIXES) {
            // ConfigCenterConfig以Config结尾，tag = ConfigCenter（还有其他的比如ReferenceBean、ReferenceConfigBase）
            if (tag.endsWith(suffix)) {
                tag = tag.substring(0, tag.length() - suffix.length());
                break;
            }
        }
        // 按照驼峰解析且如果有多个词的话用-连接，ConfigCenter->config-center
        return StringUtils.camelToSplitName(tag, "-");
    }

    public static void appendParameters(Map<String, String> parameters, Object config) {
        appendParameters(parameters, config, null);
    }

    // parameters参数一般是值-结果参数，方法的主要作用就是调用config的getXx和getParameters方法，
    // 把xx或者@Parameter注解的key值作为parameters map的key（prefix参数做前缀，如果有的话），get的返回值作为parameters的val
    @SuppressWarnings("unchecked")
    public static void appendParameters(Map<String, String> parameters, Object config, String prefix) {
        if (config == null) {
            return;
        }
        // Config一般是其子类，比如ApplicationConfig，获取所有方法
        Method[] methods = config.getClass().getMethods();
        for (Method method : methods) {
            try {
                String name = method.getName();
                // 处理get方法或者getParameters方法
                // get方法,进去
                if (MethodUtils.isGetter(method)) {
                    // 获取@Parameter注解
                    Parameter parameter = method.getAnnotation(Parameter.class);

                    // 方法返回类型为Object 或者 parameter注解里面的excluded值为true，那么不处理这个方法
                    if (method.getReturnType() == Object.class || parameter != null && parameter.excluded()) {
                        continue;
                    }
                    // 以ApplicationConfig的getVersion为例
                    //    @Parameter(key = "application.version")
                    //    public String getVersion() {
                    //        return version;
                    //    }
                    String key;
                    if (parameter != null && parameter.key().length() > 0) {
                        // 获取key值 application.version
                        key = parameter.key();
                    } else {
                        // 获取getXXX的XXX属性名称并根绝驼峰转化按照split分割返回
                        key = calculatePropertyFromGetter(name);
                    }
                    // get方法没有参数，所以invoke传对象即可
                    Object value = method.invoke(config);
                    // 转化为字符串，因为前面isGetter内部限定get方法返回primitive类型的，所以这里放心转
                    String str = String.valueOf(value).trim();
                    if (value != null && str.length() > 0) {
                        // 假设前面getVersion返回1.0

                        if (parameter != null && parameter.escaped()) {
                            // utf8编码下，进去（比如/被编码为%2F）
                            str = URL.encode(str);
                        }
                        // 是否含有append=true（默认为false）
                        if (parameter != null && parameter.append()) {
                            // 从map中取出来，比如用于一开始就put了一个entry比如num:one，而比如getNumber方法前面计算出来的key是num（从parameter注解获得的或者属性本身）
                            // getNumber的返回值为1,那么还需在前面拼上"one," 即为"one,1" --- > 详见testAppendParameters1
                            String pre = parameters.get(key);
                            if (pre != null && pre.length() > 0) {
                                // 拼接
                                str = pre + "," + str;
                            }
                        }
                        // 有前缀的话拼一下
                        if (prefix != null && prefix.length() > 0) {
                            key = prefix + "." + key;
                        }
                        // 存到map {application.version:1.0}
                        parameters.put(key, str);

                        // 如果前面getXX调用返回null，但是@Parameter(...required=true)，那么抛异常
                    } else if (parameter != null && parameter.required()) {
                        throw new IllegalStateException(config.getClass().getSimpleName() + "." + key + " == null");
                    }
                    // 如果前面不是getXX方法，判断是不是getParameters方法，进去看下怎么判断的,没必要new Object[0]
                } else if (isParametersGetter(method)) {
                    Map<String, String> map = (Map<String, String>) method.invoke(config, new Object[0]);
                    // 做一下转化（key加prefix前缀、-变成.），进去
                    parameters.putAll(convert(map, prefix));
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    @Deprecated
    protected static void appendAttributes(Map<String, Object> parameters, Object config) {
        appendAttributes(parameters, config, null);
    }

    // 已经废弃了，和appendParameters的区别在于：
    // 这个仅处理带有@Parameter注解的、map的value不限于String类型
    @Deprecated
    protected static void appendAttributes(Map<String, Object> parameters, Object config, String prefix) {
        if (config == null) {
            return;
        }
        Method[] methods = config.getClass().getMethods();
        for (Method method : methods) {
            try {
                Parameter parameter = method.getAnnotation(Parameter.class);
                if (parameter == null || !parameter.attribute()) {
                    continue;
                }
                String name = method.getName();
                if (MethodUtils.isGetter(method)) {
                    String key;
                    if (parameter.key().length() > 0) {
                        key = parameter.key();
                    } else {
                        key = calculateAttributeFromGetter(name);
                    }
                    Object value = method.invoke(config);
                    if (value != null) {
                        if (prefix != null && prefix.length() > 0) {
                            key = prefix + "." + key;
                        }
                        parameters.put(key, value);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    // todo 待分析
    protected static AsyncMethodInfo convertMethodConfig2AsyncInfo(MethodConfig methodConfig) {
        if (methodConfig == null || (methodConfig.getOninvoke() == null && methodConfig.getOnreturn() == null && methodConfig.getOnthrow() == null)) {
            return null;
        }

        //check config conflict
        if (Boolean.FALSE.equals(methodConfig.isReturn()) && (methodConfig.getOnreturn() != null || methodConfig.getOnthrow() != null)) {
            throw new IllegalStateException("method config error : return attribute must be set true when onreturn or onthrow has been set.");
        }

        AsyncMethodInfo asyncMethodInfo = new AsyncMethodInfo();

        asyncMethodInfo.setOninvokeInstance(methodConfig.getOninvoke());
        asyncMethodInfo.setOnreturnInstance(methodConfig.getOnreturn());
        asyncMethodInfo.setOnthrowInstance(methodConfig.getOnthrow());

        try {
            String oninvokeMethod = methodConfig.getOninvokeMethod();
            if (StringUtils.isNotEmpty(oninvokeMethod)) {
                asyncMethodInfo.setOninvokeMethod(getMethodByName(methodConfig.getOninvoke().getClass(), oninvokeMethod));
            }

            String onreturnMethod = methodConfig.getOnreturnMethod();
            if (StringUtils.isNotEmpty(onreturnMethod)) {
                asyncMethodInfo.setOnreturnMethod(getMethodByName(methodConfig.getOnreturn().getClass(), onreturnMethod));
            }

            String onthrowMethod = methodConfig.getOnthrowMethod();
            if (StringUtils.isNotEmpty(onthrowMethod)) {
                asyncMethodInfo.setOnthrowMethod(getMethodByName(methodConfig.getOnthrow().getClass(), onthrowMethod));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

        return asyncMethodInfo;
    }

    private static Method getMethodByName(Class<?> clazz, String methodName) {
        try {
            return ReflectUtils.findMethodByMethodName(clazz, methodName);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static Set<String> getSubProperties(Map<String, String> properties, String prefix) {
        return properties.keySet().stream().filter(k -> k.contains(prefix)).map(k -> {
            k = k.substring(prefix.length());
            return k.substring(0, k.indexOf("."));
        }).collect(Collectors.toSet());
    }

    // gx 和refresh有关，方法主要是从clazz类setter方法名称中提取属性名称，并做一些处理用以生成最后的key（Configuration的key）
    // 方法整体还是很easy的 只需要要找到getter，是因为上面含有@Parameter注解，注解里面有key
    private static String extractPropertyName(Class<?> clazz, Method setter) throws Exception {
        String propertyName = setter.getName().substring("set".length());
        Method getter = null;
        try {
            getter = clazz.getMethod("get" + propertyName);
        } catch (NoSuchMethodException e) {
            getter = clazz.getMethod("is" + propertyName);
        }
        Parameter parameter = getter.getAnnotation(Parameter.class);
        if (parameter != null && StringUtils.isNotEmpty(parameter.key()) && parameter.useKeyAsProperty()) {
            propertyName = parameter.key();
        } else {
            propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
        }
        return propertyName;
    }

    // gx
    private static String calculatePropertyFromGetter(String name) {
        int i = name.startsWith("get") ? 3 : 2;// 2的意思就是isXX方法
        // 获取属性名称
        String property = name.substring(i, i + 1).toLowerCase() + name.substring(i + 1);
        // 驼峰转为.分割的格式，比如ServiceKey = service.key
        return StringUtils.camelToSplitName(property, ".");
    }

    // gx
    private static String calculateAttributeFromGetter(String getter) {
        int i = getter.startsWith("get") ? 3 : 2;
        return getter.substring(i, i + 1).toLowerCase() + getter.substring(i + 1);
    }

    // gx
    private static void invokeSetParameters(Class c, Object o, Map map) {
        try {
            // 进去
            Method method = findMethodByMethodSignature(c, "setParameters", new String[]{Map.class.getName()});
            if (method != null && isParametersSetter(method)) {
                method.invoke(o, map);
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    // gx
    private static Map<String, String> invokeGetParameters(Class c, Object o) {
        try {
            Method method = findMethodByMethodSignature(c, "getParameters", null);
            if (method != null && isParametersGetter(method)) {
                return (Map<String, String>) method.invoke(o);
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    private static boolean isParametersGetter(Method method) {
        String name = method.getName();
        return ("getParameters".equals(name)
                && Modifier.isPublic(method.getModifiers())
                && method.getParameterTypes().length == 0
                && method.getReturnType() == Map.class);
    }

    private static boolean isParametersSetter(Method method) {
        return ("setParameters".equals(method.getName())
                && Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 1
                && Map.class == method.getParameterTypes()[0]
                && method.getReturnType() == void.class);
    }

    /**
     * @param parameters the raw parameters
     * @param prefix     the prefix
     * @return the parameters whose raw key will replace "-" to "." <---注意这个
     * @revised 2.7.8 "private" to be "protected"
     */
    // gx 方法主要作用就是给map的key加prefix，且如果key值有-连接，那么新构建一个entry，key用.连接
    protected static Map<String, String> convert(Map<String, String> parameters, String prefix) {
        if (parameters == null || parameters.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        String pre = (prefix != null && prefix.length() > 0 ? prefix + "." : "");
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            result.put(pre + key, value);
            // For compatibility, key like "registry-type" will has a duplicate key "registry.type"  <- 注意这句话
            if (key.contains("-")) {
                result.put(pre + key.replace('-', '.'), value);
            }
        }
        return result;
    }

    @Parameter(excluded = true)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void updateIdIfAbsent(String value) {
        if (StringUtils.isNotEmpty(value) && StringUtils.isEmpty(id)) {
            this.id = value;
        }
    }

    // 结合AbstractConfigTest.appendAnnotation测试程序
    // 第一个参数一般是注解.class，第二个一般是（获取到的）注解对象，方法的主要作用就是把annotation注解对象的一些值填充到this对象
    protected void appendAnnotation(Class<?> annotationClass, Object annotation) {
        Method[] methods = annotationClass.getMethods();
        // 遍历注解的所有方法
        for (Method method : methods) {
            // 满足不是Object类的方法、返回值不是void、没有参数、公有的不是静态的
            if (method.getDeclaringClass() != Object.class
                    && method.getReturnType() != void.class
                    && method.getParameterTypes().length == 0
                    && Modifier.isPublic(method.getModifiers())
                    && !Modifier.isStatic(method.getModifiers())) {
                try {
                    String property = method.getName();
                    if ("interfaceClass".equals(property) || "interfaceName".equals(property)) {
                        property = "interface";
                    }
                    // 构建set方法 str
                    String setter = "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
                    // 调用方法获取返回值
                    Object value = method.invoke(annotation);
                    // 返回值不null且不是默认值
                    if (value != null && !value.equals(method.getDefaultValue())) {
                        // 如果是基本数据类型，获取其包装类型，否则返回类型自己，目的是为了后面获取this的set方法（this一般是AbstractConfig的子类对象）
                        Class<?> parameterType = ReflectUtils.getBoxedClass(method.getReturnType());
                        if ("filter".equals(property) || "listener".equals(property)) {
                            parameterType = String.class;
                            // 比如 @Config(listener = {"l1, l2"}) ，value = new String[]{"l1,l2"}，经过下面的操作value = "l1,l2"
                            value = StringUtils.join((String[]) value, ",");
                        } else if ("parameters".equals(property)) {
                            parameterType = Map.class;
                            // 比如 @Config(parameters = {"k1", "v1", "k2", "v2"})，value=new String["k1","v1","k2","v2"]，经过下面的操作value为map，两个entry{k1:v1,k2:v2}
                            value = CollectionUtils.toStringMap((String[]) value);
                        }
                        try {
                            // this.getClass，这个this一般是AbstractConfig的子类对象，因为整个方法最终目的就是把传进来的注解的相关属性值通过
                            // 调用xxConfig的对应setXX赋值进去
                            Method setterMethod = getClass().getMethod(setter, parameterType);
                            // 调用set方法赋值
                            setterMethod.invoke(this, value);
                        } catch (NoSuchMethodException e) {
                            // ignore
                        }
                    }
                } catch (Throwable e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Should be called after Config was fully initialized.
     * // FIXME: this method should be completely replaced by appendParameters
     *
     * @return
     * @see AbstractConfig#appendParameters(Map, Object, String)
     * <p>
     * Notice! This method should include all properties in the returning map, treat @Parameter differently compared to appendParameters.
     */
    // 看上面最后一行注释前半句就是该方法的作用，然后还有一些意思就是说这个方法和appendParameters有不少重复的地方，耦合了，希望重构下，而且和appendParameters对待@Parameter是不一样的
    public Map<String, String> getMetaData() {
        Map<String, String> metaData = new HashMap<>();
        Method[] methods = this.getClass().getMethods();
        for (Method method : methods) {
            try {
                String name = method.getName();
                // 是否是元方法， 其实就是判断是不是getXX(或者isXX)方法 进去
                if (MethodUtils.isMetaMethod(method)) {
                    String key;
                    Parameter parameter = method.getAnnotation(Parameter.class);
                    // parameter.useKeyAsProperty()是否允许使用parameter.key()作为代表属性的key
                    if (parameter != null && parameter.key().length() > 0 && parameter.useKeyAsProperty()) {
                        key = parameter.key();
                    } else {
                        key = calculateAttributeFromGetter(name);
                    }
                    // treat url and configuration differently, the value should always present in configuration though it may not need to present in url.
                    // if (method.getReturnType() == Object.class || parameter != null && parameter.excluded()) {
                    if (method.getReturnType() == Object.class) {
                        metaData.put(key, null);
                        continue;
                    }

                    /**
                     * Attributes annotated as deprecated should not override newly added replacement.
                     * 注释为deprecated的属性不应该覆盖新添加的替换。
                     */
                    if (MethodUtils.isDeprecated(method) && metaData.get(key) != null) {
                        continue;
                    }

                    Object value = method.invoke(this);
                    String str = String.valueOf(value).trim();
                    if (value != null && str.length() > 0) {
                        metaData.put(key, str);
                    } else {
                        // 方法上面注释说了，获取所有的属性，即使属性的值为null
                        metaData.put(key, null);
                    }

                    // getParameters 方法处理
                } else if (isParametersGetter(method)) {
                    Map<String, String> map = (Map<String, String>) method.invoke(this, new Object[0]);
                    metaData.putAll(convert(map, ""));
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        return metaData;
    }

    // gx
    @Parameter(excluded = true)
    public String getPrefix() {
        // 默认值dubbo.XX（XX比如说Application）
        return StringUtils.isNotEmpty(prefix) ? prefix : (CommonConstants.DUBBO + "." + getTagName(this.getClass()));
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    // 这个方法的作用就是把CompositeConfiguration的一些参数赋值给AbstractConfig的一些属性赋值
    public void refresh() {
        // Configuration是依赖于Environment
        Environment env = ApplicationModel.getEnvironment();
        try {
            // 获取一个带前缀的CompositeConfiguration，进去
            CompositeConfiguration compositeConfiguration = env.getPrefixedConfiguration(this);
            // loop methods, get override value and set the new value back to method
            Method[] methods = getClass().getMethods();
            // 遍历所有的SetXx方法和setParameters方法
            for (Method method : methods) {
                if(method.getName().equals("setEscape")){
                    int i = 0;
                }
                // SetXx方法
                if (MethodUtils.isSetter(method)) {
                    try {
                        // extractPropertyName(getClass(), method))从setXx方法获取属性名xx，然后到compositeConfiguration查找值
                        // 注意一点，compositeConfiguration内部匹配到PropertiesConfiguration有属性xx参数的话，在
                        // PropertiesConfiguration.getInternalProperty内部有延迟加载模式，详见ConfigUtils.getProperties()方法
                        String value = StringUtils.trim(compositeConfiguration.getString(extractPropertyName(getClass(), method)));
                        // isTypeMatch() is called to avoid duplicate and incorrect update, for example, we have two 'setGeneric' methods in ReferenceConfig.
                        // isTypeMatch进去
                        if (StringUtils.isNotEmpty(value) && ClassUtils.isTypeMatch(method.getParameterTypes()[0], value)) {
                            method.invoke(this, ClassUtils.convertPrimitive(method.getParameterTypes()[0], value));
                        }
                    } catch (NoSuchMethodException e) {
                        logger.info("Failed to override the property " + method.getName() + " in " +
                                this.getClass().getSimpleName() +
                                ", please make sure every property has getter/setter method provided.");
                    }
                } else if (isParametersSetter(method)) {
                    // extractPropertyName(getClass(), method)的值为parameters，先从compositeConfiguration获取k为parameters的值
                    String value = StringUtils.trim(compositeConfiguration.getString(extractPropertyName(getClass(), method)));
                    if (StringUtils.isNotEmpty(value)) {
                        // 调用this.getParameters()方法
                        Map<String, String> map = invokeGetParameters(getClass(), this);
                        map = map == null ? new HashMap<>() : map;
                        // 把从Configuration获取的值经过parseParameters+convert之后也put到map
                        map.putAll(convert(StringUtils.parseParameters(value), ""));
                        // 调用setParameters方法
                        invokeSetParameters(getClass(), this, map);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to override ", e);
        }
    }

    // 这个toString是一个关键，这就是为啥每次断点变量窗口XXXConfig后面都会带什么<dubbo:xx xx=xx xx=xx/>
    @Override
    public String toString() {
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("<dubbo:");
            // 获取tagName，this是AbstractConfig的子类对象，比如ApplicationConfig进行getTagName就是application、ReferenceConfig就是reference，进去
            buf.append(getTagName(getClass()));
            Method[] methods = getClass().getMethods();
            for (Method method : methods) {
                try {
                    // 遍历所有的get方法
                    if (MethodUtils.isGetter(method)) {
                        // 根据getXx方法名取出xx属性名称
                        String key = calculateAttributeFromGetter(method.getName());

                        try {
                            // 触发这个api，看看this对应的类里面有没有xx字段
                            getClass().getDeclaredField(key);
                        } catch (NoSuchFieldException e) {
                            // ignore
                            continue;
                        }

                        // 调用getXx方法获取返回值
                        Object value = method.invoke(this);
                        if (value != null) {
                            // 如果属性有值的话，k=v 拼接到buf
                            buf.append(" ");
                            buf.append(key);
                            buf.append("=\"");
                            buf.append(value);
                            buf.append("\"");
                        }
                    }
                } catch (Exception e) {
                    logger.warn(e.getMessage(), e);
                }
            }
            buf.append(" />");
            // 比如AbstractConfigTest.appendAnnotation测试方法，下面结果就是 <dubbo:annotation listener="l1, l2" filter="f1, f2" />
            // 比如ApplicationConfigTest.testVersion测试方法，下面结果就是 <dubbo:application version="1.0.0" name="app" hostname="bogon" />
            return buf.toString();
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
            return super.toString();
        }
    }

    /**
     * FIXME check @Parameter(required=true) and any conditions that need to match.
     */
    @Parameter(excluded = true)
    public boolean isValid() {
        return true;
    }


    @Override
    public boolean equals(Object obj) {
        // 首先两个子类对象的name要相等
        if (obj == null || !(obj.getClass().getName().equals(this.getClass().getName()))) {
            return false;
        }

        Method[] methods = this.getClass().getMethods();
        for (Method method1 : methods) {
            // 获取this的所有get方法
            if (MethodUtils.isGetter(method1)) {
                Parameter parameter = method1.getAnnotation(Parameter.class);
                // 如果有Parameter注解，但是excluded为true表示不参与一些属性相关的东西，跳过该属性即可
                if (parameter != null && parameter.excluded()) {
                    continue;
                }
                try {
                    // 获取obj对应的同名同参方法
                    Method method2 = obj.getClass().getMethod(method1.getName(), method1.getParameterTypes());
                    // 调用this和obj的相同method方法，看返回值是否相同
                    Object value1 = method1.invoke(this, new Object[]{});
                    Object value2 = method2.invoke(obj, new Object[]{});
                    if (!Objects.equals(value1, value2)) {
                        return false;
                    }
                } catch (Exception e) {
                    return true;
                }
            }
        }
        return true;
    }

    /**
     * Add {@link AbstractConfig instance} into {@link ConfigManager}
     * <p>
     * Current method will invoked by Spring or Java EE container automatically, or should be triggered manually.
     *
     * @see ConfigManager#addConfig(AbstractConfig)
     * @since 2.7.5
     */
    // 作用看上面 这个方法非常重要！！！主要是用于spring的场景，我们通过xml或者注解的方式，比如xml，利用自定义的parser创建bean实例的时候，
    // 比如ServiceBean的构造方法得到触发，就会调用这里，这样DubboBootStrap在启动的就能从ConfigManager拿到要暴露/引用的服务
    @PostConstruct
    public void addIntoConfigManager() {
        ApplicationModel.getConfigManager().addConfig(this);
    }

    // nb 正常每个hashcode在子类中，但是这个父类很强，能适应所有子类，因为其hashcode的字段值是通过反射动态获取的
    @Override
    public int hashCode() {
        int hashCode = 1;

        Method[] methods = this.getClass().getMethods();
        for (Method method : methods) {
            if (MethodUtils.isGetter(method)) {
                Parameter parameter = method.getAnnotation(Parameter.class);
                if (parameter != null && parameter.excluded()) {
                    continue;
                }
                try {
                    Object value = method.invoke(this, new Object[]{});
                    hashCode = 31 * hashCode + value.hashCode();
                } catch (Exception ignored) {
                    //ignored
                }
            }
        }

        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }
}

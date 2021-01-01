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
package org.apache.dubbo.common.config;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This is an abstraction specially customized for the sequence Dubbo retrieves properties.
 *
 * 这是一个为Dubbo检索属性的序列特别定制的抽象
 */
// OK
public class CompositeConfiguration implements Configuration {
    private Logger logger = LoggerFactory.getLogger(CompositeConfiguration.class);

    private String id;
    private String prefix;

    /**
     * List holding all the configuration
     */
    // 类名本身就是Composite组合，所以是汇聚了多个不同种类的Configuration实现
    private List<Configuration> configList = new LinkedList<Configuration>();

    // 这两个构造方法都是在Environment方法调用的
    public CompositeConfiguration() {
        this(null, null);
    }

    // 带有prefix和id的，这两个值一般是AbstractConfig子类传来的
    public CompositeConfiguration(String prefix, String id) {
        if (StringUtils.isNotEmpty(prefix) && !prefix.endsWith(".")) {
            this.prefix = prefix + ".";
        } else {
            this.prefix = prefix;
        }
        this.id = id;
    }

    public CompositeConfiguration(Configuration... configurations) {
        this();
        if (configurations != null && configurations.length > 0) {
            Arrays.stream(configurations).filter(config -> !configList.contains(config)).forEach(configList::add);
        }
    }

    public void addConfiguration(Configuration configuration) {
        if (configList.contains(configuration)) {
            return;
        }
        this.configList.add(configuration);
    }

    public void addConfigurationFirst(Configuration configuration) {
        this.addConfiguration(0, configuration);
    }

    public void addConfiguration(int pos, Configuration configuration) {
        this.configList.add(pos, configuration);
    }

    @Override
    public Object getInternalProperty(String key) {
        Configuration firstMatchingConfiguration = null;
        for (Configuration config : configList) {
            try {
                // 看哪个配置类包含key
                if (config.containsKey(key)) {
                    firstMatchingConfiguration = config;
                    break;
                }
            } catch (Exception e) {
                logger.error("Error when trying to get value for key " + key + " from " + config + ", will continue to try the next one.");
            }
        }
        if (firstMatchingConfiguration != null) {
            // 取值
            return firstMatchingConfiguration.getProperty(key);
        } else {
            return null;
        }
    }

    // 重写了接口的default修饰的方法，但是跟了下其实没有调用点（出现的调用点列表其实是父接口的containsKey）
    @Override
    public boolean containsKey(String key) {
        // anyMatch有一个满足即可
        return configList.stream().anyMatch(c -> c.containsKey(key));
    }

    // 重写了接口的default修饰的方法，调用链路一般是这样的：
    // compositeConfiguration.getString(extractPropertyName(getClass(), method)) ，getString是Configuration的方法，
    // 最后流转到Configuration的default Object getProperty(String key)方法，内部触发 return this.getProperty(key, null);
    // 此时的this就是compositeConfiguration对象，也就是会调用下面的方法，主要做了一个prefix+id的拼接（如果有的话）
    @Override
    public Object getProperty(String key, Object defaultValue) {
        Object value = null;
        if (StringUtils.isNotEmpty(prefix)) {
            if (StringUtils.isNotEmpty(id)) {
                // prefix+id+key
                value = getInternalProperty(prefix + id + "." + key);
            }
            if (value == null) {
                // prefix+key
                value = getInternalProperty(prefix + key);
            }
        } else {
            // key 进去
            value = getInternalProperty(key);
        }
        return value != null ? value : defaultValue;
    }
}

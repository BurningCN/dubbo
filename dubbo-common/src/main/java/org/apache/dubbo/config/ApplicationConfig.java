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

import org.apache.dubbo.common.compiler.support.AdaptiveCompiler;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.infra.InfraAdapter;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.support.Parameter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.DUMP_DIRECTORY;
import static org.apache.dubbo.common.constants.CommonConstants.HOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METADATA_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SHUTDOWN_WAIT_KEY;
import static org.apache.dubbo.common.constants.QosConstants.ACCEPT_FOREIGN_IP;
import static org.apache.dubbo.common.constants.QosConstants.QOS_ENABLE;
import static org.apache.dubbo.common.constants.QosConstants.QOS_HOST;
import static org.apache.dubbo.common.constants.QosConstants.QOS_PORT;
import static org.apache.dubbo.config.Constants.DEVELOPMENT_ENVIRONMENT;
import static org.apache.dubbo.config.Constants.PRODUCTION_ENVIRONMENT;
import static org.apache.dubbo.config.Constants.TEST_ENVIRONMENT;


/**
 * The application info
 *
 * @export
 */
// OK
public class ApplicationConfig extends AbstractConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationConfig.class);

    private static final long serialVersionUID = 5508512956753757169L;

    /**
     * Application name
     */
    private String name;

    /**
     * The application version
     */
    private String version;

    /**
     * Application owner
     */
    private String owner;

    /**
     * Application's organization (BU) Business Unit,业务单元
     */
    private String organization;

    /**
     * Architecture layer
     */
    private String architecture;

    /**
     * Environment, e.g. dev, test or production
     */
    private String environment;

    /**
     * Java compiler
     */
    private String compiler;

    /**
     * The type of the log access
     */
    private String logger;

    /**
     * Registry centers
     */
    private List<RegistryConfig> registries;
    private String registryIds;

    /**
     * Monitor center
     */
    private MonitorConfig monitor;

    /**
     * Is default or not
     */
    private Boolean isDefault;

    /**
     * Directory for saving thread dump
     */
    private String dumpDirectory;

    /**
     * Whether to enable qos or not  QoS（Quality of Service，服务质量）
     */
    private Boolean qosEnable;

    /**
     * The qos host to listen
     */
    private String qosHost;

    /**
     * The qos port to listen
     */
    private Integer qosPort;

    /**
     * Should we accept foreign ip or not?
     */
    private Boolean qosAcceptForeignIp;

    /**
     * Customized parameters
     */
    private Map<String, String> parameters;

    /**
     * Config the shutdown.wait
     */
    private String shutwait;

    private String hostname;

    /**
     * Metadata type, local or remote, if choose remote, you need to further specify metadata center.
     */
    private String metadataType;

    private Boolean registerConsumer;

    private String repository;

    /**
     * Metadata Service, used in Service Discovery
     */
    private Integer metadataServicePort;

    public ApplicationConfig() {
    }

    public ApplicationConfig(String name) {
        setName(name);
    }

    // 一些getXX方法上面就有这个注解，在父类AbstractConfig会用到进行处理，一般至少有个key，然后以kv的方式存到map（k就是key=后面的值，v就是getXX返回的值）
    @Parameter(key = APPLICATION_KEY, required = true, useKeyAsProperty = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if (StringUtils.isEmpty(id)) {
            // 如果没有setId过，那么id = name
            id = name;
        }
    }

    @Parameter(key = "application.version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        if (environment != null) {
            if (!(DEVELOPMENT_ENVIRONMENT.equals(environment)
                    || TEST_ENVIRONMENT.equals(environment)
                    || PRODUCTION_ENVIRONMENT.equals(environment))) {

                // 仅支持3种环境变量：test、product、develop
                throw new IllegalStateException(String.format("Unsupported environment: %s, only support %s/%s/%s, default is %s.",
                        environment,
                        DEVELOPMENT_ENVIRONMENT,
                        TEST_ENVIRONMENT,
                        PRODUCTION_ENVIRONMENT,
                        PRODUCTION_ENVIRONMENT));
            }
        }
        this.environment = environment;
    }

    public RegistryConfig getRegistry() {
        // 一个ApplicationConfig仅有一个RegistryConfig
        return CollectionUtils.isEmpty(registries) ? null : registries.get(0);
    }

    public void setRegistry(RegistryConfig registry) {
        List<RegistryConfig> registries = new ArrayList<RegistryConfig>(1);
        registries.add(registry);
        // 一个ApplicationConfig仅有一个RegistryConfig
        this.registries = registries;
    }

    public List<RegistryConfig> getRegistries() {
        return registries;
    }

    @SuppressWarnings({"unchecked"})
    public void setRegistries(List<? extends RegistryConfig> registries) {
        // 和前面的一样，直接赋值操作
        this.registries = (List<RegistryConfig>) registries;
    }

    @Parameter(excluded = true)
    public String getRegistryIds() {
        return registryIds;
    }

    public void setRegistryIds(String registryIds) {
        this.registryIds = registryIds;
    }

    public MonitorConfig getMonitor() {
        return monitor;
    }

    public void setMonitor(String monitor) {
        this.monitor = new MonitorConfig(monitor);
    }

    public void setMonitor(MonitorConfig monitor) {
        this.monitor = monitor;
    }

    public String getCompiler() {
        return compiler;
    }

    public void setCompiler(String compiler) {
        this.compiler = compiler;
        AdaptiveCompiler.setDefaultCompiler(compiler);
    }

    public String getLogger() {
        return logger;
    }

    public void setLogger(String logger) {
        this.logger = logger;
        LoggerFactory.setLoggerAdapter(logger);
    }

    public Boolean isDefault() {
        return isDefault;
    }

    public void setDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    // 跟下  DUMP_DIRECTORY 参数的引用处，给 AbortPolicyWithReport 使用的
    @Parameter(key = DUMP_DIRECTORY)
    public String getDumpDirectory() {
        return dumpDirectory;
    }

    public void setDumpDirectory(String dumpDirectory) {
        this.dumpDirectory = dumpDirectory;
    }

    @Parameter(key = QOS_ENABLE)
    public Boolean getQosEnable() {
        return qosEnable;
    }

    public void setQosEnable(Boolean qosEnable) {
        this.qosEnable = qosEnable;
    }

    @Parameter(key = QOS_HOST)
    public String getQosHost() {
        return qosHost;
    }

    public void setQosHost(String qosHost) {
        this.qosHost = qosHost;
    }

    @Parameter(key = QOS_PORT)
    public Integer getQosPort() {
        return qosPort;
    }

    public void setQosPort(Integer qosPort) {
        this.qosPort = qosPort;
    }

    @Parameter(key = ACCEPT_FOREIGN_IP)
    public Boolean getQosAcceptForeignIp() {
        return qosAcceptForeignIp;
    }

    public void setQosAcceptForeignIp(Boolean qosAcceptForeignIp) {
        this.qosAcceptForeignIp = qosAcceptForeignIp;
    }

    /**
     * The format is the same as the springboot, including: getQosEnableCompatible(), getQosPortCompatible(), getQosAcceptForeignIpCompatible().
     *
     * @return
     */
    @Parameter(key = "qos-enable", excluded = true)
    public Boolean getQosEnableCompatible() {
        return getQosEnable();
    }

    public void setQosEnableCompatible(Boolean qosEnable) {
        setQosEnable(qosEnable);
    }

    @Parameter(key = "qos-host", excluded = true)
    public String getQosHostCompatible() {
        return getQosHost();
    }

    public void setQosHostCompatible(String qosHost) {
        this.setQosHost(qosHost);
    }

    @Parameter(key = "qos-port", excluded = true)
    public Integer getQosPortCompatible() {
        return getQosPort();
    }

    public void setQosPortCompatible(Integer qosPort) {
        this.setQosPort(qosPort);
    }

    @Parameter(key = "qos-accept-foreign-ip", excluded = true)
    public Boolean getQosAcceptForeignIpCompatible() {
        return this.getQosAcceptForeignIp();
    }

    public void setQosAcceptForeignIpCompatible(Boolean qosAcceptForeignIp) {
        this.setQosAcceptForeignIp(qosAcceptForeignIp);
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public String getShutwait() {
        return shutwait;
    }

    public void setShutwait(String shutwait) {
        System.setProperty(SHUTDOWN_WAIT_KEY, shutwait);
        this.shutwait = shutwait;
    }

    @Parameter(excluded = true)
    public String getHostname() {
        if (hostname == null) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                LOGGER.warn("Failed to get the hostname of current instance.", e);
                hostname = "UNKNOWN";
            }
        }
        return hostname;
    }

    @Override
    @Parameter(excluded = true)
    public boolean isValid() {
        return !StringUtils.isEmpty(name);
    }

    @Parameter(key = METADATA_KEY) // xml 配置为 "metadata-type"，不过会映射到metadataType
    public String getMetadataType() {
        return metadataType;
    }

    public void setMetadataType(String metadataType) {
        this.metadataType = metadataType;
    }

    public Boolean getRegisterConsumer() {
        return registerConsumer;
    }

    public void setRegisterConsumer(Boolean registerConsumer) {
        this.registerConsumer = registerConsumer;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    @Parameter(key = "metadata-service-port")
    public Integer getMetadataServicePort() {
        return metadataServicePort;
    }

    public void setMetadataServicePort(Integer metadataServicePort) {
        this.metadataServicePort = metadataServicePort;
    }

    @Override
    public void refresh() {
        // 进去
        super.refresh();
        appendEnvironmentProperties();
    }

    private void appendEnvironmentProperties() {
        if (parameters == null) {
            parameters = new HashMap<>();
        }

        // 返回的为EnvironmentAdapter（看SPI文件）
        Set<InfraAdapter> adapters = ExtensionLoader.getExtensionLoader(InfraAdapter.class).getSupportedExtensionInstances();
        if (CollectionUtils.isNotEmpty(adapters)) {
            Map<String, String> inputParameters = new HashMap<>();
            inputParameters.put(APPLICATION_KEY, getName());
            inputParameters.put(HOST_KEY, getHostname());
            //inputParameters = {HashMap@2087}  size = 2
            // "application" -> "app"
            // "host" -> "B-RHDTJG5H-2145.local"

            for (InfraAdapter adapter : adapters) {
                // 进去
                Map<String, String> extraParameters = adapter.getExtraAttributes(inputParameters);
                if (CollectionUtils.isNotEmptyMap(extraParameters)) {
                    extraParameters.forEach((key, value) -> {
                        String prefix = this.getPrefix() + ".";
                        if (key.startsWith(prefix)) {
                            key = key.substring(prefix.length());
                        }
                        // 填充到parameters
                        parameters.put(key, value);
                    });
                }
            }
        }
    }
}

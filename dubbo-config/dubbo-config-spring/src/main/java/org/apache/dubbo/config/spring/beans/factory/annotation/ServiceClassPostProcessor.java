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
package org.apache.dubbo.config.spring.beans.factory.annotation;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.config.MethodConfig;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.config.annotation.Method;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.context.DubboBootstrapApplicationListener;
import org.apache.dubbo.config.spring.context.annotation.DubboClassPathBeanDefinitionScanner;
import org.apache.dubbo.config.spring.schema.AnnotationBeanDefinitionParser;

import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.alibaba.spring.util.BeanRegistrar.registerInfrastructureBean;
import static com.alibaba.spring.util.ObjectUtils.of;
import static java.util.Arrays.asList;
import static org.apache.dubbo.config.spring.beans.factory.annotation.ServiceBeanNameBuilder.create;
import static org.apache.dubbo.config.spring.util.DubboAnnotationUtils.resolveServiceInterfaceClass;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;
import static org.springframework.context.annotation.AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;
import static org.springframework.core.annotation.AnnotationUtils.getAnnotationAttributes;
import static org.springframework.util.ClassUtils.resolveClassName;

/**
 * {@link BeanFactoryPostProcessor} used for processing of {@link Service @Service} annotated classes. it's also the
 * infrastructure class of XML {@link BeanDefinitionParser} on &lt;dubbbo:annotation /&gt;
 *
 * @see AnnotationBeanDefinitionParser
 * @see BeanDefinitionRegistryPostProcessor
 * @since 2.7.7
 */
// OK
//  即实现postProcessBeanDefinitionRegistry方法，可以修改增加BeanDefinition。
//  此特性可以用来动态生成bean，比如读取某个配置项，然后根据配置项动态生成bean
//  BeanClassLoaderAware接口和BeanFactoryAware接口同理，可以分别获取Bean的类装载器和bean工厂
// ServiceClassPostProcessor implements BeanDefinitionRegistryPostProcessor，实现该接口的两个方法，postProcessBeanFactory方法空实现，关键看postProcessBeanDefinitionRegistry，这个方法的作用是注册更多的bean到spring容器中，因为该方法有一个BeanDefinitionRegistry类型参数，BeanDefinitionRegistry提供了丰富的方法来操作bean定义，判断、注册、反注册等方法都准备好了，我们在编写postProcessBeanDefinitionRegistry方法的内容时，就能直接使用入参registry的这些方法来完成判断和注册、反注册等操作（当然ServiceClassPostProcessor这个bean要保证被注册到spring容器（以@Bean、xml都可以），才会调这个postProcessBeanDefinitionRegistry）。
public class ServiceClassPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware,
        ResourceLoaderAware, BeanClassLoaderAware {

    private final static List<Class<? extends Annotation>> serviceAnnotationTypes = asList(
            // @since 2.7.7 Add the @DubboService , the issue : https://github.com/apache/dubbo/issues/6007
            DubboService.class,
            // @since 2.7.0 the substitute @com.alibaba.dubbo.config.annotation.Service
            Service.class,
            // @since 2.7.3 Add the compatibility for legacy Dubbo's @Service , the issue : https://github.com/apache/dubbo/issues/4330
            com.alibaba.dubbo.config.annotation.Service.class
    );

    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Set<String> packagesToScan;


    private Environment environment;

    private ResourceLoader resourceLoader;

    private ClassLoader classLoader;

    public ServiceClassPostProcessor(String... packagesToScan) {
        // 传进来的字符串格式为${packagesToScan}
        this(asList(packagesToScan));
    }

    public ServiceClassPostProcessor(Collection<String> packagesToScan) {
        this(new LinkedHashSet<>(packagesToScan));
    }

    public ServiceClassPostProcessor(Set<String> packagesToScan) {
        this.packagesToScan = packagesToScan;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {

        // 向registry注册DubboBootstrapApplicationListener监听器bean
        registerInfrastructureBean(registry, DubboBootstrapApplicationListener.BEAN_NAME, DubboBootstrapApplicationListener.class);

        // 内部利用 environment 对 placeHolder 解析
        Set<String> resolvedPackagesToScan = resolvePackagesToScan(packagesToScan);

        if (!CollectionUtils.isEmpty(resolvedPackagesToScan)) {
            // 注册bean
            registerServiceBeans(resolvedPackagesToScan, registry);
        } else {
            if (logger.isWarnEnabled()) {
                logger.warn("packagesToScan is empty , ServiceBean registry will be ignored!");
            }
        }

    }

    /**
     * Registers Beans whose classes was annotated {@link Service}
     *
     * @param packagesToScan The base packages to scan
     * @param registry       {@link BeanDefinitionRegistry}
     */
    private void registerServiceBeans(Set<String> packagesToScan, BeanDefinitionRegistry registry) {

        // 先创建DubboClassPathBeanDefinitionScanner，一会调用其scan进行包扫描并注册BeanDefinition。
            // 注意：我们在使用ServiceClassPostProcessorTest测试程序的时候，其指定了package为org.apache.dubbo.config.spring.context.annotation.provider
            // 但是这里面的bean还是加了spring 的 @Service注解，其实是没必要的，因为 DubboClassPathBeanDefinitionScanner 这个扫描器内部传递给父类的
            // useDefaultFilters值为false，表示并不会处理这些带有spring原生注解的类，而下面我们通过addIncludeFilter指定了要扫描的（dubbo相关的注解）注解。
        DubboClassPathBeanDefinitionScanner scanner =
                new DubboClassPathBeanDefinitionScanner(registry, environment, resourceLoader);

        // 获取 BeanNameGenerator
        BeanNameGenerator beanNameGenerator = resolveBeanNameGenerator(registry);

        // ClassPathBeanDefinitionScanner#doScan 需要 BeanNameGenerator，来生成beanName
        scanner.setBeanNameGenerator(beanNameGenerator);

        // refactor @since 2.7.7
        // 然后scanner.addIncludeFilter设置哪些注解可以被扫描并处理。这些注解就是@DubboService、@Service、阿里@Service，正好我们使用的DubboClassPathBeanDefinitionScanner构造函数内部useDefaultFilters=false，即并不会处理原生的那些@Component、@Repository、@Service、@Controller、@ManagedBean、@Named注解。
        serviceAnnotationTypes.forEach(annotationType -> {
            // 内部有一个List<TypeFilter> includeFilters属性，扫描的时候用includeFilters 去过滤时，会找到并处理这4个注解的类
            scanner.addIncludeFilter(new AnnotationTypeFilter(annotationType));
        });

        for (String packageToScan : packagesToScan) {

            // 扫描指定的包，并注册beanDefinition   scan内部会调用DubboClassPathBeanDefinitionScanner#doScan
            // Registers @Service Bean first
            scanner.scan(packageToScan);

            // 查找@Service的所有BeanDefinitionHolders，这里的@Service不是单单那个注解，而是泛指上面 scanner.addIncludeFilter 的 serviceAnnotationTypes
            // 这里是获取到上面scan扫描并注册的beanDefinitionHolder，然后需要按照我们的格式再次注册一次（后面的registerServiceBean）
            Set<BeanDefinitionHolder> beanDefinitionHolders =
                    findServiceBeanDefinitionHolders(scanner, packageToScan, registry, beanNameGenerator);

            if (!CollectionUtils.isEmpty(beanDefinitionHolders)) {

                for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders) {
                    registerServiceBean(beanDefinitionHolder, registry, scanner);// 进去
                }

                if (logger.isInfoEnabled()) {
                    logger.info(beanDefinitionHolders.size() + " annotated Dubbo's @Service Components { " +
                            beanDefinitionHolders +
                            " } were scanned under package[" + packageToScan + "]");
                }

            } else {

                if (logger.isWarnEnabled()) {
                    logger.warn("No Spring Bean annotating Dubbo's @Service was found under package["
                            + packageToScan + "]");
                }

            }

        }

    }

    /**
     * It'd better to use BeanNameGenerator instance that should reference
     * {@link ConfigurationClassPostProcessor#componentScanBeanNameGenerator},
     * thus it maybe a potential problem on bean name generation.
     *
     * @param registry {@link BeanDefinitionRegistry}
     * @return {@link BeanNameGenerator} instance
     * @see SingletonBeanRegistry
     * @see AnnotationConfigUtils#CONFIGURATION_BEAN_NAME_GENERATOR
     * @see ConfigurationClassPostProcessor#processConfigBeanDefinitions
     * @since 2.5.8
     */
    private BeanNameGenerator resolveBeanNameGenerator(BeanDefinitionRegistry registry) {

        BeanNameGenerator beanNameGenerator = null;

        if (registry instanceof SingletonBeanRegistry) {
            SingletonBeanRegistry singletonBeanRegistry = SingletonBeanRegistry.class.cast(registry);
            beanNameGenerator = (BeanNameGenerator) singletonBeanRegistry.getSingleton(CONFIGURATION_BEAN_NAME_GENERATOR);
        }

        if (beanNameGenerator == null) {

            if (logger.isInfoEnabled()) {

                logger.info("BeanNameGenerator bean can't be found in BeanFactory with name ["
                        + CONFIGURATION_BEAN_NAME_GENERATOR + "]");
                logger.info("BeanNameGenerator will be a instance of " +
                        AnnotationBeanNameGenerator.class.getName() +
                        " , it maybe a potential problem on bean name generation.");
            }

            beanNameGenerator = new AnnotationBeanNameGenerator();

        }

        return beanNameGenerator;

    }

    /**
     * Finds a {@link Set} of {@link BeanDefinitionHolder BeanDefinitionHolders} whose bean type annotated
     * {@link Service} Annotation.
     *
     * @param scanner       {@link ClassPathBeanDefinitionScanner}
     * @param packageToScan pachage to scan
     * @param registry      {@link BeanDefinitionRegistry}
     * @return non-null
     * @since 2.5.8
     */
    private Set<BeanDefinitionHolder> findServiceBeanDefinitionHolders(
            ClassPathBeanDefinitionScanner scanner, String packageToScan, BeanDefinitionRegistry registry,
            BeanNameGenerator beanNameGenerator) {

        // 扫描packageToScan路径下的java文件
        Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents(packageToScan);

        Set<BeanDefinitionHolder> beanDefinitionHolders = new LinkedHashSet<>(beanDefinitions.size());

        for (BeanDefinition beanDefinition : beanDefinitions) {

            // beanNameGenerator为AnnotationBeanNameGenerator，生成beanName，先判断注解上有没有显示设置beanName，没有的话，就以类名小写为beanName
            String beanName = beanNameGenerator.generateBeanName(beanDefinition, registry);
            // BeanDefinitionHolder 就是 BeanDefinition + beanName
            BeanDefinitionHolder beanDefinitionHolder = new BeanDefinitionHolder(beanDefinition, beanName);
            beanDefinitionHolders.add(beanDefinitionHolder);

        }

        return beanDefinitionHolders;

    }

    /**
     * Registers {@link ServiceBean} from new annotated {@link Service} {@link BeanDefinition}
     *
     * @param beanDefinitionHolder
     * @param registry
     * @param scanner
     * @see ServiceBean
     * @see BeanDefinition
     */
    private void registerServiceBean(BeanDefinitionHolder beanDefinitionHolder, BeanDefinitionRegistry registry,
                                     DubboClassPathBeanDefinitionScanner scanner) {

        // 加载类
        Class<?> beanClass = resolveClass(beanDefinitionHolder);
        // 获取beanClass类上的注解，比如@DubboService
        Annotation service = findServiceAnnotation(beanClass);

        /**
         * The {@link AnnotationAttributes} of @Service annotation
         */
        AnnotationAttributes serviceAnnotationAttributes = getAnnotationAttributes(service, false, false);

        // 加载服务接口类
        Class<?> interfaceClass = resolveServiceInterfaceClass(serviceAnnotationAttributes, beanClass);
        // 获取beanName
        String annotatedServiceBeanName = beanDefinitionHolder.getBeanName();

        AbstractBeanDefinition serviceBeanDefinition =
                buildServiceBeanDefinition(service, serviceAnnotationAttributes, interfaceClass, annotatedServiceBeanName);

        // ServiceBean Bean name
        String beanName = generateServiceBeanName(serviceAnnotationAttributes, interfaceClass);

        // 返回true 表示可以进行注册，false表示已经有了
        if (scanner.checkCandidate(beanName, serviceBeanDefinition)) { // check duplicated candidate bean
            registry.registerBeanDefinition(beanName, serviceBeanDefinition);

            if (logger.isInfoEnabled()) {
                logger.info("The BeanDefinition[" + serviceBeanDefinition +
                        "] of ServiceBean has been registered with name : " + beanName);
            }

        } else {

            if (logger.isWarnEnabled()) {
                logger.warn("The Duplicated BeanDefinition[" + serviceBeanDefinition +
                        "] of ServiceBean[ bean name : " + beanName +
                        "] was be found , Did @DubboComponentScan scan to same package in many times?");
            }

        }

    }

    /**
     * Find the {@link Annotation annotation} of @Service
     *
     * @param beanClass the {@link Class class} of Bean
     * @return <code>null</code> if not found
     * @since 2.7.3
     */
    /*
    findMergedAnnotation是spring的AnnotatedElementUtils类的方法，该方法的作用是在beanClass上查询annotationType类型注解，将查询出
    的多个annotationType类型注解属性合并到查询的第一个注解中，就是多个相同注解合并（一般beanClass上很少有多个相同类型注解，比如beanClass有多个@Service注解）。
    Objects::nonNull是rt.jar的，该方法就是找到beanClass上第一个（findFirst）在serviceAnnotationTypes出现的注解。*/
    private Annotation findServiceAnnotation(Class<?> beanClass) {
        return serviceAnnotationTypes
                .stream()
                .map(annotationType -> findMergedAnnotation(beanClass, annotationType))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Generates the bean name of {@link ServiceBean}
     *
     * @param serviceAnnotationAttributes
     * @param interfaceClass              the class of interface annotated {@link Service}
     * @return ServiceBean@interfaceClassName#annotatedServiceBeanName
     * @since 2.7.3
     */
    private String generateServiceBeanName(AnnotationAttributes serviceAnnotationAttributes, Class<?> interfaceClass) {
        ServiceBeanNameBuilder builder = create(interfaceClass, environment)
                .group(serviceAnnotationAttributes.getString("group"))
                .version(serviceAnnotationAttributes.getString("version"));
        return builder.build();
    }

    private Class<?> resolveClass(BeanDefinitionHolder beanDefinitionHolder) {

        BeanDefinition beanDefinition = beanDefinitionHolder.getBeanDefinition();
        String beanClassName = beanDefinition.getBeanClassName();
        // 加载类
        return resolveClassName(beanClassName, classLoader);
    }

    private Set<String> resolvePackagesToScan(Set<String> packagesToScan) {
        Set<String> resolvedPackagesToScan = new LinkedHashSet<String>(packagesToScan.size());
        for (String packageToScan : packagesToScan) {
            if (StringUtils.hasText(packageToScan)) {
                String resolvedPackageToScan = environment.resolvePlaceholders(packageToScan.trim());
                resolvedPackagesToScan.add(resolvedPackageToScan);
            }
        }
        return resolvedPackagesToScan;
    }

    /**
     * Build the {@link AbstractBeanDefinition Bean Definition}
     *
     * @param serviceAnnotation
     * @param serviceAnnotationAttributes
     * @param interfaceClass
     * @param annotatedServiceBeanName
     * @return
     * @since 2.7.3
     */
    // 根据注解扫描到的"原bean"，生成对应的ServiceBean-BeanDefinition注册到容器，至于ServiceBean的属性填充就是来自"原bean"。
    // 之所以需要这个ServiceBean，是因为他是我们进行export的关键！（我们在讲述xml配置方式的时候已经说了这个ServiceBean）
    private AbstractBeanDefinition buildServiceBeanDefinition(Annotation serviceAnnotation,
                                                              AnnotationAttributes serviceAnnotationAttributes,
                                                              Class<?> interfaceClass,
                                                              String annotatedServiceBeanName) {

        //BeanDefinitionBuilder#rootBeanDefinition, RootBeanDefinition可用于没有继承关系的Bean的创建
        BeanDefinitionBuilder builder = rootBeanDefinition(ServiceBean.class);

        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();

        MutablePropertyValues propertyValues = beanDefinition.getPropertyValues();

        String[] ignoreAttributeNames = of("provider", "monitor", "application", "module", "registry", "protocol",
                "interface", "interfaceName", "parameters");

        propertyValues.addPropertyValues(new AnnotationPropertyValuesAdapter(serviceAnnotation, environment, ignoreAttributeNames));


        // Set interface
        builder.addPropertyValue("interface", interfaceClass.getName());
        // Convert parameters into map
        builder.addPropertyValue("parameters", convertParameters(serviceAnnotationAttributes.getStringArray("parameters")));
        // Add methods parameters
        List<MethodConfig> methodConfigs = convertMethodConfigs(serviceAnnotationAttributes.get("methods"));
        if (!methodConfigs.isEmpty()) {
            builder.addPropertyValue("methods", methodConfigs);
        }

        // ====================================================================

        // 如下代码是处理各个bean依赖，即处理的是这个ServiceBean里面非基本数据类型的的实体引用，且这些实体也是bean。这些信息都会保存在前面的propertyValues，
        // 自己可以打断点看一下，只需要注意每一个entry的value部分是基本数据类型，还是其他bean，如下案例

        // propertyValueList = {ArrayList@3477}  size = 8
        // 0 = {PropertyValue@3553} "bean property 'methods'"
        // 1 = {PropertyValue@3487} "bean property 'version'"
        // 2 = {PropertyValue@3508} "bean property 'interface'"
        //  name = "interface"
        //  value = "my.config.spring.api.DemoService"
        // ......
        //  attributes = {LinkedHashMap@3796}  size = 0
        // 3 = {PropertyValue@3509} "bean property 'parameters'"
        // 4 = {PropertyValue@3574} "bean property 'ref'"
        // 5 = {PropertyValue@3674} "bean property 'application'"
        //  name = "application"
        //  value = {RuntimeBeanReference@3681} "<dubbo-demo-application>"
        // 6 = {PropertyValue@3721} "bean property 'registries'"
        //  name = "registries"
        //  value = {ManagedList@3692}  size = 1
        //   0 = {RuntimeBeanReference@3806} "<my-registry>"

        // ====================================================================

        // 还有一个注意的点，比如该代码后面给加了一个这个属性（以这个为例）   addPropertyReference(builder, "application", applicationConfigBeanName)
        // 而applicationConfigBeanName的值为 DemoServiceImpl类上 application = "${demo.service.application}" ，
        // 而 ${demo.service.application} 在default.properties文件的内容为dubbo-demo-application，正好
        // 和ServiceAnnotationTestConfiguration2测试程序注册的@Bean("dubbo-demo-application") 一致 。
        // 还有protocol、registry属性等都是这样的。

        // ====================================================================

        // References "ref" property to annotated-@Service Bean
        addPropertyReference(builder, "ref", annotatedServiceBeanName);

        /**
         * Add {@link org.apache.dubbo.config.ProviderConfig} Bean reference
         */
        String providerConfigBeanName = serviceAnnotationAttributes.getString("provider");
        if (StringUtils.hasText(providerConfigBeanName)) {
            addPropertyReference(builder, "provider", providerConfigBeanName);
        }

        /**
         * Add {@link org.apache.dubbo.config.MonitorConfig} Bean reference
         */
        String monitorConfigBeanName = serviceAnnotationAttributes.getString("monitor");
        if (StringUtils.hasText(monitorConfigBeanName)) {
            addPropertyReference(builder, "monitor", monitorConfigBeanName);
        }

        /**
         * Add {@link org.apache.dubbo.config.ApplicationConfig} Bean reference
         */
        String applicationConfigBeanName = serviceAnnotationAttributes.getString("application");
        if (StringUtils.hasText(applicationConfigBeanName)) {
            addPropertyReference(builder, "application", applicationConfigBeanName);
        }

        /**
         * Add {@link org.apache.dubbo.config.ModuleConfig} Bean reference
         */
        String moduleConfigBeanName = serviceAnnotationAttributes.getString("module");
        if (StringUtils.hasText(moduleConfigBeanName)) {
            addPropertyReference(builder, "module", moduleConfigBeanName);
        }


        /**
         * Add {@link org.apache.dubbo.config.RegistryConfig} Bean reference
         */
        String[] registryConfigBeanNames = serviceAnnotationAttributes.getStringArray("registry");

        List<RuntimeBeanReference> registryRuntimeBeanReferences = toRuntimeBeanReferences(registryConfigBeanNames);

        if (!registryRuntimeBeanReferences.isEmpty()) {
            builder.addPropertyValue("registries", registryRuntimeBeanReferences);
        }

        /**
         * Add {@link org.apache.dubbo.config.ProtocolConfig} Bean reference
         */
        String[] protocolConfigBeanNames = serviceAnnotationAttributes.getStringArray("protocol");

        List<RuntimeBeanReference> protocolRuntimeBeanReferences = toRuntimeBeanReferences(protocolConfigBeanNames);

        if (!protocolRuntimeBeanReferences.isEmpty()) {
            builder.addPropertyValue("protocols", protocolRuntimeBeanReferences);
        }

        return builder.getBeanDefinition();

    }

    private List convertMethodConfigs(Object methodsAnnotation) {
        if (methodsAnnotation == null) {
            return Collections.EMPTY_LIST;
        }
        return MethodConfig.constructMethodConfig((Method[]) methodsAnnotation);
    }

    private ManagedList<RuntimeBeanReference> toRuntimeBeanReferences(String... beanNames) {

        ManagedList<RuntimeBeanReference> runtimeBeanReferences = new ManagedList<>();

        if (!ObjectUtils.isEmpty(beanNames)) {

            for (String beanName : beanNames) {

                String resolvedBeanName = environment.resolvePlaceholders(beanName);

                runtimeBeanReferences.add(new RuntimeBeanReference(resolvedBeanName));
            }

        }

        return runtimeBeanReferences;

    }

    private void addPropertyReference(BeanDefinitionBuilder builder, String propertyName, String beanName) {
        String resolvedBeanName = environment.resolvePlaceholders(beanName);// 比如beanName为${demo.service.application}
        //  设置属性propertyName,此属性引用已经定义的resolvedBeanName
        builder.addPropertyReference(propertyName, resolvedBeanName);
    }

    private Map<String, String> convertParameters(String[] parameters) {
        if (ArrayUtils.isEmpty(parameters)) {
            return null;
        }

        if (parameters.length % 2 != 0) {
            throw new IllegalArgumentException("parameter attribute must be paired with key followed by value");
        }

        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < parameters.length; i += 2) {
            map.put(parameters[i], parameters[i + 1]);
        }
        return map;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}

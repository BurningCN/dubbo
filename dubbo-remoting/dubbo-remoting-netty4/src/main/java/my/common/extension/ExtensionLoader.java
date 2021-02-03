package my.common.extension;

import my.server.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/**
 * @author geyu
 * @date 2021/2/3 17:30
 */
public class ExtensionLoader<T> {


    private static final Pattern NAME_separator = Pattern.compile("\\s*[,]+\\s*");
    private static volatile LoadingStrategy[] strategies = loadLoadingStrategies();
    private static Map<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();
    private final Class<?> type;
    private final ExtensionFactory extensionFactory;
    private volatile Object cachedAdaptiveInstance; // 有双重检查的加volatile
    private volatile Throwable createAdaptiveInstanceError;
    private volatile Class<?> cachedAdaptiveClass;
    private volatile String cacheDefaultName;
    private Map<String, Class<?>> cachedClasses = new HashMap<>();
    private Map<Class<?>, String> cachedNames = new HashMap<>();
    private Set<Class<?>> cachedWrapperClasses = new HashSet<>();
    private Map<String, Activate> cachedActivates = new HashMap<>();


    public ExtensionLoader(Class<?> type) {
        this.type = type;
        this.extensionFactory = type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension();

    }


    private static LoadingStrategy[] loadLoadingStrategies() {
        return StreamSupport.stream(ServiceLoader.load(LoadingStrategy.class).spliterator(), false)
                .sorted().toArray(LoadingStrategy[]::new);
    }


    public static void setStrategies(LoadingStrategy[] strategies) {
        ExtensionLoader.strategies = strategies;
    }

    public static LoadingStrategy[] getStrategies() {
        return strategies;
    }


    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");

        }
        if (!type.isAnnotationPresent(SPI.class)) {
            throw new IllegalArgumentException("Extension type (" + type +
                    ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
        }
        ExtensionLoader<?> extensionLoader = EXTENSION_LOADERS.get(type);
        if (extensionLoader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<>(type));
            extensionLoader = EXTENSION_LOADERS.get(type);
        }
        return (ExtensionLoader<T>) extensionLoader;
    }

    public T getAdaptiveExtension() {
        if (cachedAdaptiveInstance == null) {
            if (createAdaptiveInstanceError != null) {
                throw new IllegalStateException("Failed to create adaptive instance: " +
                        createAdaptiveInstanceError.toString(),
                        createAdaptiveInstanceError);
            }
            synchronized (cachedAdaptiveInstance) {
                if (cachedAdaptiveInstance == null) {
                    try {
                        cachedAdaptiveInstance = createAdaptiveExtension();
                    } catch (Throwable e) {
                        createAdaptiveInstanceError = e;
                        throw new IllegalStateException("Failed to create adaptive instance: " + e.toString(), e);
                    }
                }
            }
        }
        return (T) cachedAdaptiveInstance;
    }

    private T createAdaptiveExtension() {
        try {
            T instance = (T) getAdaptiveExtensionClass().newInstance();
            return (T) injectExtension(instance);
        } catch (Exception e) {
            throw new IllegalStateException("Can't create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }


    private Class<?> getAdaptiveExtensionClass() {
        getExtensionClasses();
        if (cachedAdaptiveClass == null) {
            cachedAdaptiveClass = createAdaptiveExtensionClass();
        }
        return cachedAdaptiveClass;
    }

    private Class<?> createAdaptiveExtensionClass() {

        synchronized (cachedAdaptiveClass){
            // todo
        }
        return null;
    }

    private Map<String, Class<?>> getExtensionClasses() {
        if (cachedClasses.size() == 0) {
            synchronized (cachedClasses) {
                loadExtensionClasses();
            }
        }
        return cachedClasses;
    }

    private void loadExtensionClasses() {
        cacheDefaultExtensionName();
        for (LoadingStrategy strategy : strategies) {
            try {
                Enumeration<URL> resources = getResources(strategy);
                if (resources != null) {
                    parseResourceAndLoadClass(strategy, resources);
                }
            } catch (IOException e) {

            }
        }
    }


    private void cacheDefaultExtensionName() {
        SPI annotation = type.getAnnotation(SPI.class);
        if (annotation != null && annotation.value() != null && annotation.value().trim().length() > 0) {
            String[] names = NAME_separator.split(annotation.value());
            if (names.length > 1) {
                throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                        + ": " + Arrays.toString(names));
            } else {
                cacheDefaultName = names[0];
            }
        }
    }

    private Enumeration<URL> getResources(LoadingStrategy strategy) throws IOException {
        String path = strategy.directory() + type.getName();
        Enumeration<URL> resources = null;
        if (strategy.preferExtensionClassLoader()) {
            ClassLoader loader = ExtensionLoader.class.getClassLoader();
            if (ClassLoader.getSystemClassLoader() != loader) {
                resources = loader.getResources(path);
            }
        } else {
            resources = ClassUtils.getClassLoader(type).getResources(path);
        }
        return resources;
    }

    private void parseResourceAndLoadClass(LoadingStrategy strategy, Enumeration<URL> resources) {
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
                String line = br.readLine();
                int i = line.trim().indexOf("#");
                if (i > 0) {
                    line = line.substring(0, i);
                }
                int i1 = line.trim().indexOf("=");
                if (i1 > 0) {
                    String extName = line.substring(0, i1).trim();
                    String classPath = line.substring(i1).trim();
                    boolean excluded = false;
                    if (classPath.length() > 0 && strategy.excludePackages() != null) {
                        for (String excludePackage : strategy.excludePackages()) {
                            if (classPath.startsWith(excludePackage)) {
                                excluded = true;
                                break;
                            }
                        }
                    }
                    if (!excluded) {
                        loadClass(extName, classPath, strategy);
                    }
                }
            } catch (Throwable t) {
//                            logger.error("Exception occurred when loading extension class (interface: " +
//                                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
            }
        }
    }

    private void loadClass(String extName, String classPath, LoadingStrategy strategy) {
        try {
            Class<?> clazz = Class.forName(classPath, true, ClassUtils.getClassLoader(type));
            if (type.isAssignableFrom(clazz)) {
                throw new IllegalStateException("Error occurred when loading extension class (interface: " +
                        type + ", class line: " + clazz.getName() + "), class "
                        + clazz.getName() + " is not subtype of interface.");
            }
            if (clazz.isAnnotationPresent(Adaptive.class)) { // 1.Adaptive
                if (cachedAdaptiveClass == null || strategy.enableOverridden()) {
                    cachedAdaptiveClass = clazz;
                } else {
                    throw new IllegalStateException("More than 1 adaptive class found: "
                            + cachedAdaptiveClass.getName()
                            + ", " + clazz.getName());
                }
            } else if (clazz.getConstructor(type) != null) {// 2.Wrapper
                cachedWrapperClasses.add(clazz);
            } else {                                        //3.activate || normal
                clazz.getConstructor();
                if (StringUtils.isEmpty(extName)) {
                    String name = clazz.getSimpleName();
                    if (name.endsWith(type.getSimpleName())) {
                        extName = clazz.getName().substring(
                                0, name.length() - type.getSimpleName().length()).toLowerCase();
                    }
                }
                String[] names = NAME_separator.split(extName);
                for (String name : names) {
                    cachedNames.putIfAbsent(clazz, name);
                    cachedClasses.putIfAbsent(name, clazz);
                }

                String aName = names[0];
                Activate activate = clazz.getAnnotation(Activate.class);
                if (activate != null) {
                    cachedActivates.putIfAbsent(aName, activate);
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private Object injectExtension(T instance) {
        return null;
    }
}


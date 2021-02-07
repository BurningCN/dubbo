package my.common.rpc.model;

import my.common.utils.ArrayUtils;
import my.rpc.CollectionUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author geyu
 * @version 1.0
 * @date 2021/2/6 12:53
 */
public class ServiceDescriptor {
    private final Class<?> serviceInterfaceClass;
    private final String serviceName;
    private Map<String, List<MethodDescriptor>> methods = new HashMap<>();
    private Map<String, Map<String, MethodDescriptor>> descToMethods = new HashMap<>();

    public ServiceDescriptor(Class<?> interfaceClass) {
        this.serviceInterfaceClass = interfaceClass;
        this.serviceName = interfaceClass.getName();
        initMethods();
    }

    private void initMethods() {
        Method[] methods = serviceInterfaceClass.getMethods();
        for (Method method : methods) {
            method.setAccessible(true); // 其实不需要，因为前面getMethods就是true的
            List<MethodDescriptor> methodDescriptors = this.methods.computeIfAbsent(method.getName(), k -> new ArrayList<>());
            methodDescriptors.add(new MethodDescriptor(method));
        }
        this.methods.forEach((methodName, methodList) -> {
            Map<String, MethodDescriptor> descriptorMap = descToMethods.computeIfAbsent(methodName, k -> new HashMap<>());
            methodList.forEach(methodDescriptor -> {
                descriptorMap.put(methodDescriptor.getParamDesc(), methodDescriptor);
            });
        });

    }

    public MethodDescriptor getMethod(String methodName, String desc) {
        Map<String, MethodDescriptor> descriptorMap = descToMethods.get(methodName);
        if (CollectionUtils.isNotEmptyMap(descriptorMap)) {
            MethodDescriptor methodDescriptor = descriptorMap.get(desc);
            return methodDescriptor;
        }
        return null;
    }

    public MethodDescriptor getMethod(String methodName, Class<?>[] parameterClasses) {
        Map<String, MethodDescriptor> descriptorMap = descToMethods.get(methodName);
        if (CollectionUtils.isNotEmptyMap(descriptorMap)) {
            for (MethodDescriptor methodDescriptor : descriptorMap.values()) {
                if (Arrays.equals(parameterClasses, methodDescriptor.getParameterClasses())) {
                    return methodDescriptor;
                }
            }
        }
        return null;
    }
}

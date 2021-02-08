package my.common.rpc.model;

import my.common.context.FrameworkExt;
import my.common.context.LifecycleAdapter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author geyu
 * @version 1.0
 * @date 2021/2/6 12:49
 */
public class ServiceRepository extends LifecycleAdapter implements FrameworkExt {


    private ConcurrentHashMap<String, ServiceDescriptor> services = new ConcurrentHashMap<>();
    public static String NAME = "repository";

    public ServiceDescriptor registerService(String path, Class<?> interfaceClass) {
        ServiceDescriptor serviceDescriptor = registerService(interfaceClass);
        if (interfaceClass.getName() != path) {
            services.putIfAbsent(path, serviceDescriptor);
        }
        return serviceDescriptor;
    }

    public ServiceDescriptor registerService(Class<?> interfaceClass) {
        return services.computeIfAbsent(interfaceClass.getName(), k -> new ServiceDescriptor(interfaceClass));
    }

    public ServiceDescriptor lookupService(String path) {
        return services.get(path);
    }

    @Override
    public void initialize() throws IllegalStateException {

    }

    @Override
    public void start() throws IllegalStateException {

    }

    @Override
    public void destroy() throws IllegalStateException {
        services.clear();
    }
}

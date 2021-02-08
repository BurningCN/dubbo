package my.rpc;

import my.common.service.EchoService;
import my.server.Constants;
import my.server.URL;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * @author geyu
 * @date 2021/2/4 15:39
 */
public abstract class AbstractProxyFactory implements ProxyFactory {
    private static final Class<?>[] INNER_INTERFACES = {Destroyable.class, EchoService.class};

    private Pattern COMMA_SPLIT_PATTERN = Pattern.compile("\\s*[,]+\\s*");

    @Override
    public <T> T getProxy(Invoker<T> invoker, boolean generic) {
        Set<Class<?>> interfacesSet = new HashSet<>();
        URL url = invoker.getURL();
        String interfaces = url.getParameter(Constants.INTERFACES);
        if (interfaces != null && interfaces.length() > 0) {
            String[] its = COMMA_SPLIT_PATTERN.split(interfaces);
            for (String clz : its) {
                interfacesSet.add(loadClass(clz));
            }
        }
        // todo myRPC 泛化处理

        interfacesSet.addAll(Arrays.asList(INNER_INTERFACES));
        interfacesSet.add(invoker.getInterface()); // 湖之一
        return doGetProxy(invoker, interfacesSet.toArray(new Class[0]));
    }

    protected abstract <T> T doGetProxy(Invoker<T> invoker, Class[] toArray);

    @Override
    public <T> T getProxy(Invoker<T> invoker) {
        return this.getProxy(invoker, false);
    }

    private Class loadClass(String className) {
        try {
            return Class.forName(className, true, this.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}

package netty.server.support;

import java.io.Serializable;

/**
 * @author geyu
 * @date 2021/2/2 20:09
 */
public class RpcMessage implements Serializable {

    private static final long serialVersionUID = 1480808417808868806L;
    private String serviceName;

    private String methodName;

    private Class<?>[] parameterTypes;

    private Object[] arguments;

    public RpcMessage(String serviceName, String methodName, Class<?>[] parameterTypes, Object[] arguments) {
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.arguments = arguments;
    }


    public String getServiceName() {
        return serviceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public Object[] getArguments() {
        return arguments;
    }
}

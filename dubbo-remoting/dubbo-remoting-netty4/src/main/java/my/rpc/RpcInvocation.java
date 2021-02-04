package my.rpc;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author geyu
 * @date 2021/2/4 14:18
 */
public class RpcInvocation implements Invocation, Serializable {
    private static final long serialVersionUID = 1508509101233462328L;
    private Invoker<?> invoker;
    private String methodName;
    private Class<?>[] parameterTypes;
    private Object[] arguments;
    private String serviceName;
    private Map<String, Object> attachments = new HashMap<>();
    private transient InvokeMode invokeMode;
    private transient Class<?> returnType;
    public Invoker<?> getInvoker() {
        return invoker;
    }

    public void setInvoker(Invoker<?> invoker) {
        this.invoker = invoker;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getArguments() {
        return arguments;
    }
    public String getAttachment(String key) {
        if (attachments == null) {
            return null;
        }
        return (String) attachments.get(key);
    }

    public String getAttachment(String key, String defaultValue) {
        if (attachments == null) {
            return defaultValue;
        }
        String value = (String) attachments.get(key);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        return value;
    }
    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Map<String, Object> getAttachments() {
        return attachments;
    }

    public void setAttachments(Map<String, Object> attachments) {
        this.attachments = attachments;
    }

    public InvokeMode getInvokeMode() {
        return invokeMode;
    }

    public void setInvokeMode(InvokeMode invokeMode) {
        this.invokeMode = invokeMode;
    }


    public void addAttachmentsIfAbsent(Map<String, Object> attachments) {
        for (Map.Entry<String, Object> entry : attachments.entrySet()) {
            if (!this.attachments.containsKey(entry.getKey())) {
                this.attachments.put(entry.getKey(),entry.getValue());
            }
        }
    }

    public void addAttachments(Map<String, Object> rpcAttachments) {
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public void setReturnType(Class<?> returnType) {
        this.returnType = returnType;
    }
}

package my.common.rpc.model;

import my.common.utils.ReflectUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import static my.common.constants.CommonConstants.*;


/**
 * @author geyu
 * @version 1.0
 * @date 2021/2/6 12:57
 */
public class MethodDescriptor {
    private final Method method;
    private final Class<?>[] parameterClasses;
    private final Class<?> returnType;
    private final String paramDesc;
    private final String methodName;
    private final boolean generic;
    private Type[] returnTypes;

    public MethodDescriptor(Method method) {
        this.method = method;
        this.parameterClasses = method.getParameterTypes();
        this.returnType = method.getReturnType();
        this.returnTypes = ReflectUtils.getReturnTypes(method);
        this.paramDesc = ReflectUtils.getDesc(parameterClasses);
        this.methodName = method.getName();
        this.generic = (methodName.equals($INVOKE) || methodName.equals($INVOKE_ASYNC)) && parameterClasses.length == 3;
    }

    public Type[] getReturnTypes() {
        return returnTypes;
    }

    public Class<?>[] getParameterClasses() {
        return parameterClasses;
    }

    public String getParamDesc() {
        return paramDesc;
    }
}

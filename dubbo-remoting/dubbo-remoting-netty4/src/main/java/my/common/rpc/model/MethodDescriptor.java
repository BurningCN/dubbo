package my.common.rpc.model;

import java.lang.reflect.Type;

/**
 * @author geyu
 * @version 1.0
 * @date 2021/2/6 12:57
 */
public class MethodDescriptor {
    private Type[] returnTypes;

    public Type[] getReturnTypes() {
        return returnTypes;
    }
    public Class<?>[] getParameterClasses() {
        return null;
    }
}

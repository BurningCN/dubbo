package my.rpc;

/**
 * @author geyu
 * @date 2021/2/4 11:28
 */
public interface Invocation {
    String getMethodName();

    Class<?>[] getParameterTypes();

    Object[] getArguments();
}

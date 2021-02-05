package my.rpc;

import java.util.Map;
import java.util.stream.Stream;

/**
 * @author geyu
 * @date 2021/2/4 11:28
 */
public interface Invocation {
    String getTargetServiceUniqueName();

    String getProtocolServiceKey();

    String getMethodName();

    String getServiceName();

    Class<?>[] getParameterTypes();

    default String[] getCompatibleParamSignatures() {
        return Stream.of(getParameterTypes())
                .map(Class::getName)
                .toArray(String[]::new);
    }
    Object[] getArguments();

    Map<String, String> getAttachments();

    Map<String, Object> getObjectAttachments();

    void setAttachment(String key, String value);

    void setAttachment(String key, Object value);

    void setObjectAttachment(String key, Object value);

    void setAttachmentIfAbsent(String key, String value);

    void setAttachmentIfAbsent(String key, Object value);

    void setObjectAttachmentIfAbsent(String key, Object value);

    String getAttachment(String key);

    Object getObjectAttachment(String key);

    String getAttachment(String key, String defaultValue);

    Object getObjectAttachment(String key, Object defaultValue);

    Invoker<?> getInvoker();

    Object put(Object key, Object value);

    Object get(Object key);

    Map<Object, Object> getAttributes();
}

package my.common.extension_todo;

/**
 * @author geyu
 * @date 2021/2/3 19:31
 */
@SPI
public interface ExtensionFactory {
    Object getExtension(Class<?> parameterType, String property);
}

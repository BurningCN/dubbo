package netty.server;

/**
 * @author geyu
 * @date 2021/1/29 23:31
 */
public class ExtensionLoader<T> {
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> codec2Class) {
        return null;
    }

    public boolean hasExtension(String codeName) {
        return false;
    }

    public T getExtension(String codeName) {
        return null;
    }

    public Dispatcher getAdaptiveExtension() {
        return null;
    }

    public T getDefaultExtension() {
        return null;
    }
}

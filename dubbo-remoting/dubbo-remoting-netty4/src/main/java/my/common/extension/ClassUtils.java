package my.common.extension;

/**
 * @author geyu
 * @date 2021/2/3 19:58
 */
public class ClassUtils {
    public static ClassLoader getClassLoader(Class clz) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null && clz != null) {
            cl = clz.getClassLoader();
        }
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        return cl;
    }
}

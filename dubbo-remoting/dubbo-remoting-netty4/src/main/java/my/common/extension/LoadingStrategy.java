package my.common.extension;

/**
 * @author geyu
 * @date 2021/2/3 17:33
 */
@FunctionalInterface
public interface LoadingStrategy extends Prioritized {
    String directory();

    default boolean preferExtensionClassLoader() {
        return false;
    }

    default String[] excludePackages() {
        return null;
    }

    default boolean enableOverridden(){
        return false;
    }
}

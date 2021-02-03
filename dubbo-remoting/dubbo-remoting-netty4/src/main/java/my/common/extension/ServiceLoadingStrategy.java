package my.common.extension;

/**
 * @author geyu
 * @date 2021/2/3 19:10
 */
public class ServiceLoadingStrategy implements LoadingStrategy {
    @Override
    public String directory() {
        return "META-INF/services/";
    }

    @Override
    public int getPriority() {
        return MIN_PRIORITY;
    }
}

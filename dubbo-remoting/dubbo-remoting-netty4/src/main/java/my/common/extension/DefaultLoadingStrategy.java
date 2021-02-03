package my.common.extension;

/**
 * @author geyu
 * @date 2021/2/3 19:13
 */
public class DefaultLoadingStrategy implements LoadingStrategy {
    @Override
    public String directory() {
        return "/META-INF/my/";
    }

    @Override
    public int getPriority() {
        return NORMAL_PRIORITY;
    }
}

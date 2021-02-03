package my.common.extension;

/**
 * @author geyu
 * @date 2021/2/3 19:12
 */
public class InternalLoadingStrategy implements LoadingStrategy{
    @Override
    public String directory() {
        return "/META-INF/my/internal/";
    }

    @Override
    public int getPriority() {
        return MAX_PRIORITY;
    }
}

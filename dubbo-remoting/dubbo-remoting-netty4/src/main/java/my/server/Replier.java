package my.server;

/**
 * @author gy821075
 * @date 2021/2/2 12:26
 */
@FunctionalInterface
public interface Replier<T> {
    Object reply(InnerChannel channel, T request);
}

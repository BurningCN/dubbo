package netty.server;

/**
 * @author gy821075
 * @date 2021/2/1 19:32
 */
public interface IdleSensible {
    default boolean canHandleIdle() {
        return false;
    }
}

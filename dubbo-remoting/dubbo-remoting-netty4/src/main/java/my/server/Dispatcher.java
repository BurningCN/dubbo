package my.server;

/**
 * @author gy821075
 * @date 2021/1/30 12:14
 */
public interface Dispatcher {
    ChannelHandler dispatch(ChannelHandler handler);
}

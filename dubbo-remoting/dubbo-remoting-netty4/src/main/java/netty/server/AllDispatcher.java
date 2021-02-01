package netty.server;

/**
 * @author geyu
 * @date 2021/1/30 12:16
 */
public class AllDispatcher implements Dispatcher{
    @Override
    public ChannelHandler dispatch( ChannelHandler handler) {
        return new AllChannelHandler(handler);
    }
}

package netty.server;

/**
 * @author geyu
 * @date 2021/2/2 17:08
 */
public class FakeChannelHandlers extends ChannelHandlers {

    public static void setFake() {
        setTestingChannelHandlers(new FakeChannelHandlers());
    }

    public static void reset() {
        setTestingChannelHandlers(new ChannelHandlers());
    }

    @Override
    public ChannelHandler wrapInternal(ChannelHandler handler) {
        // 没有心跳Handler
        return handler;
    }
}

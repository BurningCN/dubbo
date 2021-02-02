package netty.server;

/**
 * @author geyu
 * @date 2021/1/30 12:11
 */
public class ChannelHandlers {

    private static ChannelHandlers INSTANCE = new ChannelHandlers();

    public static ChannelHandlers getINSTANCE() {
        return INSTANCE;
    }

    protected static void setTestingChannelHandlers(ChannelHandlers instance) {
        INSTANCE = instance;
    }

    public static ChannelHandler wrap(ChannelHandler handler) {
        return getINSTANCE().wrapInternal(handler);
    }


    public ChannelHandler wrapInternal(ChannelHandler handler) {
        ChannelHandler dispatcher = new AllDispatcher().dispatch(handler);
        HeartbeatHandler heartbeatHandler = new HeartbeatHandler(dispatcher);
        MultiMessageHandler multiMessageHandler = new MultiMessageHandler(heartbeatHandler);
        return multiMessageHandler;
    }
}

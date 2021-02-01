package netty.server;

/**
 * @author geyu
 * @date 2021/1/30 12:12
 */
public class MultiMessageHandler extends AbstractChannelHandlerDelegate {

    public MultiMessageHandler(ChannelHandler handler) {
        super(handler);
    }

    @Override
    public void received(InnerChannel channel, Object message) throws RemotingException {
        if (message instanceof MultiMessage) {
            MultiMessage list = (MultiMessage) message;
            for (Object obj : list) {
                super.received(channel, obj);
            }
        } else {
            super.received(channel, message);
        }
    }
}

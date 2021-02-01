package netty.server;


import org.apache.dubbo.remoting.Decodeable;

/**
 * @author geyu
 * @date 2021/1/30 21:42
 */
public class DecodeHandler extends AbstractChannelHandlerDelegate {
    protected DecodeHandler(ChannelHandler handler) {
        super(handler);
    }

    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        // if(message instanceof Decodeable){}
        if (message instanceof Request) {
            decode(((Request) message).getData());
        }
        if (message instanceof Response) {
            decode(((Response) message).getResult());
        }
        super.received(channel, message);
    }

    private void decode(Object message) {
        // todo myRPC
    }
}

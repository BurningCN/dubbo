package netty.server;

/**
 * @author geyu
 * @date 2021/2/2 14:07
 */
public class ChannelHandlerAdapter implements ChannelHandler {


    @Override
    public void connected(InnerChannel channel) throws RemotingException {
        System.out.println(getClass().getSimpleName() + " connected ");
    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {
        System.out.println(getClass().getSimpleName() + " disconnected ");
    }

    @Override
    public void sent(InnerChannel channel, Object message) throws RemotingException {
        System.out.println(getClass().getSimpleName() + " sent ");
    }

    @Override
    public void received(InnerChannel channel, Object message) throws RemotingException {
        System.out.println(getClass().getSimpleName() + " received ");
    }

    @Override
    public void caught(InnerChannel channel, Throwable exception) throws RemotingException {
        System.out.println(getClass().getSimpleName() + " caught ");
    }
}

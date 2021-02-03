package my.server;

/**
 * @author geyu
 * @date 2021/2/2 14:07
 */
public class ChannelHandlerAdapter implements ChannelHandler {


    @Override
    public void connected(InnerChannel channel) throws RemotingException {
        System.out.println(DataTimeUtil.now() + getClass().getSimpleName() + " connected,localAddr=" + channel.getLocalAddress());
    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {
        System.out.println(DataTimeUtil.now() + getClass().getSimpleName() + " disconnected,localAddr=" + channel.getLocalAddress());
    }

    @Override
    public void sent(InnerChannel channel, Object message) throws RemotingException {
        long id = message instanceof Request ? ((Request) message).getId() : ((Response) message).getId();
        System.out.println(DataTimeUtil.now() + getClass().getSimpleName() + " sent,msg=" + message + ",id=" + id + ",localAddr=" + channel.getLocalAddress());
    }

    @Override
    public void received(InnerChannel channel, Object message) throws RemotingException {
        long id = message instanceof Request ? ((Request) message).getId() : ((Response) message).getId();
        System.out.println(DataTimeUtil.now() + getClass().getSimpleName() + " received,msg=" + message + ",id=" + id + ",localAddr=" + channel.getLocalAddress());
    }

    @Override
    public void caught(InnerChannel channel, Throwable exception) throws RemotingException {
        System.out.println(DataTimeUtil.now() + getClass().getSimpleName() + " caught,localAddr=" + channel.getLocalAddress());
    }
}

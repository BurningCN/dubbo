package netty.server;


/**
 * @author gy821075
 * @date 2021/1/29 21:14
 */
public interface ChannelHandler {
    void connected(Channel channel) throws RemotingException;

    void disconnected(Channel channel) throws RemotingException;

    void sent(Channel channel, Object message) throws RemotingException;

    void received(Channel channel, Object message) throws RemotingException;

    void caught(Channel channel, Throwable exception) throws RemotingException;
}

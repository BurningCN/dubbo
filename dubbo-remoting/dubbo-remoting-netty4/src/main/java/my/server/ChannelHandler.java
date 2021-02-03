package my.server;


/**
 * @author gy821075
 * @date 2021/1/29 21:14
 */
public interface ChannelHandler {
    void connected(InnerChannel channel) throws RemotingException;

    void disconnected(InnerChannel channel) throws RemotingException;

    void sent(InnerChannel channel, Object message) throws RemotingException;

    void received(InnerChannel channel, Object message) throws RemotingException;

    void caught(InnerChannel channel, Throwable exception) throws RemotingException;
}

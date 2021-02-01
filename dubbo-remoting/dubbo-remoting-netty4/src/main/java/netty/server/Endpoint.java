package netty.server;


import java.net.InetSocketAddress;

/**
 * @author gy821075
 * @date 2021/1/31 14:32
 */
public interface Endpoint {
    URL getUrl();

    ChannelHandler getChannelHandler();

    InetSocketAddress getLocalAddress();

    void send(Object message) throws RemotingException;

    void send(Object message, boolean sent) throws RemotingException;

    void close();

    void close(int timeout);

    void startClose();

    boolean isClosed();
}

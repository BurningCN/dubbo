package netty.server;


import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author geyu
 * @date 2021/1/28 18:17
 */
public abstract class AbstractClient implements Client {

    private final URL url;
    private final int sendTimeout;
    private final boolean sent;
    private Codec2 codec;
    private int connectionTimeout;
    private int idleTimeout;
    private InetSocketAddress serverAddress;
    private ReentrantLock connectLock = new ReentrantLock();
    private ChannelHandler channelHandler;

    public AbstractClient(URL url, ChannelHandler channelHandler) throws RemotingException {
        this.url = url;
        this.codec = getChannelCodec(url);
        this.serverAddress = new InetSocketAddress(url.getHost(), url.getPort());
        this.channelHandler = channelHandler;
        this.connectionTimeout = url.getPositiveParameter(Constants.CONNECT_TIMEOUT, Constants.DEFAULT_CONNECT_TIMEOUT);
        this.idleTimeout = url.getPositiveParameter(Constants.HEARTBEAT, Constants.DEFAULT_HEARTBEAT);
        this.sendTimeout = url.getPositiveParameter(Constants.SEND_TIMEOUT, Constants.DEFAULT_SEND_TIMEOUT);
        this.sent = url.getParameter(Constants.SENT, false);
        doOpen();
        connect();
    }

    public ChannelHandler getChannelHandler() {
        return channelHandler;
    }

    protected Codec2 getChannelCodec(URL url) {
        return new ExchangeCodec();
        // todo myRPC 需要支持spi
    }

    @Override
    public void connect() throws RemotingException {
        connectLock.lock();
        try {
            if (isConnected()) {
                return;
            }
            doConnect();
            if (!isConnected()) {
                throw new RemotingException();
            } else {
                // System.out.println("客户端连接成功，服务端地址为：" + getServerAddress());
            }
        } catch (RemotingException e) {
            throw e;
        } finally {
            connectLock.unlock();
        }
    }

    public boolean isConnected() {
        return getChannel() != null && getChannel().isConnected();
    }

    protected abstract void doConnect() throws RemotingException;

    protected abstract void doOpen();


    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    public Codec2 getCodec() {
        return codec;
    }

    public int getConnectTimeout() {
        return connectionTimeout;
    }

    public int getIdleTimeout() {
        System.out.println("客户端读空闲：" + idleTimeout);
        return idleTimeout;
    }

    public URL getUrl() {
        return url;
    }

    public int getSendTimeout() {
        return sendTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public boolean getSent() {
        return sent;
    }
}

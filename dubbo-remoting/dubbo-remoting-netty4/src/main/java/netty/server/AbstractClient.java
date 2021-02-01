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
        this.idleTimeout = UrlUtils.getHeartbeat(url);
        this.sendTimeout = url.getPositiveParameter(Constants.SEND_TIMEOUT, Constants.DEFAULT_SEND_TIMEOUT);
        this.sent = url.getParameter(Constants.SENT, false);
        doOpen();
        connect();
        doStartTimer();
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
        if (!isConnected()) {
            try {
                connectLock.lock();
                if (!isConnected()) {
                    doConnect();
                    if (!isConnected()) {
                        throw new RemotingException();
                    } else {
                        // System.out.println("客户端连接成功，服务端地址为：" + getServerAddress());
                    }
                }
            } catch (RemotingException e) {
                throw e;
            } finally {
                connectLock.unlock();
            }
        }
    }

    @Override
    public void disconnect() throws RemotingException {
        if (isConnected()) {
            try {
                connectLock.lock();
                if (isConnected()) {
                    InnerChannel channel = getChannel();
                    if (channel != null) {
                        channel.close();
                    }
                    // doDisconnect(); 不需要
                }
            } finally {
                connectLock.unlock();
            }
        }
    }

    @Override
    public void reconnect() throws RemotingException {
        if (!isConnected()) {
            try {
                connectLock.lock();
                if (!isConnected()) {
                    disconnect();
                    connect();
                }
            } finally {
                connectLock.unlock();
            }
        }
    }

    @Override
    public void close() throws RemotingException {
        if (isConnected()) {
            try {
                connectLock.lock();
                if (isConnected()) {
                    disconnect();
                    doClose();
                }
            } finally {
                connectLock.unlock();
            }
        }
    }

    public boolean isConnected() {
        return getChannel() != null && getChannel().isConnected();
    }

    protected abstract void doConnect() throws RemotingException;

    protected abstract void doOpen();

    protected abstract void doClose();

    protected abstract void doStartTimer();

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

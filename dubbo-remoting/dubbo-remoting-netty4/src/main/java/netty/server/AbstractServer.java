package netty.server;

import java.net.InetSocketAddress;

/**
 * @author geyu
 * @date 2021/1/28 13:52
 */
public abstract class AbstractServer implements Server {

    private ChannelHandler handler;
    private InetSocketAddress bindAddress;

    private int idleTimeout;

    private Codec2 codec;

    private URL url;

    public AbstractServer(URL url, ChannelHandler handler) {// 待 需要传入一个对象，根据对象取值
        this.url = url;
        this.bindAddress = new InetSocketAddress(url.getHost(), url.getPort());
        this.idleTimeout = UrlUtils.getIdleTimeout(url);
        this.codec = getChannelCodec(url);
        this.handler = handler;
        doOpen();
    }

    protected Codec2 getChannelCodec(URL url) {
        return new ExchangeCodec();
        // todo myRPC 需要支持spi
    }

    public ChannelHandler getChannelHandler() {
        return handler;
    }

    protected abstract void doOpen();

    public Codec2 getCodec() {
        return codec;
    }

    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public int getIdleTimeout() {
        System.out.println("服务端读写空闲：" + idleTimeout);
        return idleTimeout;
    }

    public URL getUrl() {
        return url;
    }
}

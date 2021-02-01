package netty.server;


import java.net.InetSocketAddress;

/**
 * @author geyu
 * @date 2021/1/28 18:37
 */
public class RemotingException extends Throwable {

    private InetSocketAddress localAddress;
    private InetSocketAddress remoteAddress;

    public RemotingException() {

    }

    // ==== =====  不带cause

    public RemotingException(InnerChannel channel, String msg) {
        this(channel == null ? null : channel.getLocalAddress(), channel == null ? null : channel.getRemoteAddress(),
                msg);
    }

    public RemotingException(InetSocketAddress localAddress, InetSocketAddress remoteAddress, String message) {
        super(message);

        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
    }

    // ==== =====  带cause
    public RemotingException(InnerChannel channel, String message, Throwable cause) {
        this(channel == null ? null : channel.getLocalAddress(), channel == null ? null : channel.getRemoteAddress(),
                message, cause);
    }

    public RemotingException(InetSocketAddress localAddress, InetSocketAddress remoteAddress, String message,
                             Throwable cause) {
        super(message, cause);
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;

    }
}

package netty.server;

import org.apache.dubbo.remoting.exchange.support.header.HeartbeatHandler;

/**
 * @author geyu
 * @date 2021/2/1 17:29
 */
public class ReconnectTimerTask extends AbstractTimerTask {

    private final long idleTimeout;
    private final Client client;

    public ReconnectTimerTask(ChannelProvider channelProvider, long interval, long idleTimeout, Client client) {
        super(channelProvider, interval,"ReconnectTimerTask");
        this.idleTimeout = idleTimeout;
        this.client = client;
    }

    @Override
    protected void doTask(InnerChannel channel) throws RemotingException {
        if (!channel.isConnected()) {
            client.reconnect();
        } else {
            Long lastRead = (Long) channel.getAttribute(HeartbeatHandler.KEY_READ_TIMESTAMP);
            Long now = System.currentTimeMillis();
            if (lastRead != null && now - lastRead > idleTimeout) {
                client.reconnect();
            }
        }
    }
}

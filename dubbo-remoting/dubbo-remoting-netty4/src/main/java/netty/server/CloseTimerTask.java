package netty.server;

import org.apache.dubbo.remoting.exchange.support.header.HeartbeatHandler;

/**
 * @author geyu
 * @date 2021/2/1 21:01
 */
public class CloseTimerTask extends AbstractTimerTask {
    private final int idleTimeout;

    public CloseTimerTask(ChannelProvider channelProvider, long interval, int idleTimeout) {
        super(channelProvider, interval,"CloseTimerTask");
        this.idleTimeout = idleTimeout;
    }

    @Override
    protected void doTask(InnerChannel channel) throws RemotingException {
        Long lastRead = (Long) channel.getAttribute(org.apache.dubbo.remoting.exchange.support.header.HeartbeatHandler.KEY_READ_TIMESTAMP);
        Long lastWrite = (Long) channel.getAttribute(HeartbeatHandler.KEY_WRITE_TIMESTAMP);
        long now = System.currentTimeMillis();
        if ((lastRead != null && now - lastRead > idleTimeout)
                || lastWrite != null && now - lastWrite > idleTimeout) {
            System.out.println("CloseTimerTask:::Close channel " + channel + ", because idleCheck timeout: "
                    + idleTimeout + "ms");
            channel.close();
        }
    }
}

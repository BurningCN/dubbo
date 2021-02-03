package my.server;

import org.apache.dubbo.remoting.exchange.support.header.HeartbeatHandler;

/**
 * @author geyu
 * @date 2021/2/1 19:49
 */
public class HeartBeatTimerTask extends AbstractTimerTask {
    private final int heartbeatTimeout;

    public HeartBeatTimerTask(ChannelProvider channelProvider, long interval, int heartbeatTimeout) {
        super(channelProvider, interval,"HeartBeatTimerTask");
        this.heartbeatTimeout = heartbeatTimeout;
    }

    @Override
    protected void doTask(InnerChannel channel) throws RemotingException {
        Long lastRead = (Long) channel.getAttribute(HeartbeatHandler.KEY_READ_TIMESTAMP);
        Long lastWrite = (Long) channel.getAttribute(HeartbeatHandler.KEY_WRITE_TIMESTAMP);
        long now = System.currentTimeMillis();
        if ((lastRead != null && now - lastRead > heartbeatTimeout)
                || lastWrite != null && now - lastWrite > heartbeatTimeout) {
            channel.send(Request.makeHeartbeat());
            System.out.println("HeartBeatTimerTask:::Send heartbeat to remote channel " + channel.getRemoteAddress()
                    + ", cause: The channel has no data-transmission exceeds a heartbeat period: "
                    + heartbeatTimeout + "ms");
        }

    }
}

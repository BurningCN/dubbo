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
        super(channelProvider, interval, "ReconnectTimerTask");
        this.idleTimeout = idleTimeout;
        this.client = client;
    }

    @Override
    protected void doTask(InnerChannel channel) throws RemotingException {
        if (!client.isConnected()) { // 注意!!!这里不能使用 !channel.isConnected() ，因为client的channel可能为null（比如没有服务端，就发起了连接），我们用client的状态去判断是否连接成功
            client.reconnect();
            System.out.println("ReconnectTimerTask:::Reconnect");
        } else {
            Long lastRead = (Long) channel.getAttribute(HeartbeatHandler.KEY_READ_TIMESTAMP);
            Long now = System.currentTimeMillis();
            if (lastRead != null && now - lastRead > idleTimeout) {
                client.reconnect();
                System.out.println("ReconnectTimerTask:::Reconnect");// 我们只在Reconnect动作发生后打印日志
            }
        }
    }
}

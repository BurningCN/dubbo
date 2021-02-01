package netty.server;


/**
 * @author geyu
 * @date 2021/1/30 12:12
 */
public class HeartbeatHandler extends AbstractChannelHandlerDelegate {

    public static final String KEY_READ_TIMESTAMP = "READ_TIMESTAMP";

    public static final String KEY_WRITE_TIMESTAMP = "WRITE_TIMESTAMP";

    public HeartbeatHandler(ChannelHandler handler) {
        super(handler);
    }

    @Override
    public void connected(Channel channel) throws RemotingException {
        setReadTimestamp(channel);
        setWriteTimestamp(channel);
        super.connected(channel);
    }

    @Override
    public void disconnected(Channel channel) throws RemotingException {
        setReadTimestamp(channel);
        setWriteTimestamp(channel);
        super.disconnected(channel);
    }

    @Override
    public void sent(Channel channel, Object message) throws RemotingException {
        setWriteTimestamp(channel);
        super.sent(channel, message);
    }

    @Override
    public void received(Channel channel, Object msg) throws RemotingException {
        setReadTimestamp(channel);
        if (isHeartbeatRequest(msg)) {
            Request request = (Request) msg;
            if (request.isTwoWay()) {
                Response response = new Response(request.getId());
                response.setEvent(CommonConstants.HEARTBEAT_EVENT);
                channel.send(msg);
                System.out.println("Received request heartbeat from remote channel " + channel.getRemoteAddress());
            }
            return;
        }
        if (isHeartbeatResponse(msg)) {
            System.out.println("Receive response heartbeat response from remote channel " + channel.getRemoteAddress());
            return;
        }
        super.received(channel, msg);
    }

    private void setReadTimestamp(Channel channel) {
        channel.setAttribute(KEY_READ_TIMESTAMP, System.currentTimeMillis());
    }

    private void setWriteTimestamp(Channel channel) {
        channel.setAttribute(KEY_WRITE_TIMESTAMP, System.currentTimeMillis());
    }

    private boolean isHeartbeatResponse(Object msg) {
        return msg instanceof Response && ((Response) msg).isHeartbeat();
    }

    private boolean isHeartbeatRequest(Object msg) {
        return msg instanceof Request && ((Request) msg).isHeartbeat();
    }
}

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
    public void connected(InnerChannel channel) throws RemotingException {
        setReadTimestamp(channel);
        setWriteTimestamp(channel);
        super.connected(channel);
    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {
        clearReadTimestamp(channel);
        clearWriteTimestamp(channel);
        super.disconnected(channel);
    }

    @Override
    public void sent(InnerChannel channel, Object message) throws RemotingException {
        setWriteTimestamp(channel);
        super.sent(channel, message);
    }

    @Override
    public void received(InnerChannel channel, Object msg) throws RemotingException {
        setReadTimestamp(channel);
        if (isHeartbeatRequest(msg)) {
            Request request = (Request) msg;
            if (request.isTwoWay()) {
                Response response = new Response(request.getId(), request.getVersion());
                response.setEvent(CommonConstants.HEARTBEAT_EVENT);
                channel.send(response);
                System.out.println(DataTimeUtil.now() + "Received request heartbeat from remote channel " + channel.getRemoteAddress());
            }
            return;
        }
        if (isHeartbeatResponse(msg)) {
            System.out.println(DataTimeUtil.now() + "Receive response heartbeat response from remote channel " + channel.getRemoteAddress());
            return;
        }
        super.received(channel, msg);
    }

    private void setReadTimestamp(InnerChannel channel) {
        channel.setAttribute(KEY_READ_TIMESTAMP, System.currentTimeMillis());
    }

    private void setWriteTimestamp(InnerChannel channel) {
        channel.setAttribute(KEY_WRITE_TIMESTAMP, System.currentTimeMillis());
    }

    private void clearReadTimestamp(InnerChannel channel) {
        channel.removeAttribute(KEY_READ_TIMESTAMP);
    }

    private void clearWriteTimestamp(InnerChannel channel) {
        channel.removeAttribute(KEY_WRITE_TIMESTAMP);
    }

    private boolean isHeartbeatResponse(Object msg) {
        return msg instanceof Response && ((Response) msg).isHeartbeat();
    }

    private boolean isHeartbeatRequest(Object msg) {
        return msg instanceof Request && ((Request) msg).isHeartbeat();
    }
}

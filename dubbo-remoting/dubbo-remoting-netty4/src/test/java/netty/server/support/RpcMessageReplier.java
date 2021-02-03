package netty.server.support;

import netty.server.InnerChannel;
import netty.server.Replier;

/**
 * @author geyu
 * @date 2021/2/2 20:10
 */
public class RpcMessageReplier implements Replier<RpcMessage> {
    @Override
    public Object reply(InnerChannel channel, RpcMessage request) {
        return null;
    }
}

package netty.server;

/**
 * @author geyu
 * @date 2021/1/28 18:14
 */
public class NettyTransporter implements Transporter {

    @Override
    public RemotingServer bind(URL url, ChannelHandler handler) throws RemotingException{
        return new NettyServer(url,handler);
    }

    @Override
    public Client connect(URL url, ChannelHandler handler) throws RemotingException {
        return new NettyClient(url,handler);
    }
}

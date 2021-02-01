package netty.server;


/**
 * @author gy821075
 * @date 2021/1/28 18:08
 */
public interface Transporter {

    Server bind(URL url, ChannelHandler channelHandler) throws RemotingException;

    Client connect(URL url,ChannelHandler channelHandler) throws RemotingException;
}

package netty.server;


/**
 * @author gy821075
 * @date 2021/1/28 18:08
 */
public interface Transporter {

    Server bind(URL url, ExchangeHandler channelHandler) throws RemotingException;

    Client connect(URL url,ExchangeHandler channelHandler) throws RemotingException;
}

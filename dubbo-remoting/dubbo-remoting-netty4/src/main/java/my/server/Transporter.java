package my.server;


/**
 * @author gy821075
 * @date 2021/1/28 18:08
 */
public interface Transporter {

    @Important
    Server bind(URL url, ExchangeHandler channelHandler) throws RemotingException;

    Server bind(URL url) throws RemotingException;

    Server bind(URL url, Replier<?> replier) throws RemotingException;

    @Important
    Client connect(URL url, ExchangeHandler channelHandler) throws RemotingException;

    Client connect(URL url) throws RemotingException;

    Client connect(URL url, Replier<?> replier) throws RemotingException;

}

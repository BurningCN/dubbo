package netty.server;


/**
 * @author geyu
 * @date 2021/1/28 18:14
 */
public class NettyTransporter implements Transporter {

    @Override
    public Server bind(URL url, ExchangeHandler handler) throws RemotingException {
        return new NettyServer(url, new DecodeHandler(new HeaderExchangeHandler(handler)));
    }

    @Override
    public Client connect(URL url, ExchangeHandler handler) throws RemotingException {
        return new NettyClient(url, new DecodeHandler(new HeaderExchangeHandler(handler)));
    }
}

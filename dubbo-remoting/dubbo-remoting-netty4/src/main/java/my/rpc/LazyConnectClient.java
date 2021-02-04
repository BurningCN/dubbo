package my.rpc;

import my.server.*;

/**
 * @author geyu
 * @date 2021/2/4 20:35
 */
public class LazyConnectClient implements Client {


    public LazyConnectClient(URL url, ExchangeHandler requestHandler) {
    }

    @Override
    public void connect() throws RemotingException {
        
    }

    @Override
    public void disconnect() throws RemotingException {

    }

    @Override
    public void reconnect() throws RemotingException {

    }

    @Override
    public void close() throws RemotingException {

    }

    @Override
    public InnerChannel getChannel() {
        return null;
    }

    @Override
    public boolean isConnected() {
        return false;
    }
}

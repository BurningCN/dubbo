package my.rpc;

import my.server.ExchangeHandler;
import my.server.InnerChannel;
import my.server.RemotingException;

import java.util.concurrent.CompletableFuture;

/**
 * @author geyu
 * @date 2021/2/4 13:42
 */
public class DefaultExchangeHandler implements ExchangeHandler {
    @Override
    public CompletableFuture<Object> reply(InnerChannel channel, Object request) {
        return null;
    }

    @Override
    public void connected(InnerChannel channel) throws RemotingException {

    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {

    }

    @Override
    public void sent(InnerChannel channel, Object message) throws RemotingException {

    }

    @Override
    public void received(InnerChannel channel, Object message) throws RemotingException {

    }

    @Override
    public void caught(InnerChannel channel, Throwable exception) throws RemotingException {

    }
}

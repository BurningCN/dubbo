package netty.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author geyu
 * @date 2021/1/30 20:53
 */
public class MockChannelHandler implements ExchangeHandler {

    private static AtomicLong index = new AtomicLong(0);

    @Override
    public CompletableFuture<Object> reply(ExchangeChannel channel, Object request) {
        // request的处理...
        CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        completableFuture.complete("server hello " + index.incrementAndGet());
        return completableFuture;
    }

    @Override
    public void connected(Channel channel) throws RemotingException {

    }

    @Override
    public void disconnected(Channel channel) throws RemotingException {

    }

    @Override
    public void sent(Channel channel, Object message) throws RemotingException {

    }

    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        if (message instanceof Request) {
            Request request = (Request) message;
            System.out.println(request.getData());
        }

    }

    @Override
    public void caught(Channel channel, Throwable exception) throws RemotingException {

    }
}

package my.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author geyu
 * @date 2021/1/30 20:53
 */
public class MockChannelHandler implements ExchangeHandler {

    private static AtomicLong index = new AtomicLong(0);

    @Override
    public CompletableFuture<Object> reply(InnerChannel channel, Object requestData) {
        if(requestData instanceof String){
            String str = (String)requestData;
            System.out.println(str);
        }
        // request的处理...
        CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        completableFuture.complete("server hello " + index.incrementAndGet());
        return completableFuture;
    }

    @Override
    public void connected(InnerChannel channel) throws RemotingException {
        System.out.println(channel.getLocalAddress() + " MockChannelHandler connected");
    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {
        System.out.println(channel.getLocalAddress() + " MockChannelHandler disconnected");
    }

    @Override
    public void sent(InnerChannel channel, Object message) throws RemotingException {
        System.out.println(channel.getLocalAddress() + " MockChannelHandler sent");
    }

    @Override
    public void received(InnerChannel channel, Object message) throws RemotingException {
        if (message instanceof Request) {
            Request request = (Request) message;
            System.out.println(request.getData());
        }
        System.out.println(channel.getLocalAddress() + " MockChannelHandler received");

    }

    @Override
    public void caught(InnerChannel channel, Throwable exception) throws RemotingException {
        System.out.println(channel.getLocalAddress() + " MockChannelHandler caught");
    }
}

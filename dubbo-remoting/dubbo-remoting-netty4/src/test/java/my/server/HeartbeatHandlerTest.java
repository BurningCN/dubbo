package my.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * @author geyu
 * @date 2021/2/2 16:43
 */
public class HeartbeatHandlerTest {

    @Test
    public void testHeartbeat() throws RemotingException, InterruptedException {
        URL url = URL.valueOf("dubbo://127.0.0.1:8889/testHeartbeat?heartbeat=5000");
        url.addParameter("reconnect", false);
        NettyTransporter transporter = new NettyTransporter();
        CountDownLatch connect = new CountDownLatch(1);
        CountDownLatch disconnect = new CountDownLatch(1);
        TestHandler testHandler = new TestHandler(connect, disconnect);

        Server bind = transporter.bind(url, testHandler);
        FakeChannelHandlers.setFake(); // 注意这里 ，然后发现输出只有服务端接收到客户端的心跳包，服务端的确也回包了，不过客户端没有HeartbeatHandler来处理响应的心跳包，所以没有输出Receive response heartbeat response from remote channel /127.0.0.1:8889
        // 将上面的调整到bind前面会有不同的效果，这下连服务端都没有HeartbeatHandler处理心跳了，回包都没有了，但是客户端一直会因为空闲超时而发心跳，所以最后还是不会断开的
        Client client = transporter.connect(url);
        disconnect.await();
    }

}

class TestHandler implements ExchangeHandler {


    private final CountDownLatch disconnect;
    private final CountDownLatch connect;

    public TestHandler(CountDownLatch connect, CountDownLatch disconnect) {
        this.connect = connect;
        this.disconnect = disconnect;
    }

    @Override
    public CompletableFuture<Object> reply(InnerChannel channel, Object request) {
        return null;
    }

    @Override
    public void connected(InnerChannel channel) throws RemotingException {
        connect.countDown();
        System.out.println(getClass().getSimpleName() + " connected ");
    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {
        disconnect.countDown();
        System.out.println(getClass().getSimpleName() + " disconnected ");

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
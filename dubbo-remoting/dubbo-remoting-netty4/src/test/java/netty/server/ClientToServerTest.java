package netty.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author geyu
 * @date 2021/2/2 12:11
 */
public class ClientToServerTest {

    protected Transporter transporter;

    private static Object reply(InnerChannel a, Object b) {
        return "server know :" + b;
    }

    @BeforeEach
    protected void setUp() throws RemotingException {
        transporter = new NettyTransporter();
    }


    @Test
    public void testReplyWithMockChannelHandler() throws RemotingException, ExecutionException, InterruptedException {
        URL url = URL.valueOf("dubbo://127.0.0.1:" + 99998 + "/test.reconnect"); // check=false在connect失败抛异常的时候ignore
        Server server = transporter.bind(url, new MockChannelHandler());
        Client client = transporter.connect(url, new MockChannelHandler());
        CompletableFuture<Object> request = client.getChannel().request("哈哈", 100000);
        System.out.println((String) request.get());
        client.close();
        server.close();
    }

    @Test
    public void testReplyWithReplier() throws RemotingException, ExecutionException, InterruptedException {
        URL url = URL.valueOf("dubbo://127.0.0.1:" + 9998 + "/test.reconnect");
        Server server = transporter.bind(url, new WorldReplier());
        Client client = transporter.connect(url);
        // 报错！！！核心就是我们在服务端反序列化调用的是JSON.parse()，返回的类型是JSONObject，导致ReplierDispatcher找不到该class类型的replier
        // 而string类型的数据不会有问题！！这个需要等到自己实现DubboCodec和DecodeableRpcResult
        CompletableFuture<Object> request = client.getChannel().request(new World("xxx"), 100000);// 注意这里传输是自定义的引用类型，不是String
        System.out.println((World) request.get());
        client.close();
        server.close();
        Thread.sleep(4000);
    }

    @Test
    public void testReplyWithReplier2() throws RemotingException, ExecutionException, InterruptedException {
        URL url = URL.valueOf("dubbo://127.0.0.1:" + 9998 + "/test.reconnect");
        Server server = transporter.bind(url, ClientToServerTest::reply);
        Client client = transporter.connect(url);
        CompletableFuture<Object> request = client.getChannel().request("client say hello", 100000);
        System.out.println(request.get());
        client.close();
        server.close();
    }

}

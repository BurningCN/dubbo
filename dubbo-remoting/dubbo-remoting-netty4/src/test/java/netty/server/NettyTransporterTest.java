package netty.server;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author geyu
 * @date 2021/1/29 15:20
 */
public class NettyTransporterTest {

    @Test
    public void testConnect() throws RemotingException, InterruptedException {
        NettyTransporter transporter = new NettyTransporter();
        URL url = URL.valueOf("dubbo://localhost:9991/");
        transporter.bind(url, getMockHandler());
        transporter.connect(url, getMockHandler());
        Thread.sleep(15 * 1000);
    }

    @Test
    public void testHeartbeat() throws RemotingException, InterruptedException {
        NettyTransporter transporter = new NettyTransporter();
        URL url = URL.valueOf("dubbo://localhost:9991/test?heartbeat=5000");
        transporter.bind(url, getMockHandler());
        transporter.connect(url, getMockHandler());
        Thread.sleep(15 * 1000);
    }

    @Test
    public void testClientNotSendHeartbeat() throws RemotingException, InterruptedException {
        NettyTransporter transporter = new NettyTransporter();
        URL url = URL.valueOf("dubbo://localhost:9991/test?heartbeat=5000&dont.send=true");
        transporter.bind(url, getMockHandler());
        transporter.connect(url, getMockHandler());
        Thread.sleep(15 * 1000);
    }

    @Test
    public void testRequestAndReply() throws RemotingException, InterruptedException, ExecutionException {
        NettyTransporter transporter = new NettyTransporter();
        URL url = URL.valueOf("dubbo://localhost:9991/test");
        transporter.bind(url, getMockHandler());
        Client client = transporter.connect(url, getMockHandler());

        LinkedBlockingQueue<CompletableFuture<Object>> futureList = new LinkedBlockingQueue<>();
        AtomicInteger success = new AtomicInteger(0);
        Thread testThread = startThread(futureList, success);

        for (int i = 0; i < 100; i++) {
            futureList.put(client.getChannel().request("client hello " + i));
            Thread.sleep(500);
        }
        while (success.get() != 100) {
            System.out.println(success.get());
            Thread.sleep(10);
        }
        testThread.interrupt();
    }

    private Thread startThread(LinkedBlockingQueue<CompletableFuture<Object>> futureList, AtomicInteger success) {
        Thread thread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    CompletableFuture<Object> f = futureList.take();
                    System.out.println((String) f.get());
                    success.incrementAndGet();
                }
            } catch (Exception e) {
                // e.printStackTrace();
            }
        }, "testThread");
        thread.start();
        return thread;
    }


    public ChannelHandler getMockHandler() {
        return new DecodeHandler(new HeaderExchangeHandler(new MockChannelHandler()));
    }


}

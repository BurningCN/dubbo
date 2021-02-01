package netty.server;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author geyu
 * @date 2021/1/29 15:20
 */
public class NettyTransporterTest {
    @Test
    public void init() throws RemotingException {
        NettyTransporter transporter = new NettyTransporter();
        URL url = URL.valueOf("dubbo://localhost:9991/");
        transporter.bind(url, null);
        transporter.connect(url, null);
    }

    @Test
    public void testHeartbeat() throws RemotingException, InterruptedException {
        NettyTransporter transporter = new NettyTransporter();
        URL url = URL.valueOf("dubbo://localhost:9991/test?heartbeat=5000");
        transporter.bind(url, null);
        transporter.connect(url, null);
        Thread.sleep(100 * 10000);
    }

    @Test
    public void testRequest() throws RemotingException, InterruptedException, ExecutionException {
        NettyTransporter transporter = new NettyTransporter();
        URL url = URL.valueOf("dubbo://localhost:9991/test");
        MockChannelHandler handler = new MockChannelHandler();
        transporter.bind(url, new DecodeHandler(new HeaderExchangeHandler(handler)));
        Client client = transporter.connect(url, new DecodeHandler(new HeaderExchangeHandler(handler)));
        LinkedBlockingQueue<CompletableFuture<Object>> futureList = new LinkedBlockingQueue<>();
        AtomicInteger success = new AtomicInteger(0);
        Thread testThread = startThread(futureList, success);
        for (int i = 0; i < 100; i++) {
            //futureList.put(client.request("client hello " + i, 100000, null));
            Thread.sleep(50);
        }
        while (success.get() != 100) {
            System.out.println(success.get());
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


}

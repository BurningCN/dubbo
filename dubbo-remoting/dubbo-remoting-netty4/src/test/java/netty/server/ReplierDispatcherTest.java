package netty.server;

import netty.server.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.ExecutionException;


/**
 * @author geyu
 * @date 2021/2/2 20:01
 */
public class ReplierDispatcherTest {

    private Server server;
    private Client client;
    private Transporter transporter;
    private URL url;
    private Map<String, Client> clients = new ConcurrentHashMap<>();

    @BeforeEach
    public void startServer() throws RemotingException {
        transporter = new NettyTransporter();
        url = URL.valueOf("dubbo://127.0.0.1:8888/replier?heartbeat=5000&reconnet=true&check=false&serialization=hessian2");
        ReplierDispatcher replierDispatcher = new ReplierDispatcher();
        replierDispatcher.addReplier(RpcMessage.class, new RpcMessageReplier());
        replierDispatcher.addReplier(Data.class, (channel, msg) -> new StringMessage("hello world"));
        server = transporter.bind(url, replierDispatcher);
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void testDataPackage() throws RemotingException, ExecutionException, InterruptedException {
        client = transporter.connect(url);
        Random random = new Random();
        int count = 0;
        for (int i = 5; i < 100; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i * 100; j++)
                sb.append("(" + random.nextLong() + ")");
            Data data = new Data(sb.toString());
            CompletableFuture<Object> request = client.getChannel().request(data);
            // Assertions.assertEquals(request.get().toString(), "hello world");
            System.out.println(DataTimeUtil.now() + request.get().toString() + "," + ++count);
        }
    }

    @Test
    public void testMultiClientSequential() throws RemotingException, InterruptedException {
        List<Client> clientList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            clientList.add(transporter.connect(url));
        }
        System.out.println(clientList.size());
    }

    @Test
    public void testMultiThreadRPCRequest() throws RemotingException, InterruptedException {
        int threadNum = 10;
        CountDownLatch countDownLatch = new CountDownLatch(threadNum);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threadNum, threadNum,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), new DefaultThreadFactory("MultiClientConcurrent"));
        for (int i = 0; i < threadNum; i++) {
            executor.submit(() -> {
                try {
                    Client client = transporter.connect(url);
                    clients.put(Thread.currentThread().getName(), client);
                    CompletableFuture<Object> future = client.getChannel().request(new RpcMessage(DemoService.class.getName(), "minus" +
                            "", new Class<?>[]{int.class, int.class}, new Object[]{20, 2}));
                    Assertions.assertEquals(((MockResult) future.get()).getResult(), 18);
                    for (int j = 0; j < 100; j++) {
                        CompletableFuture<Object> future1 = client.getChannel().request(new RpcMessage(DemoService.class.getName(), "sayHello" +
                                "", new Class<?>[]{String.class}, new Object[]{"mrh" + j}));
                        System.out.println(((MockResult) future1.get()).getResult());
                    }
                    countDownLatch.countDown();
                } catch (RemotingException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        countDownLatch.await(1000 * 10, TimeUnit.MILLISECONDS);
        System.out.println(DataTimeUtil.now() + clients.keySet());
    }
}

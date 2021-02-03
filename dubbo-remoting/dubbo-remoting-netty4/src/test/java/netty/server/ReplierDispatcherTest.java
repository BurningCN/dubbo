package netty.server;

import netty.server.support.Data;
import netty.server.support.RpcMessage;
import netty.server.support.RpcMessageReplier;
import netty.server.support.StringMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author geyu
 * @date 2021/2/2 20:01
 */
public class ReplierDispatcherTest {

    private Server server;
    private Client client;

    @BeforeEach
    public void startServer() throws RemotingException {
        NettyTransporter transporter = new NettyTransporter();
        URL url = URL.valueOf("dubbo://127.0.0.1:8888/replier?heartbeat=5000&reconnet=true&check=false&serialization=hessian2");
        ReplierDispatcher replierDispatcher = new ReplierDispatcher();
        replierDispatcher.addReplier(RpcMessage.class, new RpcMessageReplier());
        replierDispatcher.addReplier(Data.class, (channel, msg) -> new StringMessage("hello world"));
        server = transporter.bind(url, replierDispatcher);
        client = transporter.connect(url);
    }

    @AfterEach
    public void tearUP() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void testDataPackage() throws RemotingException, ExecutionException, InterruptedException {
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


}

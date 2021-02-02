package netty.server;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author geyu
 * @date 2021/2/2 11:12
 */
public class ClientReconnectTest {
    Transporter transporter;

    {
        transporter = new NettyTransporter();
    }

    @Test
    public void reconnect() throws InterruptedException, RemotingException {
        {
            int availablePort = NetUtils.getAvailablePort();
            Client client = newClient(availablePort, 1000);
            assertFalse(client.isConnected());
            Server server = newServer(availablePort);
            int i;
            for (i = 0; i < 100 && !client.isConnected(); i++) {
                Thread.sleep(100);
            }
            System.out.println(i);
            Assertions.assertTrue(client.isConnected());
            client.close();
            server.close();
        }
        {
            int availablePort = NetUtils.getAvailablePort();
            Client client = newClient(availablePort, 200);
            assertFalse(client.isConnected());
            Server server = newServer(availablePort);
            for (int i = 0; i < 5; i++) {
                Thread.sleep(200);
            }
            assertTrue(client.isConnected());
            client.close();
            server.close();
        }

    }

    Client newClient(int port, int heartbeat) throws RemotingException {
        URL url = URL.valueOf("dubbo://127.0.0.1:" + port + "/test.reconnect?check=false&heartbeat=" + heartbeat); // check=false在connect失败抛异常的时候ignore
        return transporter.connect(url, new MockChannelHandler());
    }

    Server newServer(int port) throws RemotingException {
        URL url = URL.valueOf("dubbo://127.0.0.1:" + port + "/test.reconnect");
        return transporter.bind(url, new MockChannelHandler());
    }
}

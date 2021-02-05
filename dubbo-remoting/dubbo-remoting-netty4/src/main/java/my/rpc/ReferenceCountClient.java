package my.rpc;

import my.server.Client;
import my.server.InnerChannel;
import my.server.RemotingException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author geyu
 * @date 2021/2/4 20:22
 */
public class ReferenceCountClient implements Client {
    private AtomicLong referenceCount = new AtomicLong(0);

    private Client client;

    public ReferenceCountClient(Client client) {
        this.client = client;
        referenceCount.incrementAndGet();
    }

    @Override
    public void connect() throws RemotingException {
        client.connect();
    }

    @Override
    public void disconnect() throws RemotingException {
        client.disconnect();
    }

    @Override
    public void reconnect() throws RemotingException {
        client.reconnect();
    }

    @Override
    public void close() throws RemotingException {
        if (referenceCount.decrementAndGet() <= 0) {
            client.close();
            replaceWithLazyClient();
        }

    }

    private void replaceWithLazyClient() {
        // todo myRPC
    }

    @Override
    public InnerChannel getChannel() {
        return client.getChannel();
    }

    @Override
    public boolean isConnected() {
        return client.isConnected();
    }

    public void incrementAndGetCount() {
        referenceCount.incrementAndGet();
    }
}

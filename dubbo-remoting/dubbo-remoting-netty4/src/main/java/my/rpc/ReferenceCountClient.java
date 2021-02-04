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

    }

    @Override
    public void disconnect() throws RemotingException {

    }

    @Override
    public void reconnect() throws RemotingException {

    }

    @Override
    public void close() throws RemotingException {

    }

    @Override
    public InnerChannel getChannel() {
        return null;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    public void incrementAndGetCount() {
        referenceCount.incrementAndGet();
    }
}

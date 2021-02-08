package my.rpc;

import my.server.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author geyu
 * @date 2021/2/4 20:22
 */
public class ReferenceCountClient implements Client {
    private URL url;
    private ExchangeHandler requestHandler;
    private AtomicLong referenceCount = new AtomicLong(0);
    private Client client;

    public ReferenceCountClient(Client client, ExchangeHandler requestHandler, URL url) {
        this.client = client;
        this.requestHandler = requestHandler;
        this.url = url;
        this.referenceCount.incrementAndGet();
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

    private void replaceWithLazyClient() { // 此时 ReferenceCountClient 变成LazyConnectClient，实际的客户端没有了，如果触发对该类ReferenceCountClient的请求时候，会跳转到LazyConnectClient
        URL lazyUrl = url.addParameter("connect.lazy.initial.state", Boolean.TRUE)
                .addParameter("reconnect", Boolean.FALSE)
                .addParameter("send.reconnect", Boolean.TRUE.toString())
                .addParameter(LazyConnectClient.REQUEST_WITH_WARNING_KEY, Boolean.TRUE);
        if (!(client instanceof LazyConnectClient) || !client.isConnected()) {
            client = new LazyConnectClient(lazyUrl, requestHandler);
        }
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

    // for test
    public Long getReferenceCount() {
        return referenceCount.get();
    }
}

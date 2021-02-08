package my.rpc;

import io.netty.channel.Channel;
import javassist.runtime.Inner;
import my.server.*;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static my.rpc.defaults.Constants.LAZY_CONNECT_INITIAL_STATE_KEY;

/**
 * @author geyu
 * @date 2021/2/4 20:35
 */
public class LazyConnectClient implements Client {

    protected static final String REQUEST_WITH_WARNING_KEY = "lazyclient_request_with_warning";
    private final URL url;
    private final boolean initialState;
    private final boolean requestWithWarning;
    private final ExchangeHandler requestHandler;
    private volatile Client client;
    private final ReentrantLock connectLock = new ReentrantLock();
    private AtomicLong warningcount = new AtomicLong(0);

    public LazyConnectClient(URL url, ExchangeHandler requestHandler) {
        this.url = url.addParameter("send.reconnect", Boolean.TRUE.toString());
        this.initialState = url.getParameter("connect.lazy.initial.state", true);
        this.requestWithWarning = url.getParameter(REQUEST_WITH_WARNING_KEY, false);
        this.requestHandler = requestHandler;

    }


    @Override
    public InnerChannel getChannel() {
        return new LazyInnerChannel();
    }

    class LazyInnerChannel extends AbstractDelegateChannel {

        private void initClient() throws RemotingException {
            if (client == null) {
                connectLock.lock();
                try {
                    if (client == null) {
                        System.out.println("Lazy connect to " + url);
                        client = new NettyTransporter().connect(url, requestHandler);
                        super.setChannel(client.getChannel());
                    }
                } finally {
                    connectLock.unlock();
                }
            }
        }

        @Override
        public CompletableFuture<Object> request(Object request) throws RemotingException {
            warning();
            initClient();
            return super.request(request);
        }


        @Override
        public CompletableFuture<Object> request(Object request, int timeout) throws RemotingException {
            warning();
            initClient();
            return super.request(request, timeout);
        }

        @Override
        public CompletableFuture<Object> request(Object request, ExecutorService executor) throws RemotingException {
            warning();
            initClient();
            return super.request(request, executor);
        }

        @Override
        public CompletableFuture<Object> request(Object request, int timeout, ExecutorService executor) throws RemotingException {
            warning();
            initClient();
            return super.request(request, timeout, executor);
        }


        @Override
        public void send(Object message) throws RemotingException {
            initClient();
            super.send(message);
        }

        @Override
        public void send(Object message, boolean sent) throws RemotingException {
            initClient();
            super.send(message, sent);
        }

        private void warning() {
            if (requestWithWarning) {
                if (warningcount.get() % 2 == 0) {
                    System.out.println("safe guard client , should not be called ,must have a bug.");
                }
                warningcount.incrementAndGet();
            }
        }
    }

    @Override
    public void connect() throws RemotingException {
        checkClient();
        client.reconnect();

    }

    private void checkClient() {
        if (client == null) {
            throw new IllegalStateException(
                    "LazyConnectExchangeClient state error. the client has not be init .url:" + url);
        }
    }

    @Override
    public void disconnect() throws RemotingException {
        checkClient();
        client.disconnect();
    }

    @Override
    public void reconnect() throws RemotingException {
        checkClient();
        client.reconnect();
    }

    @Override
    public void close() throws RemotingException {
        if (client != null) {
            client.close();
        }
    }


    @Override
    public boolean isConnected() {
        if (client == null) {
            return initialState;
        } else {
            return client.isConnected();
        }
    }
}

package my.server;

import org.apache.dubbo.common.utils.Assert;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author geyu
 * @date 2021/1/31 23:20
 */
public abstract class AbstractChannelHandlerDelegate implements ChannelHandlerDelegate {
    protected final ChannelHandler handler; // protected 方便子类直接访问

    protected AbstractChannelHandlerDelegate(ChannelHandler handler) {
        Assert.notNull(handler, "handler == null");
        this.handler = handler;// HeaderExchangerHandler
    }

    @Override
    public ChannelHandler getHandler() {
        if (handler instanceof ChannelHandlerDelegate) {
            return ((ChannelHandlerDelegate) handler).getHandler();
        }
        return handler;
    }

    @Override
    public void connected(InnerChannel channel) throws RemotingException {
        handler.connected(channel);
    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {
        handler.disconnected(channel);
    }

    @Override
    public void sent(InnerChannel channel, Object message) throws RemotingException {
        handler.sent(channel, message);
    }

    @Override
    public void received(InnerChannel channel, Object message) throws RemotingException {
        handler.received(channel, message);
    }

    @Override
    public void caught(InnerChannel channel, Throwable exception) throws RemotingException {
        handler.caught(channel, exception);
    }

    // ====== 以下是WrappedChannelHandler的内容，为了公用，放到这个类里

    private URL url;

    public void setUrl(URL url) {
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }

    // protected 子类直接调用
    protected void sendFeedBack(InnerChannel channel, Request request, Throwable t) throws RemotingException {
        if (request.isTwoWay()) {
            String msg = "Server side(" + url.getIp() + "," + url.getPort()
                    + ") thread pool is exhausted, detail msg:" + t.getMessage();
            Response response = new Response(request.getId(), request.getVersion());
            response.setStatus(Response.SERVER_THREADPOOL_EXHAUSTED_ERROR);
            response.setErrorMessage(msg);
            channel.send(response);
        }
    }

    // 目前这个方法主要是定制化的，方便消费者端的线程模型
    protected ExecutorService getPreferredExecutorService(Object msg) {
        if (msg instanceof Response) {
            Response response = (Response) msg;
            DefaultFuture future = DefaultFuture.getFuture(response.getId());
            return future == null ? getSharedExecutorServer() : (future.getExecutor() == null ? getSharedExecutorServer() : future.getExecutor());
        } else {
            return getSharedExecutorServer();
        }
    }

    protected ExecutorService getSharedExecutorServer() {
        /*ExecutorRepository repository = ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension();
        ExecutorService executorService = repository.getExecutor(url);
        if (executorService == null) {
            executorService = repository.createExecutor(url);
        }*/
        // todo myRPC 上面都是空实现 ，临时如下
        ExecutorService executorService = Executors.newFixedThreadPool(200,
                new DefaultThreadFactory("dispatch thread"));
        return executorService;
}
}

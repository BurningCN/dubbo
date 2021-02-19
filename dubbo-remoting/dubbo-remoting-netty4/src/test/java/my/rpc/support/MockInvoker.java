package my.rpc.support;

import my.rpc.*;
import my.server.RemotingException;
import my.server.URL;

import java.util.concurrent.CompletableFuture;

/**
 * @author geyu
 * @date 2021/2/19 11:12
 */
public class MockInvoker<T> implements Invoker<T> {
    private final Class<T> clz;
    private final URL url;
    boolean hasException = false;

    public MockInvoker(URL url, Class<T> clz){
        this.clz = clz;
        this.url = url;
    }
    @Override
    public Class<T> getInterface() {
        return clz;
    }

    @Override
    public Result invoke(Invocation invocation) throws RemotingException, Exception {
        AppResponse appResponse = new AppResponse();
        if(hasException){
            appResponse.setException(new RuntimeException("mock exception"));
        }else{
            appResponse.setValue("mock value");
        }

        return new AsyncRpcResult(CompletableFuture.completedFuture(appResponse),invocation);
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void destroy() {

    }
}

package my.rpc.api.filter;

import my.common.extension.Activate;
import my.rpc.*;
import my.server.RemotingException;
import my.server.URL;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * @author geyu
 * @date 2021/2/9 17:13
 */
@Activate(group = "provider")
public class TimeoutFilter implements Filter, Filter.Listener {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RemotingException, Exception {
        return invoker.invoke(invocation);
    }

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        // forTest(invoker.getURL());
        Object obj = RpcContext.getContext().get("timeout-countdown");
        if (obj != null) {
            TimeoutCountDown timeoutCountDown = (TimeoutCountDown) obj;
            if (timeoutCountDown.isExpired()) {
                // 结果清空
                ((AppResponse) appResponse).clear();
                System.out.println("invoke timed out. method: " + invocation.getMethodName() + " arguments: " +
                        Arrays.toString(invocation.getArguments()) + " , url is " + invoker.getURL() +
                        ", invoke elapsed " + timeoutCountDown.elapsedMillis() + " ms.");
            }
        }
    }

    private void forTest(URL url) {
        if(url.hasParameter("timeout-countdown")){
            System.out.println(Thread.currentThread().getName());
            RpcContext.getContext().set("timeout-countdown",TimeoutCountDown.newCountDown(1000, TimeUnit.MILLISECONDS));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {
        // ignore
    }
}

package my.rpc.api.filter;

import my.common.extension.Activate;
import my.rpc.*;
import my.rpc.api.RpcStatus;
import my.server.RemotingException;
import my.server.URL;

/**
 * @author geyu
 * @date 2021/2/9 20:44
 */
@Activate(group = "provider", value = "executes")
public class ExecuteLimitFilter implements Filter, Filter.Listener {
    private static final String EXECUTE_LIMIT_FILTER_START_TIME = "execute_limit_filter_start_time";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RemotingException, Exception {
        URL url = invoker.getURL();
        String methodName = invocation.getMethodName();
        int max = url.getMethodParameter(methodName, "executes", 0);
        if (!RpcStatus.beginCount(url, methodName, max)) {
            throw new RpcException(RpcException.LIMIT_EXCEEDED_EXCEPTION,
                    "Failed to invoke method " + invocation.getMethodName() + " in provider " +
                            url + ", cause: The service using threads greater than <dubbo:service executes=\"" + max +
                            "\" /> limited.");
        }
        invocation.put(EXECUTE_LIMIT_FILTER_START_TIME, System.currentTimeMillis());
        try {
            return invoker.invoke(invocation);
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new RpcException("unexpected exception when ExecuteLimitFilter", t);
            }
        }
    }

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        RpcStatus.endCount(invoker.getURL(), invocation.getMethodName(), getElapsed(invocation), true);
    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {
        if(t instanceof RpcException){
            if(((RpcException)t).isLimitExceed()){
                return;
            }
        }
        RpcStatus.endCount(invoker.getURL(), invocation.getMethodName(), getElapsed(invocation), false);

    }

    private long getElapsed(Invocation invocation) {
        Object start = invocation.get(EXECUTE_LIMIT_FILTER_START_TIME);
        return start == null ? 0 : System.currentTimeMillis() - (long) start;
    }


}

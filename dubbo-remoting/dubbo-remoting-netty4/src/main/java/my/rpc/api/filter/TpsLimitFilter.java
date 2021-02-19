package my.rpc.api.filter;

import my.common.extension.Activate;
import my.rpc.*;
import my.rpc.api.filter.tps.DefaultTpsLimiter;
import my.rpc.api.filter.tps.TpsLimiter;
import my.server.RemotingException;

/**
 * @author geyu
 * @date 2021/2/19 12:03
 */
@Activate(group = "provider", value = "tps")
public class TpsLimitFilter implements Filter {

    private TpsLimiter tpsLimiter = new DefaultTpsLimiter();

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RemotingException, Exception {
        if (!tpsLimiter.isAllowable(invoker.getURL())) {
            throw new RpcException(
                    "Failed to invoke service " +
                            invoker.getInterface().getName() +
                            "." +
                            invocation.getMethodName() +
                            " because exceed max service tps.");
        }
        return invoker.invoke(invocation);
    }
}

package my.rpc.api.filter;

import my.common.extension.Activate;
import my.rpc.*;
import my.server.RemotingException;

/**
 * @author geyu
 * @date 2021/2/8 17:28
 */
@Activate(group = "provider", order = -110000)
public class EchoFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RemotingException, Exception {
        if (invocation.getMethodName().equals("$echo") && invocation.getArguments().length == 1) {
            return AsyncRpcResult.newDefaultAsyncResult(invocation, invocation.getArguments()[0]);
        }
        return invoker.invoke(invocation);
    }
}

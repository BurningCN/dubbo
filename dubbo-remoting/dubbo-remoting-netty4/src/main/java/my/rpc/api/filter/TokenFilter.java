package my.rpc.api.filter;

import my.common.extension.Activate;
import my.common.utils.StringUtils;
import my.rpc.*;
import my.server.RemotingException;

/**
 * @author geyu
 * @date 2021/2/9 18:28
 */
@Activate(group = "provider", value = "token")
public class TokenFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RemotingException, Exception {
        String token = invoker.getURL().getParameter("token");
        if (StringUtils.isNoneEmpty(token)) {
            Object remoteTokenObject = invocation.getObjectAttachment("token");
            boolean isSame = false;
            if (remoteTokenObject != null) {
                String remoteTokenString = (String) remoteTokenObject;
                if (token.equals(remoteTokenString)) {
                    isSame = true;
                }
            }
            if (!isSame) {
                throw new RpcException("Invalid token! Forbid invoke remote service " + invoker.getInterface() + " method " + invocation.getMethodName()
                        + "() from consumer " + RpcContext.getContext().getRemoteHost() + " to provider " + RpcContext.getContext().getLocalHost()
                        + ", consumer incorrect token is " + remoteTokenObject);
            }
        }
        return invoker.invoke(invocation);
    }
}

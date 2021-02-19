package my.rpc.api.filter;

import my.common.extension.Activate;
import my.rpc.Filter;
import my.rpc.Invocation;
import my.rpc.Invoker;
import my.rpc.Result;
import my.server.RemotingException;

/**
 * @author geyu
 * @date 2021/2/19 10:56
 */
@Activate(group = "provider", order = -30000)
public class ClassLoaderFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RemotingException, Exception {
        ClassLoader ocl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(invoker.getInterface().getClassLoader());
            return invoker.invoke(invocation);
        } finally {
            Thread.currentThread().setContextClassLoader(ocl);
        }

    }
}

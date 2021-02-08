package my.rpc;

import my.common.extension.SPI;
import my.server.RemotingException;

/**
 * @author gy821075
 * @date 2021/2/8 17:25
 */
@SPI
public interface Filter {

    Result invoke(Invoker<?> invoker, Invocation invocation) throws RemotingException, Exception;

    interface Listener {
        void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation);

        void onError(Throwable appResponse, Invoker<?> invoker, Invocation invocation);
    }
}

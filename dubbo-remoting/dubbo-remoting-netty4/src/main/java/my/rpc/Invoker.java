package my.rpc;

import my.server.RemotingException;

/**
 * @author gy821075
 * @date 2021/2/4 11:26
 */
public interface Invoker<T> extends Node {

    Class<T> getInterface();

    Result invoke(Invocation invocation) throws Exception, RemotingException;
}

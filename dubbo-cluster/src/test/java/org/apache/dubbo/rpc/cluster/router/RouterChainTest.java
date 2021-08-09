package org.apache.dubbo.rpc.cluster.router;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.Router;
import org.apache.dubbo.rpc.cluster.RouterChain;
import org.apache.dubbo.rpc.cluster.router.state.BitList;
import org.apache.dubbo.rpc.cluster.router.state.RouterCache;
import org.apache.dubbo.rpc.cluster.router.state.StateRouter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.TAG_KEY;
import static org.mockito.Mockito.when;

/**
 * @author geyu
 * @version 1.0
 * @date 2021/8/9 18:19
 */
public class RouterChainTest {

    @Test
    public void test() {
        URL url = URL.valueOf("test://127.0.0.1/test?remote.application=app");
        RouterChain<RouterChainTest> routerChain = RouterChain.buildChain(url);

        routerChain.addRouters(Arrays.asList(new MockRouter()));
        routerChain.addStateRouters(Arrays.asList(new MockStatRouter()));

        routerChain.getRouters();
        routerChain.getStateRouters();


        Invoker invoker1 = Mockito.mock(Invoker.class);
        Invoker invoker2 = Mockito.mock(Invoker.class);
        Invoker invoker3 = Mockito.mock(Invoker.class);
        when(invoker1.getUrl()).thenReturn(url);
        when(invoker2.getUrl()).thenReturn(url);
        when(invoker3.getUrl()).thenReturn(url);

        final Invocation rpcInvocation = new RpcInvocation();
        Assertions.assertThrows(RpcException.class, () -> routerChain.route(url, rpcInvocation));


        routerChain.setInvokers(Arrays.asList(invoker1, invoker2, invoker3));
        Assertions.assertTrue(routerChain.getFirstBuildCache());
        routerChain.loop(true);
        routerChain.loop(false);

        List<Invoker<RouterChainTest>> route = routerChain.route(url, rpcInvocation);

        when(rpcInvocation.getAttachment(TAG_KEY)).thenReturn("test");
        List<Invoker<RouterChainTest>> route1 = routerChain.route(url, rpcInvocation);


        routerChain.destroy();

    }

    class MockRouter implements Router {
        @Override
        public URL getUrl() {
            return null;
        }

        @Override
        public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
            return null;
        }

        @Override
        public boolean isRuntime() {
            return false;
        }

        @Override
        public boolean isForce() {
            return false;
        }

        @Override
        public int getPriority() {
            return 0;
        }
    }

    class MockStatRouter implements StateRouter {
        @Override
        public URL getUrl() {
            return null;
        }

        @Override
        public <T> BitList<Invoker<T>> route(BitList<Invoker<T>> invokers, RouterCache<T> cache, URL url, Invocation invocation) throws RpcException {
            return null;
        }

        @Override
        public boolean isRuntime() {
            return false;
        }

        @Override
        public boolean isEnable() {
            return false;
        }

        @Override
        public boolean isForce() {
            return false;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public String getName() {
            return "mockStateRouter";
        }

        @Override
        public boolean shouldRePool() {
            return false;
        }

        @Override
        public <T> RouterCache<T> pool(List<Invoker<T>> invokers) {
            RouterCache<T> routerChain = new RouterCache<>();

            return routerChain;
        }

        @Override
        public void pool() {

        }
    }
}

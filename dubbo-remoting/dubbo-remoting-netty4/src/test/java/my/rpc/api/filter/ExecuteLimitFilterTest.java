package my.rpc.api.filter;

import my.common.extension.ExtensionLoader;
import my.rpc.*;
import my.rpc.api.RpcStatus;
import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

/**
 * @author geyu
 * @date 2021/2/9 21:25
 */
public class ExecuteLimitFilterTest {
    private ExecuteLimitFilter executeLimitFilter = new ExecuteLimitFilter();

    @Test
    public void testNoExecuteLimitInvoke() throws Exception, RemotingException {
        Invoker invoker = mock(Invoker.class);

        URL url = URL.valueOf("test://test:11/test?accesslog=true&group=xxx&version=1.1");
        when(invoker.getURL()).thenReturn(url);
        when(invoker.invoke(any(Invocation.class))).thenReturn(new AppResponse("halou"));

        Invocation invocation = mock(Invocation.class);
        when(invocation.getMethodName()).thenReturn("testNoExecuteLimitInvoke");

        Result result = executeLimitFilter.invoke(invoker, invocation);
        Assertions.assertEquals(result.getValue(), "halou");

    }

    @Test
    public void testExecuteLimitInvoke() throws Exception, RemotingException {
        Invoker invoker = mock(Invoker.class);

        URL url = URL.valueOf("test://test:11/test?accesslog=true&group=xxx&version=1.1&executes=10");
        when(invoker.getURL()).thenReturn(url);
        when(invoker.invoke(any(Invocation.class))).thenReturn(new AppResponse("halou"));

        Invocation invocation = mock(Invocation.class);
        when(invocation.getMethodName()).thenReturn("testNoExecuteLimitInvoke");

        Result result = executeLimitFilter.invoke(invoker, invocation);
        Assertions.assertEquals(result.getValue(), "halou");

    }

    @Test
    public void testMoreThanExecuteLimitInvoke() throws Exception, RemotingException {

        int maxExecute = 10;
        int totalExecute = 20;

        BlockInvoker blockInvoker = new BlockInvoker(DemoService.class, URL.valueOf("test://test:11/test?accesslog=true&group=xxx&version=1.1&executes=" + maxExecute));
        Invocation invocation = new RpcInvocation();
        invocation.setMethodName("ninini");

        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(totalExecute);
        final AtomicInteger failed = new AtomicInteger(0);

        for (int i = 0; i < totalExecute; i++) {
            new Thread(() -> {
                try {
                    latch.await();
                    Result result = executeLimitFilter.invoke(blockInvoker, invocation);
                    Assertions.assertEquals(result.getValue(), "hahaha");
                } catch (RemotingException | Exception e) {
                    failed.incrementAndGet();
                } finally {
                    latch2.countDown();
                }

            }).start();
        }
        latch.countDown();
        latch2.await();
        Assertions.assertEquals(failed.get(), totalExecute - maxExecute);
    }

    @Test
    public void testByFilterChain() throws RemotingException, InterruptedException { // todo myRPC这个程序有问题，服务端DecodeableRpcInvocation #doDecode解码解的很乱
        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        URL url = URL.valueOf("default://localhost:9999/test?timeout=60000");//&executes=10
        protocol.export(proxyFactory.getInvoker(new DemoServiceImpl(), DemoService.class, url));
        DemoService proxy = proxyFactory.getProxy(protocol.refer(DemoService.class, url));

        final AtomicInteger failed = new AtomicInteger(0);
        final AtomicInteger successes = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch downLatch = new CountDownLatch(20);
        for (int i = 0; i < 20; i++) {
            executorService.submit(() -> {
                try {
                    Assertions.assertTrue(proxy.getThreadName().startsWith("dispatch"));
                    downLatch.countDown();
                    successes.incrementAndGet();
                } catch (RpcException e) {
                    failed.incrementAndGet();
                }
            });
        }
        downLatch.await();
        RpcStatus status = RpcStatus.getStatus(url, "testForExecuteLimit");
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Assertions.assertEquals(status.getFailed().get(), 10);
        Assertions.assertEquals(status.getTotal().get(), 20);
    }

    class BlockInvoker extends AbstractInvoker {

        public BlockInvoker(Class type, URL url) {
            super(type, url);
        }

        @Override
        protected Result doInvoke(Invocation invocation) throws RemotingException {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return AsyncRpcResult.newDefaultAsyncResult(invocation, new AppResponse("hahaha"));
        }
    }

}

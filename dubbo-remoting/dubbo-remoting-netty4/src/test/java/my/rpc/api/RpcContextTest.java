package my.rpc.api;

import my.rpc.RpcContext;
import my.rpc.RpcException;
import my.server.URL;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author geyu
 * @date 2021/2/9 15:19
 */
public class RpcContextTest {

    @Test
    public void testGetRpcCtx() {
        RpcContext context = RpcContext.getContext();
        assertNotNull(context);

        RpcContext.removeContext();
        assertNotNull(RpcContext.getContext());
        assertNotEquals(context, RpcContext.getContext());


        RpcContext serverContext = RpcContext.getServerContext();
        assertNotNull(serverContext);
        RpcContext.removeServerContext();
        assertNotEquals(serverContext, RpcContext.getServerContext());
    }

    @Test
    public void testAddress() {
        RpcContext context = RpcContext.getContext();
        context.setLocalAddress("127.0.0.1", 1000);
        assertEquals(1000, context.getLocalAddress().getPort());

        context.setRemoteAddress("127.0.0.1", 1111);
        assertEquals(1111, context.getRemoteAddress().getPort());

    }


    @Test
    public void testCheckSide() {

        RpcContext context = RpcContext.getContext();
        context.setUrl(URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1"));
        assertFalse(context.isConsumerSide());
        assertTrue(context.isProviderSide());
        context.setUrl(URL.valueOf("test://test:11/test?accesslog=true&group=dubbo&version=1.1&side=consumer"));
        assertTrue(context.isConsumerSide());
        assertFalse(context.isProviderSide());
    }

    @Test
    public void testAttachments() {
        Map<String, Object> map = new HashMap<>();
        map.put("1", 1);
        map.put("2", 1);
        map.put("3", 1);
        RpcContext.getContext().setObjectAttachments(map);
        assertEquals(1, RpcContext.getContext().getObjectAttachment("1"));
        assertEquals(1, RpcContext.getContext().getObjectAttachment("2"));
        assertEquals(1, RpcContext.getContext().getObjectAttachment("3"));
        RpcContext.getContext().clearObjectAttachments();
    }

    @Test
    public void testObject() {

        RpcContext context = RpcContext.getContext();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("_11", "1111");
        map.put("_22", "2222");
        map.put(".33", "3333");
        map.forEach(context::set);
        assertEquals(map, context.get());
        assertEquals("1111", context.get("_11"));
        context.set("_11", "11.11");
        assertEquals("11.11", context.get("_11"));
        context.set(null, "22222");
        context.set("_22", null);
        assertEquals("22222", context.get(null));
        assertNull(context.get("_22"));
        assertNull(context.get("_33"));
        assertEquals("3333", context.get(".33"));
        map.keySet().forEach(context::remove);
        assertNull(context.get("_11"));
    }

    @Test
    public void testAsync() {
        RpcContext context = RpcContext.getContext();
        assertFalse(context.isAsyncStarted());
        AsyncContext asyncContext = RpcContext.startAsync();

        assertTrue(context.isAsyncStarted());

        asyncContext.write(new Object());

        AsyncContextImpl asyncContext1 = (AsyncContextImpl) asyncContext;
        assertTrue(asyncContext1.getFuture().isDone());

        RpcContext.stopAsync();
        assertFalse(context.isAsyncStarted());
        RpcContext.removeContext();
    }

    @Test
    public void testAsyncCall() {
        CompletableFuture<Object> completableFuture = RpcContext.getContext().asyncCall(() -> {
            throw new NullPointerException();
        });
        completableFuture.whenComplete((v, e) -> {
            System.out.println(e.toString());
            assertNull(v);
            assertTrue(e instanceof RpcException);
            assertTrue(e.getCause() instanceof NullPointerException);
        });

        assertThrows(ExecutionException.class, completableFuture::get);
        CompletableFuture new1 = completableFuture.exceptionally(throwable -> "mock success");
        new1.join();
    }
}

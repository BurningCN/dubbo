package my.rpc.api;

import my.rpc.FutureContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

/**
 * @author geyu
 * @date 2021/2/9 16:10
 */
public class FutureContextTest {

    @Test
    public void TestFuture() {
        Thread thread1 = new Thread(() -> {
            FutureContext.getContext().setFuture(CompletableFuture.completedFuture("this is thread1 future"));
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Assertions.assertEquals(FutureContext.getContext().getFuture(), "this is thread1 future");
        });
        thread1.start();

        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Assertions.assertNull(FutureContext.getContext().getFuture());
        });
        thread2.start();
    }
}

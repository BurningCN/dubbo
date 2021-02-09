package my.rpc.api;

import my.rpc.AppResponse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * @author geyu
 * @date 2021/2/9 17:04
 */
public class AppResponseTest {
    @Test
    public void testAppResponseWithNormalException() {
        NullPointerException nullPointerException = new NullPointerException();
        AppResponse appResponse = new AppResponse(nullPointerException);
        StackTraceElement[] stackTrace = appResponse.getException().getStackTrace();
        assertNotNull(stackTrace);
        assertTrue(stackTrace.length > 1);
    }
}

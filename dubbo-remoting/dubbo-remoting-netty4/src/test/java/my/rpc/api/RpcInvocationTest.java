package my.rpc.api;

import my.rpc.RpcInvocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

/**
 * @author geyu
 * @date 2021/2/9 15:13
 */
public class RpcInvocationTest {

    @Test
    public void testAttachment() {
        RpcInvocation rpcInvocation = new RpcInvocation();
        rpcInvocation.setAttachment("k1", "v1");
        rpcInvocation.setAttachment("k2", "v2");
        rpcInvocation.setAttachment("k3", "v3");
        rpcInvocation.setObjectAttachment("k4", 10);

        Assertions.assertFalse(rpcInvocation.getAttachments().size() == 4);
        Assertions.assertTrue(rpcInvocation.getObjectAttachments().size() == 4);

        Assertions.assertTrue(rpcInvocation.getAttachment("k4") == null);
        Assertions.assertFalse(rpcInvocation.getObjectAttachment("k4") == null);

        HashMap<String, Object> map = new HashMap<>();
        map.put("mapKey1", 1);
        map.put("mapKey2", "mapValue2");
        rpcInvocation.setObjectAttachments(map);
        Assertions.assertEquals(map, rpcInvocation.getObjectAttachments());


    }
}

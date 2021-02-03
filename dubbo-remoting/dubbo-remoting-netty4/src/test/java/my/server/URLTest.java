package my.server;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author geyu
 * @date 2021/1/29 18:20
 */
public class URLTest {
    @Test
    void testValueOf(){
        String str =  "dubbo://110:As2S@localhost:9999/org.apache.HelloService?k1=v1&k2=v2";
        URL url = URL.valueOf(str);
        Assertions.assertEquals("dubbo",url.getProtocol());
        Assertions.assertEquals("110",url.getUsername());
        Assertions.assertEquals("As2S",url.getPwd());
        Assertions.assertEquals("localhost",url.getHost());
        Assertions.assertEquals("org.apache.HelloService",url.getPath());
        Assertions.assertEquals(2,url.getParameters().size());
        Assertions.assertEquals("v1",url.getParameters().get("k1"));
        Assertions.assertEquals("v2",url.getParameters().get("k2"));
    }
}

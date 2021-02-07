package my.server.serilization;

import my.server.serialization.ObjectInput;
import my.server.serialization.ObjectOutput;
import my.server.support.World;
import my.server.serialization.hessian2.Hessian2Serialization;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author geyu
 * @date 2021/2/2 19:43
 */
public class Hessian2Test {
    private static ObjectInput objectInput;

    @Test
    public void test() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Hessian2Serialization hessian2Serialization = new Hessian2Serialization();
        ObjectOutput objectOutput = hessian2Serialization.serialize(bos);
        objectOutput.writeObject(new World("haha"));
        objectOutput.flushBuffer();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInput objectInput = hessian2Serialization.deSerialize(bis);
        Object o = objectInput.readObject();
        System.out.println(o.getClass());
    }

    @Test
    public void testMultiThreadSharedOneObjectInput() throws IOException, InterruptedException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Hessian2Serialization hessian2Serialization = new Hessian2Serialization();
        ObjectOutput objectOutput = hessian2Serialization.serialize(bos);
        objectOutput.writeUTF("hahhah");
        objectOutput.flushBuffer();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        objectInput = hessian2Serialization.deSerialize(bis);
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.out.println(objectInput.readUTF());
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        t.start();
        t.join();

    }
}

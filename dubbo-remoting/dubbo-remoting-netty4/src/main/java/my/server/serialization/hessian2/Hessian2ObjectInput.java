package my.server.serialization.hessian2;

import com.alibaba.com.caucho.hessian.io.Hessian2Input;
import my.server.serialization.ObjectInput;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * @author geyu
 * @date 2021/2/2 19:11
 */
public class Hessian2ObjectInput implements ObjectInput {

    private static ThreadLocal<Hessian2Input> INPUT_TL = ThreadLocal.withInitial(() -> {
        Hessian2Input hessian2Input = new Hessian2Input(null);
        hessian2Input.setSerializerFactory(new DefaultHessian2FactoryInitializer().getSerializerFactory());
        hessian2Input.setCloseStreamOnClose(true);
        return hessian2Input;
    });

    private final Hessian2Input h2i;

    public Hessian2ObjectInput(InputStream bis) {
        h2i = INPUT_TL.get();
        h2i.init(bis);
    }

    @Override
    public Object readObject() throws IOException {
        return h2i.readObject();
    }

    @Override
    public <T> T readObject(Class<T> clz) throws IOException {
        return (T) h2i.readObject(clz);
    }

    @Override
    public <T> T readObject(Class<T> clz, Type type) throws IOException {
        return readObject(clz);
    }

    @Override
    public String readUTF() throws IOException {
        return h2i.readString();
    }
}

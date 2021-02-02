package netty.server.serialization.hessian2;

import com.alibaba.com.caucho.hessian.io.Hessian2Output;
import netty.server.serialization.ObjectOutput;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author geyu
 * @date 2021/2/2 19:12
 */
public class Hessian2ObjectOutput implements ObjectOutput {

    private static ThreadLocal<Hessian2Output> OUTPUT_TL = ThreadLocal.withInitial(() -> {
        Hessian2Output hessian2Output = new Hessian2Output(null);
        hessian2Output.setSerializerFactory(new DefaultHessian2FactoryInitializer().getSerializerFactory());
        hessian2Output.setCloseStreamOnClose(true);
        return hessian2Output;
    });

    private Hessian2Output h2o;

    public Hessian2ObjectOutput(OutputStream bos) {
        h2o = OUTPUT_TL.get();
        h2o.init(bos);
    }

    @Override
    public void flushBuffer() throws IOException {
        h2o.flushBuffer();
    }

    @Override
    public void writeObject(Object data) throws IOException {
        h2o.writeObject(data);
    }

    @Override
    public void writeUTF(String v) throws IOException {
        h2o.writeString(v);
    }
}

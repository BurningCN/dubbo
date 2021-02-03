package my.server.serialization.hessian2;

import com.alibaba.com.caucho.hessian.io.SerializerFactory;
import org.apache.dubbo.common.serialize.hessian2.Hessian2SerializerFactory;

/**
 * @author geyu
 * @date 2021/2/2 19:30
 */
public class DefaultHessian2FactoryInitializer extends AbstractHessian2FactoryInitializer {
    @Override
    public SerializerFactory getSerializerFactory() {
        return new Hessian2SerializerFactory();
    }
}

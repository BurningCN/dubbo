package my.server.serialization.hessian2;

import com.alibaba.com.caucho.hessian.io.SerializerFactory;

/**
 * @author geyu
 * @date 2021/2/2 19:26
 */
public class AbstractHessian2FactoryInitializer implements Hessian2FactoryInitializer {

    SerializerFactory SERIALIZER_FACTORY;

    public SerializerFactory getSerializerFactory() {
        if (SERIALIZER_FACTORY == null) {
            synchronized (SERIALIZER_FACTORY) {
                if (SERIALIZER_FACTORY == null) {
                    createSerializerFactory();
                }
            }
        }
        return SERIALIZER_FACTORY;
    }

    protected void createSerializerFactory() {
    }
}

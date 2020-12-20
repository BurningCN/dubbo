package org.apache.dubbo.common.function;

/**
 * @author geyu
 * @version 1.0
 * @date 2020/12/20 17:12
 */
@FunctionalInterface
public interface ThrowableConsumerX<T> {

    void consume(T t);

    default void execute(T t){
        try{
            consume(t);
        }catch (Throwable e){
            throw  new RuntimeException(e.getMessage(),e.getCause());
        }
    }

     static <T> void execute(T t,ThrowableConsumerX consumerX ){
        consumerX.execute(t);
    }
}

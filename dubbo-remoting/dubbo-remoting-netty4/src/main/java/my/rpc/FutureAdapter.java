package my.rpc;


import java.util.concurrent.CompletableFuture;

/**
 * @author geyu
 * @date 2021/2/5 11:49
 */
public class FutureAdapter<V> extends CompletableFuture<V>  {

    public FutureAdapter(CompletableFuture<AppResponse> future){

    }
}

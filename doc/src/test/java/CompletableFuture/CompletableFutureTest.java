package CompletableFuture;

import POJO.Discount;
import POJO.Quote;
import POJO.Shop;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import  java.util.concurrent.*;


/**
 * @author gy821075
 * @date 2020/12/22 10:55 上午
 */
public class CompletableFutureTest {

    public Future<Double> getPrice(){
        CompletableFuture<Double> completableFuture = new CompletableFuture<>();
        new Thread(()->{
            try {
                // sleep ...
                completableFuture.complete(100d);
            }catch (Exception e){
                completableFuture.completeExceptionally(e);
            }

        }).start();
        return completableFuture;
    }

    @Test
    public void findAllShopsSequential() {
        // CompletableFuture异步编程 =====================================================================================
        Future<Double> price1 = getPrice();
        // doSth... 耗时操作(上面getPrice并行)
        try {
            price1.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }


        List<Shop> shopList = Arrays.asList(new Shop("A", 1f),
                new Shop("B", 2f),
                new Shop("C", 3f),
                new Shop("D", 4f));
        // stream ==========================================================================================
        List<String> collect = shopList.stream()
                .map(shop -> String.format("name:%s,price:%f", shop.name, shop.price))
                .collect(Collectors.toList());


        // parallelStream ==========================================================================================
        List<String> collect1 = shopList.parallelStream()
                .map(shop -> String.format("name:%s,price:%f", shop.name, shop.price))
                .collect(Collectors.toList());

        // CompletableFuture.supplyAsync ==========================================================================================
        List<CompletableFuture<String>> collect2 = shopList.stream()
                .map(shop -> CompletableFuture.supplyAsync(() -> String.format("name:%s,price:%f", shop.name, shop.price)))
                .collect(Collectors.toList());
        List<String> collect3 = collect2.stream().map(CompletableFuture::join).collect(Collectors.toList());

        // CompletableFuture.supplyAsync(() 和 parallelStream内部
        //采用的是同样的通用线程池，默认都使用固定数目的线程，具体线程数取决于Runtime.
        //getRuntime().availableProcessors()的返回值。


        // CompletableFuture.supplyAsync + executorService ==========================================================================================
        // N = cpu个数*期望的cpu利用率*(1+等待时间于计算时间的比率)
        ExecutorService executorService = Executors.newFixedThreadPool(Math.min(100, shopList.size()), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            }
        });
        List<CompletableFuture<String>> collect4 = shopList.stream()
                .map(shop -> CompletableFuture.supplyAsync(() -> String.format("name:%s,price:%f", shop.name, shop.price),executorService))
                .collect(Collectors.toList());
        List<String> collect5 = collect4.stream().map(CompletableFuture::join).collect(Collectors.toList());


        // CompletableFuture.supplyAsync + thenApply + thenCompose ==========================================================================================
        List<Object> collect6 = shopList.stream().map(shop -> shop.price).map(Quote::parse).map(Discount::applyDiscount).collect(Collectors.toList());
        // 将上面的三个同步map改成异步
        List<CompletableFuture<String>> collect7 = shopList.stream()
                .map(shop -> CompletableFuture.supplyAsync(() -> shop.price, executorService))
                .map(future -> future.thenApply(Quote::parse))
                .map(future -> future.thenCompose(quote -> CompletableFuture.supplyAsync(() -> Discount.applyDiscount(quote), executorService)))
                .collect(Collectors.toList());
        List<Object> collect8 = collect7.stream().map(CompletableFuture::join).collect(Collectors.toList());

        // thenCombine
        Shop shop = new Shop("DDD",12f);
        CompletableFuture
                .supplyAsync(()->shop.name)
                .thenCombine( CompletableFuture.supplyAsync(()->shop.price) , (name,price)->name+price+"" );

    }


}

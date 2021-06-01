package samples.sd.transfer.consumer;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import samples.sd.transfer.demo.GreetingService;

import java.util.concurrent.CountDownLatch;

/**
 * @author BurningCN
 * @date 2021/5/31 16:58
 */
public class ApplicationNew {
    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("samples.sd.transfer/dubbo-consumer.xml");
        context.start();

        GreetingService greetingService = context.getBean("greetingService", GreetingService.class);
        String hello = greetingService.hello();
        System.out.println("result: " + hello);

        new CountDownLatch(1).await();
    }
}

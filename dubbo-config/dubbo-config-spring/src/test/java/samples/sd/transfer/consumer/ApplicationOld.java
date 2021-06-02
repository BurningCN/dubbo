package samples.sd.transfer.consumer;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import samples.sd.transfer.demo.DemoService;
import samples.sd.transfer.demo.GreetingService;

import java.util.concurrent.CountDownLatch;

/**
 * @author BurningCN
 * @date 2021/5/31 16:58
 */
public class ApplicationOld {
    public static void main(String[] args) throws Exception {

        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("samples.sd.transfer/dubbo-consumer-old.xml");
        context.start();
        DemoService demoService = context.getBean("demoService", DemoService.class);
        String hello = demoService.sayHello("world");
        System.out.println("result: " + hello);
        GreetingService greetingService = context.getBean("greetingService", GreetingService.class);
        System.out.print("greetings: " + greetingService.hello());
        new CountDownLatch(1).await();

    }
}

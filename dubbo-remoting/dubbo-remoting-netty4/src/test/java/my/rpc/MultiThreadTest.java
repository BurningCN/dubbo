package my.rpc;


import my.common.extension.ExtensionLoader;
import my.common.rpc.model.ApplicationModel;
import my.rpc.support.DemoService;
import my.rpc.support.DemoServiceImpl;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author geyu
 * @date 2021/2/8 15:00
 */
public class MultiThreadTest {
    private Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    private ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    @AfterEach
    public void after() throws RemotingException {
        ApplicationModel.getServiceRepository().destroy();
        DefaultProtocol.getDefaultProtocol().destroy();
    }

    @Test
    public void multiThreadInvokeTest() throws RemotingException, InterruptedException {
        ApplicationModel.getServiceRepository().registerService("TestService", DemoService.class);
        URL demoURL = URL.valueOf("default://localhost:8888/TestService?heartbeat=600000&timeout=600000");
        Invoker<DemoService> demoServerInvoker = proxyFactory.getInvoker(new DemoServiceImpl(), DemoService.class, demoURL);
        Exporter<DemoService> serverExporter = protocol.export(demoServerInvoker);

        Invoker<DemoService> clientInvoker = protocol.refer(DemoService.class, demoURL);
        DemoService clientProxy = proxyFactory.getProxy(clientInvoker);

        Assertions.assertEquals(3, clientProxy.getSize(new String[]{"1", "2", "3"}));

        final StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 1024 * 64 + 32; i++)
            sb.append('A');
        Assertions.assertEquals(sb.toString(), clientProxy.echo(sb.toString()));

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        final AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            final int fi = i;
            executorService.submit(() -> {
                for (int j = 0; j < 30; j++) {
                    System.out.println(fi + ":" + counter.getAndIncrement());
                    Assertions.assertEquals(clientProxy.echo(sb.toString()), sb.toString());
                }
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
        executorService.shutdownNow();
        serverExporter.unExport();
    }

}

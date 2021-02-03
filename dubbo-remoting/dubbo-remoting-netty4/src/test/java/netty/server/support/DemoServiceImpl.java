package netty.server.support;

/**
 * @author geyu
 * @date 2021/2/3 14:25
 */
public class DemoServiceImpl implements DemoService {

    @Override
    public String sayHello(String name) {
        return "hello  " + name;
    }

    @Override
    public int minus(int a, int b) {
        return a - b;
    }
}

package my.rpc.support;

/**
 * @author geyu
 * @date 2021/2/4 18:11
 */
public class HelloServiceImpl implements HelloService {
    @Override
    public String hello() {
        return "nihao";
    }
}

package my.rpc.api.filter;

import my.rpc.Invocation;
import my.rpc.Invoker;
import my.rpc.Result;
import my.rpc.support.DemoService;
import my.rpc.support.MockInvoker;
import my.server.RemotingException;
import my.server.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URLClassLoader;

public class ClassLoaderFilterTest {

    private ClassLoaderFilter classLoaderFilter = new ClassLoaderFilter();

    @Test
    public void testInvoke() throws Exception, RemotingException {
        URL url = URL.valueOf("default://localhost:9999?test?group=lala&version=2.2.2");
        String path = DemoService.class.getResource("/").getPath();
        URLClassLoader urlClassLoader = new URLClassLoader(new java.net.URL[]{new java.net.URL("file:" + path)}) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                try {
                    return findClass(name);// 自定义类加载器一定是重写loadClass，并且！！要调用这个方法findClass，因为该方法内部会抛异常，这样我们补货异常，然后让上层加载器去加载
                } catch (ClassNotFoundException e) {
                    return super.loadClass(name); // 比如下面要加载DemoService的时候，肯定是需要先加载父类Object，但是这个我们的自定义URLClassLoader是无法加载的，就需要走这里的逻辑
                }
            }
        };
        // 注意实际在loadClass的时候还是利用AppClassLoader加载的，不过会传递给URLClassLoader
        // 如果对aClass和DemoService.class分别调用getClassLoader,发现返回的分别是URLClassLoader和AppClassLoader
        Class<?> aClass = urlClassLoader.loadClass(DemoService.class.getCanonicalName());

        Invoker invoker = new MockInvoker(url, DemoService.class) {
            @Override
            public Class getInterface() { // 注意一定要重写这个方法，因为我们是要URLClassLoader
                return aClass;
            }

            @Override
            public Result invoke(Invocation invocation) throws RemotingException, Exception {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Assertions.assertEquals(loader, urlClassLoader);
                Assertions.assertTrue(loader instanceof URLClassLoader);
                return super.invoke(invocation);
            }
        };
        Invocation invocation = Mockito.mock(Invocation.class);
        classLoaderFilter.invoke(invoker, invocation);
    }
}

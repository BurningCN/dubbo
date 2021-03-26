https://blog.csdn.net/qq_16055765/article/details/78833260

在spring 中使用注解，常使用@Autowired， 默认是根据类型Type来自动注入的。但有些特殊情况，对同一个接口，可能会有几种不同的实现类，而默认只会采取其中一种的情况下 @Primary 的作用就出来了。下面是个简单的使用例子。

有如下一个接口

```
public interface Singer {
    String sing(String lyrics);
}123
```

有下面的两个实现类:

```
@Component // 加注解，让spring识别
public class MetalSinger implements Singer{

    @Override
    public String sing(String lyrics) {
        return "I am singing with DIO voice: "+lyrics;
    }
}12345678
```

//注意，这里没有注解

```
public class OperaSinger implements Singer {
    @Override
    public String sing(String lyrics) {
        return "I am singing in Bocelli voice: "+lyrics;
    }
}123456
```

下面就是注入上面的

```
@Component
 public class SingerService {
        private static final Logger logger = LoggerFactory.getLogger(SingerService.class);
    @Autowired
    private Singer singer;
    public String sing(){
        return singer.sing("song lyrics");
    }
   }
123456789
```

I am singing with DIO voice: song lyrics. 原因很简单，就是 OperaSinger 这个类上面根本没有加上注解@Copmonent 或者 @Service, 所以spring 注入的时候，只能找到 MetalSinger 这个实现类. 所以才有这个结果。

但是如果一旦 OperaSinger 这个类加上了@Copmonent 或者 @Service 注解，有趣的事情就会发生，你会发现一个错误的结果或异常:
org.springframework.beans.factory.NoUniqueBeanDefinitionException: No qualifying bean of type [main.service.Singer] is defined: expected single matching bean but found 2: metalSinger,operaSinger

提示很明确了，spring 根据类型无法选择到底注入哪一个。这个时候@Primay 可以闪亮登场了。

```
@Primary
@Component
public class OperaSinger implements Singer{

    @Override
    public String sing(String lyrics) {
        return "I am singing in Bocelli voice: "+lyrics;
    }
}123456789
```

如果代码改成这样，再次运行，结果如下：
“I am singing in Bocelli voice: song lyrics”， 用@Primary 告诉spring 在犹豫的时候优先选择哪一个具体的实现。

二、用@Qualifier这个注解来解决问题

将上面的两个类改为如下:

```
@Component // 加注解，让spring识别
@Qualifier("metalSinger")
public class MetalSinger implements Singer{

    @Override
    public String sing(String lyrics) {
        return "I am singing with DIO voice: "+lyrics;
    }
}

@Component
@Qualifier("opreaSinger")
public class OperaSinger implements Singer {
    @Override
    public String sing(String lyrics) {
        return "I am singing in Bocelli voice: "+lyrics;
    }
}123456789101112131415161718
@Component
public class SingerService {
    private static final Logger logger = LoggerFactory.getLogger(SingerService.class);

    @Autowired
    private Singer singer;

    @Qualifier("opreaSinger")
    public String sing(){
        return singer.sing("song lyrics");
    }
}123456789101112
```

扩展：Spring注解常用汇总

使用注解之前要开启自动扫描功能

其中base-package为需要扫描的包(含子包)


@Configuration把一个类作为一个IoC容器，它的某个方法头上如果注册了@Bean，就会作为这个Spring容器中的Bean。
@Scope注解 作用域
@Lazy(true) 表示延迟初始化
@Service用于标注业务层组件、
@Controller用于标注控制层组件（如struts中的action）
@Repository用于标注数据访问组件，即DAO组件。
@Component泛指组件，当组件不好归类的时候，我们可以使用这个注解进行标注。
@Scope用于指定scope作用域的（用在类上）
@PostConstruct用于指定初始化方法（用在方法上）
@PreDestory用于指定销毁方法（用在方法上）
@Resource 默认按名称装配，当找不到与名称匹配的bean才会按类型装配。
@DependsOn：定义Bean初始化及销毁时的顺序
@Primary：自动装配时当出现多个Bean候选者时，被注解为@Primary的Bean将作为首选者，否则将抛出异常
@Autowired 默认按类型装配，如果我们想使用按名称装配，可以结合@Qualifier注解一起使用
@Autowired @Qualifier(“personDaoBean”) 存在多个实例配合使用
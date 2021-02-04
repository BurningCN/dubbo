**001.Comparator的设计**。Prioritized接口继承Comparable接口并以default默认方法的方式实现了，以及提供了外界直接访问的Comparator对象，以及getPriority方法可以被子类重写实现不同子类的自己的优先级定义。详见Prioritized、PrioritizedTest

**002.泛型设计。**Converter<S, T>接口本身支持S->T类型的转化，但是StringConverter子接口的引入限定了仅支持String->T的转化，子类只能传递一个T。

**003.类型转化设计。**Converter SPI接口有多个扩展类，支持String->T的转化。用户给定的S->T是否支持转化，内部用了很多泛型参数、获取父类、父接口等相关API的操作以及结合lambda流式处理和过滤。比如StringToBooleanConverter在accept判断的时候就会获取String.class和Integer.class。

**004.MultiConverter和Converter的设计。**都是accept+convert+getConverter/find几个方法。以及都继承了Prioritized，用以在find的时候优先对优先级高的进行accept判定。StringToIterableConverter的Convert方法用了模板方法设计模式。

**005.Completable异步编程技巧。**ScheduledCompletableFuture.submit提交一个任务，返回CompletableFuture对象，把这个future填充到集合里面，在某处会遍历该集合，判断future.isDone并作对应的处理，比如isDone=false，进行future.cancel。isDone完成的时间点在future.complete(xx)的触发，详见ScheduledCompletableFuture。**实现一个带有返回结果的任务**不一定使用callable，可以直接用CompletableFuture。

**006.自定义线程池的拒绝策略。**详见AbortPolicyWithReport。在原生AbortPolicy的基础上做了一些扩展功能：log、dumpJStack、dispatchThreadPoolExhaustedEvent。以及涉及到了事件监听模型，在线程池Exhausted的时候，构建ThreadPoolExhaustedEvent并派遣给对应的监听器处理。

**007.ThreadPool的设计**。ThreadPool是一个**SPI**接口，扩展类给出了不同线程池的实现，**核心还是利用ThreadPoolExecutor**，只是参数不同，参数大都从URL取得，以及用了**自定义的线程工厂、拒绝策略**等。特别看了下**EagerThreadPoolExecutor和TaskQueue**，前者属于自定义的线程池，**重写了afterExecute和execute方法做一些前置后置操作**，以及在发生拒绝异常的时候重试投递到TaskQueue，且**TaskQueue的offer重写了，使得不一定任务队列满了才会创建超过核心线程数大小的线程。**

**008.更快速的ThreadLocal。**涉及到的类型InternalThread、InternalThreadLocal、InternalThreadMap。快速体现在使用了数组存储，线程副本实现思想和原生ThreadLocal一致。

**009.注解工具类。** 判断某个类上的注解是否是某个注解（isSameType）、获取注解里面的值（getAttribute）、获取类上的所有注解并提供带谓词过滤的（getDeclaredAnnotations）、获取类以及所有父类、父接口的注解、获取元注解、获取所有的元注解包括元注解本身的元注解、类上是否匹配多个注解、根据注解的全限定名获取类的对应注解（testGetAnnotation）、

**010.UnmodifiableXXX、SingletonList。**很多工具类返回的集合等数据都用unmodifiableXXX包装了，防止修改。

**011.AnnotatedElement是Class和Method的父接口**，getDeclaredAnnotations方法用AnnotatedElement来接受的原因是：有时候我们不仅仅想获取类上的注解（传入A.class），还想获取方法上的注解（传入的是Method m）虽然Class和Method都有getDeclaredAnnotations，但是为了通用，就都用AnnotatedElement接受，一定程度的抽取公共部分解耦。

**012.ExecutorService线程池的优雅关闭。** shutdwon+awaitTermination+shutdownNow+线程循环shutdownNow详见ExecutorUtil。

**013.ClassUtil实现了自己的forName。**涉及到线程上下文加载器、loadClass api等。

**014.很多工具类都是私有化自己的构造方法的。**还有一些单例模式，比如CharSequenceComparator。

**015.ConcurrentHashSet的实现借助了ConcurrentHashMap**。内部的value用present填充。

**016.${}占位符的解析和替换。**主要利用了Pattern、Matcher，详见replaceProperty方法。

**017.线程上下文加载器加载resources下的文件。**在当前模块下对resources目录下的的文件进行file.exist()都会返回false，需要利用线程上下文加载器加载。getResourceAsStream、getResources、ClassUtils.getClassLoader().getResources(fileName)

**018.LFU的设计。**存储结构：Map+CacheDeque，队列之间用CacheDeque.nextDeque指针连接，put的元素永远放在第0个队列最后，当超过容量大小开始驱逐（驱逐的个数取决于驱逐因子evictionFactor的值），每次get元素的时候影响到元素的访问次数，get会将元素所在的队列迁移到挨着的下一个队列，访问次数越多，越靠后。

**019.LRU的设计**。LinkedHashMap+锁保证安全。

**020.Dubbo基于Spring提供的NamespaceHandler和BeanDefinitionParser来扩展了自己XML Schemas。**实现spring中自定义xml标签并解析一般需要四个步骤:提供自己的BeanDefinition解析器、命名空间处理器（注册前面自己的BeanDefinition解析器）、配置spring.handlers用以关联命名空间处理器和xsd中的targetNamespace、配置spring.schemas指定dubbo.xsd的路径。

**021.SpringExtensionFactory。**dubbo的其中一种容器工厂，用于获取spring相关的bean，内部有缓存所有add进来的ioc容器，获取bean的时候实际是循环遍历从ioc容器获取。不同容器的beanName是可以重复的。

**022.责任链模式，扩展类的Wrapper类。** 去看下上面Protocol$Adaptive的export方法，最后进入DubboProtocol的export方法，但是注意了！！！getExtension最后返回的是QosProtocolWrapper，原因是因为在getExtension内部处理会调用createExtension(String name, boolean wrap)，且默认wrap是ture即扩展类实例（比如DubboProtocol）需要被包装，对于Protocol来说在loadClass的时候有三个WrapperClass（根据是否含有拷贝构造函数），分别是QosProtocolWrapper、ProtocolFilterWrapper、ProtocolListenerWrapper，按照@Activate(order=xx)的值以及WrapperComparator.COMPARATOR进行排序，然后千层饼一样包装（其实是责任链模式），最后QosProtocolWrapper（ProtocolFilterWrapper（ProtocolListenerWrapper（DubboProtocol））））然后export一层层深入调用，每层加了自己的逻辑

**023.ProxyFactory**。含有getProxy和getInvoker，利用jdk和Javassist生成基于接口的代理类。

**024.hashCode和equals的常见写法。**hashcode是 result = primie*result + att==null?0:attr.hashCode()  （prime和result初始值为31和1）equals就不说了。随便找一个参考下吧

**025.从getXXX方法提取XXX提取以及根据驼峰转为split分割的字符串。** 详见calculatePropertyFromGetter和camelToSplitName。

**026.值结果参数。**new一个对象，作为参数传入到一个不带返回值的方法，内部会对这个参数填值。ApplicationConfigTest.testName测程序。

**027.CompositeConfiguration的设计+AbstractConfig的refresh操作。**CompositeConfiguration是一种组合不同场景Configuration的Configuration；AbstractConfig的refresh操作，把CompositeConfiguration的一些参数赋值给AbstractConfig的一些属性赋值，且考虑到不同场景Configuration的优先级，同名key优先取谁的值。

**028.一些恢复现场的操作。**test程序System.setProperty(x,y)在最后一定clearProperty、filed.isAccessable() = false的时候设置为true最后在还原为false。

**029.Properties和文件的交互。**properties.load(this.getClass().getResourceAsStream("/dubbo.properties"));

**030.两个AbstractConfig的子类对象是否equals的逻辑。**name相同、两个对象同名同参的getXx方法的返回值相同。还有个小技巧就是如果@parameter注解带有exclued为true，那么不参与比较。

**031.延迟加载。**compositeConfiguration内部匹配到PropertiesConfiguration有属性xx参数的话，在PropertiesConfiguration.getInternalProperty内部有延迟加载模式，因为加载文件内容涉及到io操作，相对耗时。

**032.isValid方法，动态指定是否可用。**checkRegistry方法：registries里面有很多对象，如果暂时不想某些类型的注册中心对象生效，可以isValid置为false

**033.ConfigManager的read、write方法。**ConfigManager内部关于configsCache的读写业务逻辑操作都封装了runnable任务/callable，并传给write或者后面的read，并且write、read方法内部使用读写锁保护了configCache。

**034.DubboShutdownHook。**在DubboBootstrap的构造方法内部向jvm注册了一个DubboShutdownHook，其run方法主要是执行所有注册的回调以及资源清理动作（还涉及到一些事件派发）。回调的注册在DubboShuthookCallbacks类，填充了很多DubboShuthookCallback(且有优先级)，并根据spi能加载配置的子类对象。注：DubboShutdownHook、DubboShuthookCallbacks都是饿汉单例的。DubboBootStrap是双重检查的单例模式。

**035.类结构设计，接口->Abstract类->实现类**。Abstract里面可以放置一些公共逻辑，实现模板方法模式。比如ZookeeperClient->AbstractZookeeperClient->CuratorZookeeperClient，ZookeeperTransporter->AbstractZookeeperTransporter->CuratorZookeeperTransporter等。

**036.观察者模式。**详见AbstractZookeeperClient的stateChanged方法。被观察者的状态变化，调用所有观察者的方法

036.Curator连接zk的客户端。

**037.连接复用，缓存设计。**AbstractZookeeperTransport的connect方法内部两次查询缓存，都查不到才会创建zkClinet，利用缓存实现连接复用，不会多次创建相同的connection。

**038.抽象工厂模式。**ChannelBufferFactory接口、ChannelBuffer接口以及相关的实现类利用了标准的抽象工厂模式。

**039.动态缓冲区，自动扩容。**详见ensureWritableBytes以及重写的方法（2倍递增直到超过期望的最小目标容量）

**040.过滤器、责任链模式。**在调用Protocol实例的export方法的时候，会经过FilterProtocol的export，内部会取出很多filter进行拦截。我们也可以根据自己进行扩展。

041.AbstractRegistry注册、订阅、同步异步保存文件、文件锁

**042.根据url搞线程池。**AbstractDynamicConfiguration，构造函数会根据url参数来创建线程池（前缀、核心线程从url取），以及getConfig、removeConfig等操作当做一个任务交给线程池执行，以及根据timeout值来决定是带超时的阻塞还是不带...

**043.AbstractExporter抽象类定义构造方法的意义的就是子类公用父类逻辑。**

**044.DefaultTPSLimiter限流**。限制一个service的调用在interval内至多调用rate+1次、内部用到了longAddr作为次数，以及当前时间>interval+lastRestTime会重置lastResetTime和token。

**045.ActiveLimitFilter限定一个方法的调用次数以及涉及到超时**。如果有超过配置的正在尝试调用远程方法，则调用其余的方法将等待配置的超时(默认为0秒)，然后调用被dubbo终止。里面的一些设计点：双重检查判定次数是否达到active的值，sync+while+wait的惯用法，wait醒来后判断是否超时，超时抛异常。以及一些RpcStatus 的设计（两个维度，service和method），其有一个active属性，在beginCount方法中利用cas+for进行++，endCount反操作，以及在onResponse触发后调用rpcStatus.notifyAll();唤醒在sync等待的线程，RpcStatus还有一些统计指标（成功失败次数、总时长、最大时长）。其测试程序（testInvokeNotTimeOut方法）有个设计点，两个latch分别控制多线程的起点和重点。

**046.ExecuteLimitFilter。**类似于上面的，不过不考虑超时。

**047.ProtocolListenerWrapper**。本身实现了Protocol，这个类是做一个拦截/代理逻辑，在发起其含有的目标对象protocol.export和refer方法后，会在export或refer的返回值之后用ListenerExporterWrapper或者ListenerInvokerWrapper包装起来，并通过spi加载对应的监听器，相当于就是说export之后，那些监听器关心的话，会调用相应的的处理函数。且注意ListenerExporterWrapper或者ListenerInvokerWrapper会返回给调用方，要做到对调用方只关心返回类型是Exporter和Invoker，所以这两个类本身是实现Exporter和Invoker的。

**048.RpcException。**本身继承runtimeException，可以传入code、message、cause，code代表不同场景下的异常，也提供了isXX是否是某种异常，注意在isLimitExceed方法里面的这个api记下：getCause() instanceof LimitExceededException;

**049.RpcContext。**一个临时状态容器。每次发送或接收请求时，RpcContext中的状态都会发生变化。内置核心InternalThreadLocal，LOCAL、SERVER_LOCAL这种，以及一些操作，例如：getContext、removeContext、getServerContext、removeServerContextsetUrl、isConsumerSide、isProviderSide、setLocalAddress、setRemoteAddress、setObjectAttachments（Attachments等相关）、values map容器、AsyncContext、asyncCall。

**050.AppResponse**。result的实现类，主要可以存放值、异常、attachments map、还有一个recreate方法，这个通过循环拿到异常的顶级父类Throwable，然后反射获取stackTrace字段，看是不是null是的话，设置exception.setStackTrace(new StackTraceElement[0]);还接触到UnsupportedOperationException异常。

**051.AsyncRpcResult。**内部主要有一些CompletableFuture的api值得学习、isDone、get(timeout, unit)、complete(xx)、whenComplete(BiConsumer)、thenApply(Function)、completeExceptionally等。

**052.ListenableFilter**。杂类，没啥用。学到一个ConcurrentMap父接口，子类ConcurrentHashMap，比如赋值ConcurrentMap<Invocation, Listener> listeners = new ConcurrentHashMap<>();

**053.InvokeMode。**枚举类，三种调用方式SYNC, ASYNC, FUTURE;

**054.FutureContext。**和RpcContext类似，核心都是利用InternalThreadLocal。主要是这么调用FutureContext.getContext().setFuture(CompletableFuture.completedFuture("future from thread1"));且主要被RpcContext使用。

**055.Constants。**接口的属性、方法不需要public、static前缀，因为自动会加上

**056.AttachmentsAdapter。**其有一个内部类ObjectToStringMap继承了HashMap，其ObjectToStringMap构造方法主要把value转化为String类型。以及ObjectToStringMap是public static修饰的，外部如果要new这个内部类实例的话，这样：new AttachmentsAdapter.ObjectToStringMap(attachments);

**057.AsyncContextImpl。**AsyncContextImpl->start->write/getInternalFuture->stop大概是这个顺序，不知为何里面用started和stopped两个原子类来标记启动、开始。

**058.Rpcutils。** 主要为RpcInvocation服务。

（1）attachInvocationIdIfAsync方法是幂等的，用以给(rpc)Invocation添加id，id是用原子类自增实现唯一性，从0计数，内部根据参数url、invocation是否含有async标记。

（2）RpcUtils.getReturnType(inv)，明白了inv和invoker的关系，inv的含有invoker引用、方法名、参数等信息，这些信息表明这个inv是想要调用invoker的url表示的接口的对应方法，通过invoker.invoke(inv)触发。

（3）RpcUtils.getReturnTypes(inv)获取方法返回类型以及带泛型的返回类型，学到一些反射相关的api泛型method.getReturnType();、method.getGenericReturnType();，isAssignableFrom、instanceof ParameterizedType、((ParameterizedType) genericReturnType).getActualTypeArguments()[0]、(Class<?>) ((ParameterizedType) actualArgType).getRawType()。

（4）测试方法：@ParameterizedTest+@CsvSource({x,y,z})，xyz循环多次作为入参

（5）RPCInvocation指定的方法名指定为$invoke，具体的方法名称、参数类型数组、参数值数组 在parameterTypes、arguments属性给出。比如 parameterTypes = new Class<?>[]{String.class, String[].class, Object[].class},arguments=new Object[]{"method", new String[]{}, new Object[]{"hello", "dubbo", 520}}

**059.MockProtocol。**AbstractProtocol的子类，主要是一个mock程序，其实现了protocolBindingRefer模板方法，内部return new MockInvoker<>(url, type);

**060.MockInvoker。** 内有方法parseMockValue、new MockInvoker(url, String.class);、mockInvoker.invoke(invocation)常见方法，主要是做mock测试的，最核心的就是Result invoke(Invocation invocation)方法，会从url获取mock的值（mock值三种情况: return xx 、 throw xx 、接口impl全限定名）、以及有normalizeMock标准化mock值。

**061.AsyncToSyncInvoker。**invoker.invoke(invocation);之后如果发现是同步的，那么会get()阻塞直接结果返回方法才返回。

**062.DubboInvoker。**AbstractInvoker的具体子类，主要在DubboProtocol得到构造。最关键的就是doInvoke模板方法的实现，给inv填充一些属性后，根据url+inv判断是否是**onway**请求，向ExchangeClient发送send或者request方法调用，最后结果都是以AsyncRpcResult返回；选择ExchangeClient的时候是从构造函数传进来的clients数组**轮询**选择的。其isAvailable方法判断拥有的clients数组，有一个可用（连接状态+没有可读属性）就代表可用。其destroy方法利用**双重检查（lock锁）防止重复关闭**。

**064.DubboProtocol。**ProxyFactory、Protocol、Invoker关系:protocol.export(proxy.getInvoker

**065.DubboProtocolServer。** ProtocolServer子类，存放RemotingServer和address的。

**066.ServerStatusChecker。**检查DubboProtocol实例持有的RemotingServer，看是否isBound，然后设置level和sb填充到Status。

**067.Status。** 给StatusChecker用的，三个核心属性level+message+desc。

**068.StatusChecker。**spi接口，唯一方法Status check();。

**069.SimpleDataStore。**DataStore spi接口的唯一实现。主要在内存级别维护了一个双key的map，<component name or id, <data-name, data-value>>

**070.ThreadPoolStatusChecker。**主要是检查线程池的一些状态，几个api学习下：getMaximumPoolSize、getCorePoolSize、getLargestPoolSize、getActiveCount、getTaskCount。maximumPoolSize:是一个静态变量,在变量初始化的时候,有构造函数指定.largestPoolSize: 是一个动态变量,是记录Poll曾经达到的最高值,也就是 largestPoolSize<= maximumPoolSize.

**071.Codec2。**SPI接口，codec 编码-解码器“Coder-Decoder”的缩写，接口两个方法encode和decode。

**072.CodecAdapter。**该类实现Codec2接口，实现了标准的适配器模式，实现目标接口，含有被适配器对象，主要是兼容旧版本。旧版本的Codec接口（没有2）的encode和decode的参数没有buffer，而是io输入输出流，也说明buffer既可以作为输入也可以作为输出，这点比io流更好。

**073.UnsafeByteArrayOutputStream。** extends OutputStream，核心属性：字节数组和填充的字节数，支持自动扩容。注意几个api：System.arraycopy(b, off, mBuffer, mCount, len) 、 new String(mBuffer, 0, mCount, charset);指定字符编码、ByteBuffer.wrap(mBuffer, 0, mCount);将字节数组转化为ByteBuffer。

**074.AbstractCodec。**Codec的抽象实现类，checkPayload检查size是否超过payLoad，isClientSide判断是否是客户端（从channel的属性获取、获取不到的话利用url和channel.getRemoteAddress比较，这里得知channel有localAddress和remoteAddress）

**075.SerializableClassRegistry、SerializationOptimize。**序列化类注册容器和序列化优化器，没啥调用点。

**076.FastJsonObjectInput。**核心属性BufferedReader reader，如果传入的是字节流也会转化成字符流。核心点是利用String json = readLine();然后根据fastJson api转化为对应类型的数据，如 JSON.parse(json) 、 JSON.parseObject(json, cls) 等。

**078.FastJsonObjectOutput。**核心属性PrintWriter writer ，如果传入的是字节流也会转化成字符流。核心点，写入字节数组的时候利用writer.println(new String(b, off, len))，其他对象利用fastJson的api：JSONSerializer serializer = new JSONSerializer(new SerializeWriter())、serializer.write(obj)、out.writeTo(writer)、writer.println() + writer.flush()。

**079.FastJsonSerialization。**implements Serialization 这个spi接口（spi默认的hession2序列化），里面主要提供getContentTypeId、getContentType方法，还有serialize+deserialize方法，这两个是核心 方法，主要是分别根据传入的OutputStream、InputStream 构造new FastJsonObjectOutput(output) 或者 new FastJsonObjectInput(input)返回。

**080.FastJsonObjectInputTest。**new ByteArrayInputStream("true".getBytes())、new StringReader("false")都是输入流，可以传递给FastJsonObjectInput。这个api  Byte.parseByte("123")) 转化一个Byte。[{},{}] 也是json数据，是一个json数组，new StringReader("[{\"name\":\"John\",\"age\":30},{\"name\":\"Born\",\"age\":24}]")。注意下 (T) JSON.parseObject(json, type)。

**081.FastJsonObjectOutputTest**。我们事先准备好byteos，然后通过fastJsonObjectOutput写入到byteos，byteos里面就有各种数据了，然后new FastJsonObjectInput(new ByteArrayInputStream(byteos.toByteArray())) 实现和FastJsonObjectInput的交互。还能writeObject(new Image(xx))写入图片/文件流。

**082.CodecSupport。**通过static{}静态块，上来就加载了所有的序列化实例并缓存到容器中。且kv互换整两个容器这种思想很常见，方便双向查找。

**082.TransportCodec。**AbstractCodec的子类。这个被标记为废弃了，子类TelnetCodec和ExchangeCodec两者都覆盖在这个类中声明的所有方法。学习到的东西有：（1）encode方法中，用new ChannelBufferOutputStream(buffer) 对channelBuffer进行了包装，然后获取序列化器(ObjectOutput)进行序列化（因为序列化器，比如FastJsonObjectOutput，必须要输出流参数）。（2）ChannelBufferOutputStream本身继承outputStream，重写了write相关方法，把实际的操作还是转向了buffer。（3）Cleanable类似一种标记接口，如果 objectOutput instanceof Cleanable，那么((Cleanable) objectOutput).cleanup();

**083.TelnetCodecTest。** 

（1）在构造AbstractMockChannel的时候，给InetSocketAddress localAddress、remoteAddress属性赋值的时候，把url的参数对取出后，String类型的"ip:port"根据:拆分后，可以 new InetSocketAddress(host, port) 然后赋值给那两个属性 主要学习了这个api。

（2）objectToByte方法，将一个Object类型对象转化为字节数组，如果是String类型，直接getBytes，如果是byte[]类型，直接强转，如果是对象类型，利用bo = new ByteArrayOutputStream() ; oos = new ObjectOutputStream(bo)，然后oo.writeObject(obj)，然后byte[] bytes = bo.toByteArray()即可。

（3）将Object类型的数据（利用上面的objectToByte方法）转化为字节数组后，和Enter字节数组拼接，代码如下：

```
byte[] ret = new byte[in1.length + in2.length];
System.arraycopy(in1, 0, ret, 0, in1.length);
System.arraycopy(in2, 0, ret, in1.length, in2.length);
```

**084.TelnetCodec。**

（1）getCharset方法学习到 Charset charset =  Charset.forName((String) attribute)的api运用，这里拿到字符集 实例，用以编解码，但是实际跟了下，只是在字节数组转化为字符串的时候填充了字符集名称，如：new String(copy, 0, index, charset.name())。

（2）endsWith(byte[] message, byte[] command) 判断一个字节数组是否以另一个结尾，内部逻辑学习下大概是这样的：

```
int offset = message.length - command.length;
for (int i = command.length - 1; i >= 0; i--) {
    if (message[offset + i] != command[i]) {
        return false;
    }
}
```

（3）EXIT、ENTER两个list。list存放了字节数组，对于EXIT来说，如果msg（decode的参数，字节数组）和EXIT的其一项完全相等，表示要关闭channel，对于ENTER来说，任何服务端的msg都要以ENTER的其一项为结尾。

（4）decode是最核心的方法。主要对msg进行解码，这是方法名之所以叫Telnetxxx的原因，原因在于UP、DOWN、ENTER、EXIT的字节数组List设计，以及channel的history存储设计（配合UP、DOWN）。

**085.ExchangeCodecTest。**

（1）getRequestBytes。头+体是怎么组合的。首先待写入数据为Object obj，利用序列化器进行序列化写到os（比如是UnsafeByteArrayOutputStream），然后拿到bos.toByteArray()身体的字节数组，将身体的长度值 int类型转化为四个字节的字节数组，然后写到头部的12~15位置，然后join两个字节数组（参考TelnetCodecTest的（3）），这样一个完整的按照协议的数据格式就组装好了。

（2）header的设计，前两个为魔数，必须有魔数这是协议设计的范式。Header长度为16个字节，除了前两个模式，比如 00000010-response/oneway/hearbeat=true |20-stats=ok|id=0|length=0 

（3）header的第三个字节是标记，假设为0x8F = 1000 1111（最高位1表示request类型） ，1000 1111  & 0001 1111(SERIALIZATION_MASK) = 1111 （十进制15），而CodecSupport里面没有缓存id为15的序列化器，内部肯定抛异常。

（4）header的12~15为数据长度，比如1111，经过byte2Int转化之后返回16843009，checkPayload肯定抛异常。

（5）仅传header，且header的长度<16，直接return DecodeResult.NEED_MORE_INPUT。

（6）header+从header读出的身体大小 > 实际传给decode的buffer数据大小，return DecodeResult.NEED_MORE_INPUT;

（7）header的魔数放在2 3 位置，会进telnetCodec的decode方法。

（8）header的结构如下：

```
/**
 *
 *         byte 16
 *         0-1 magic code
 *         2 flag
     *         8 - 1-request/0-response
     *         7 - two way
     *         6 - heartbeat
     *         1-5 serialization id
 *         3 status
     *         20 ok
     *         90 error?
 *         4-11 id (long)
 *         12 -15 datalength
 */
```

**086.ExchangeCodec。**

（1）编解码器，支持对Request、response进行编解码，编码是把数据按照header+body的格式写入到ChannelBuffer，解码是从ChannelBuffer按照协议格式进行解码后返回该数据。

（2）头部的魔数占据两个字节，整体利用short MAGIC = (short) 0xdabb 表示，MAGIC_HIGH和MAGIC_LOW利用Bytes.short2bytes(MAGIC)[0/1]分别取第0和第1个字节。

（3）头部的标记+序列化id用1个字节表示。和rmq的类似。最高位0或1表示响应或者请求，6 7位如果为1表示FLAG_TWOWAY+FLAG_EVENT。

（4）encodeRequest方法。对请求数据进行编码，编码成规定的协议格式，主要是Header+Body。先把Header的16个字节该设置的设置好，多个标记位利用或操作，然后往参数buffer（本身是空白的，没内容）写入身体部分（实现移动写指针，空出16字节），再把写入的字节数填充到头部，然后移动写指针到空处开头，写入头，再还原写指针。

（5）decode方法。检查魔数、readable < HEADER_LENGTH、从头部读取数据长度len然后checkPayload、readable < len + HEADER_LENGTH不满足则返回（按道理一般是相等），然后从header取出标记、序列化id、status、requestId等数据，进行解码。

**087.Request。**static final AtomicLong INVOKE_ID = new AtomicLong(0);类所有实例共享，用于给每个Request实例生成mId。

**088.Response。**其id是从Request对应的，从Header取出来的赋值到response。

**089.Endpoint。**端点，Channel、Client、RemotingServer三类端点（Endpoint接口的子接口）。主要包含getUrl、getChannelHandler、getLocalAddress、close。

**090.Channel。**EndPoint的子接口，getRemoteAddress+isConnected+Attribute相关方法。

**091.AbstractChannel。**extends AbstractPeer implements Channel。其send方法是一个共有方法，子类（NettyChannel）会重写，并在第一步super.send(x,x)调用，进行前置检查。toString方法输出getLocalAddress() + " -> " + getRemoteAddress()，channel连接连接一般都要这两个信息。

**092.ChannelHandler。**主要是channel发生了一些事件，做出相应的回调处理。void connected(Channel channel)、disconnected(Channel channel)、 sent(Channel channel, Object message) 、received(Channel channel, Object message)、caught(Channel channel, Throwable exception)。

**093.ChannelHandlerDelegate。**ChannelHandler的子接口，只有一个ChannelHandler getHandler(); 一猜就知道实现类里面肯定含有别的ChannelHandler子类。有点代理模式的意思。

**094.AbstractChannelHandlerDelegate。**抽象类，ChannelHandlerDelegate的实现类。其构造方法传入ChannelHandler handler并赋值给自己的属性。实现了父接口的getHandler方法，如果满足handler instanceof ChannelHandlerDelegate则((ChannelHandlerDelegate) handler).getHandler()，即拥有的handler依然是委托类型的，那么调用其getHandler，直到找到目标Handler，深层委托！

**095.MultiMessageHandler。**AbstractChannelHandlerDelegate子类，其构造方法传入的为HeartbeatHandler，received方法发现msg是MultiMessage类型的，for循环调用handler.received(channel, obj);这里学到MultiMessage是Iterable接口的实现类，可以直接增强for循环。

**096.HeartbeatHandler。**AbstractChannelHandlerDelegate子类，其构造方法传入的为目标ChannelHandler。这个心跳处理器的作用就是在连接完成、关闭、读写等操作的时候向channel的属性中写入当前读、写时间戳，以及received方法，如果发现是心跳包（两种请求心跳包+响应心跳包，请求如果是twoWay的话会回发心跳），打印日志直接return，不会调用目标handler.received(channel, message);

**097.NettyEventLoopFactory。**主要用来创建EventLoopGroup具体实例的，根据shouldEpoll来确定是否使用EpollEventLoopGroup、还是NioEventLoopGroup，且注意传入了线程个数和线程工厂，比如new EpollEventLoopGroup(threads, threadFactory)。shouldEpoll逻辑是configuration.getBoolean("netty.epoll.enable") = true + configuration.getString("os.name")="linux"。

**098.NettyServerHandler。**extends ChannelDuplexHandler，复用的、双向的出入站处理器，关注读写、连接、取消连接、关闭等事件，主要是作为服务端的childHandler的一员，在各种事件到来固有的两个步骤（1）NettyChannel.getOrAddChannel(ctx.channel(), url, handler) ，缓存到NettyChannel内部的一个Static修饰的即类持有/实例共享的CHANNEL_MAP变量中，同时还缓存到本类的channels map 属性中。如果是连接不活跃、空闲超时、抛异常会从CHANNEL_MAP移除（2）传递事件给通过构造函数传进来的handler，handler特指NettyServer，比如handler.connected(channel) ，connected是NettyServer父类AbstractServer的方法。

核心注意两个方法：channelRead + write。

**099.NettyChannel。** 类本身是final的，不允许被继承。主要是缓存netty的channel的。（1）几个重要属性：缓存对象为ConcurrentMap<Channel, NettyChannel> <u>CHANNEL_MAP</u>。入缓存的时候，如果channel.isActive的话，把nettyChannel的AtomicBoolean <u>active设置为true</u>，从缓存remove的nettyChannel.markActive(false)。Map<String, Object> <u>attributes</u> 属性存放一些kv，没有在netty的channel api来添加属性。Channel channel属性，即Netty的channel。（2）send方法核心操作为ChannelFuture future = channel.writeAndFlush(message) +  future.await( url.getTimeout) + Throwable cause = future.cause()。close方法主要是清空attributes + channel.close()。还有一些和attributes相关的方法。

**100.NettyBackedChannelBuffer。**见名知意，实现编解码的，作为childHandler的，利用netty提供的编解码MessageToByteEncoder和ByteToMessageDecoder，将其委托给codec（一般是dubboExchangeCodec）。几个关键属性：codec 、 encoder = new InternalEncoder()、decoder = new InternalDecoder() ，以InternalEncoder内部类为例，继承MessageToByteEncoder，encode方法内部最后调用codec.encode(channel, buffer, msg)用以编码。解码方法不知道为何是循环解码的？大概是和rmq的ByteBuffer很像，rmq是while(byteBuffer.hasRemaining()){}，这里是while(byteBuf.isReadable)。

**101.NettyServer。** extends AbstractServer 。

（1）doOpen模板方法，父类的构造方法再进行一些初始化操作之后会调用该方法，方法主要是标准的Netty服务端程序，bootstrap 、 bossGroup+workerGroup 这两个利用前面的NettyEventLoopFactory进行eventLoopGroup的创建（Epoll型还是NIO型），然后就是范式如下：

```
        bootstrap.group(bossGroup, workerGroup)
                .channel(NettyEventLoopFactory.serverSocketChannelClass())// 进去，epoll和nio
                .option(ChannelOption.SO_REUSEADDR, Boolean.TRUE)// 连接复用
                .childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<SocketChannel>()
        ChannelFuture channelFuture = bootstrap.bind(getBindAddress());
        channelFuture.syncUninterruptibly(); // Uninterruptibly 不间断地；连续地
        channel = channelFuture.channel();
```

这里我们补充下childHandler，主要是通过ChannelInitializer的initChannel(SocketChannel ch)方法，里面利用ch.pipeline()添加addLast各种handler，包括adapter.getDecoder()、adapter.getEncoder()、IdleStateHandler、NettyServerHandler。这里学习到空闲状态处理器检测到读写空闲超时后（从url取的空闲超时时间）会产生事件传递给NettyServerHandler，其有一个userEventTriggered方法会得到触发。注意顺序，入站顺序adapter.getDecoder - >  IdleStateHandler ->NettyServerHandler，出站顺序adapter.getEncoder ->IdleStateHandler -> NettyServerHandler。

（2）doClose方法比较简单：channel.close()（这里的channel是channelFuture.channel()返回的） + getChannels()遍历close + boss/workerGroup.shutdownGracefully().syncUninterruptibly();

（3）isBound方法。channel.isActive()。

**102.AbstractServer。**NettyServer父类，其构造方法参数url+handler，首先super把这两个传给父类AbstractPeer，然后从url获取数据构建localAddress + bindAddress（使用InetSocketAddress(ip,port)），然后获取accepts和idleTimeout。学习到了一个最大连接数，如果NettyServerHandler监听到连接事件，会调用该类的connected方法，发现超过连接了，会把新来的channel关闭。idleTimeout主要是被子类NettyServer用作idleStatHandler的读写空闲超时的。还有会executor = executorRepository.createExecutorIfAbsent(url)，创建线程池，别处通过DefaultExecutorResposity的getExecutor能拿到线程池。

注意到一个bindIp=0.0.0.0，如果满足条件：anyhost = true或者host为127.0.0.1、localhost、0.0.0.0等，就设置 ip 为 0.0.0.0，表示服务端的socket不选择ip，一个机器可以多ip/多宿的，这样其他客户端可以指定多个服务端ip，都能接收到

**103.HeaderExchanger。**Exchanger接口的唯一实现。代码很简单，如下，connect和bind分别用户客户端和服务端。

```java
public ExchangeClient connect(URL url, ExchangeHandler handler) throws RemotingException {
    return new HeaderExchangeClient(Transporters.connect(url, new DecodeHandler(new HeaderExchangeHandler(handler))), true);
}

public ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        return new HeaderExchangeServer(Transporters.bind(url, new DecodeHandler(new HeaderExchangeHandler(handler))));
    }
```

**104.Exchanger。**使用了MEP（Message Exchange Pattern ）思想。百度一处比较好理解的如下：

*SOA*中的*MEP*和*JavaEE*中的*JMS*类似，当然了就应该是类似的，因为都是关于消息方面的。一个是对系统架构当中消息的解决思路，一个是针对*Java*平台中的消息的具体解决办法（严格说不是具体的，只是提供了接口而已）或者说和上面分析*BPM*与*Jpbm*一样，前者是后者的特例，所以后者有的特征前者大部分都有。就像*JMS*规范中描述的那样消息无非也就是请求与应答，这里有两种基本的模式其实很简单啦。<u>第一种就是请求*/*应答，第二种是请求不应答（单程）</u>。如果两次单程消息的传递可以类似的成为一次请求*/*应答模式。但是他们还是有区别的，单程的消息传递是没有阻塞的，发送了就不需要发送者再去关系消息，而请求*/*应答是阻塞的，只要接受者没有应答，那么发送者就认为消息没有发送成功可能重新发送或者采取别的措施保证消息的安全到达。请求*/*应答的优势是面对的处理消息的对象是不变的。哪种更好没有定论需要根据实际业务去选择。

基于上面的两个基础的*MEP*可以演化出比较复杂的*MEP*。请求*/*回调，指的是类似*Ajax*的一种一步请求的应答模式，发送请求后表面上就不在关心消息但是实际上当应答到来后会采取相应的措施，即“回调”。发布*/*订阅，值得是类似*RSS*的模式发布者负责把消息推送给已经订阅的用户，在*SOA*中接受消息的可能是一个服务或者一个将多个服务组合到一起的系统。

在*MEP*中还有很多细节，比如对于错误消息的处理，比如各个服务模块和*ESB*之间需要制定协议，那么对于多层次协议的*MEP*的延迟处理等等。

**105.Exchangers。**门面模式。提供bind和connect方法，参数都是url+ChannelHandler，分别返回ExchangeServer和ExchangeClient。bind和connect的逻辑分别是getExchanger(url).bind(url, handler) 和 getExchanger(url).connect(url, handler)，getExchanger根据spi机制默认获取的是HeaderExchanger，走他的bind和connect。static{}块调用Version类的checkDuplicate，门面肯定一般都是一份，所以不应该有多个重复的类（比如引入的依赖、jar有了重复的）

**106.Transporters。**门面/外观模式。方便用户使用复杂的子系统，不和子系统直接交互。其bind、connect交给默认的NettyTransporter。

**107.Transporter。**传输层。有bind和connect方法，分别返回RemotingServer和Client。主要是Transporters使用。

**108.NettyTransporter。**Transporter的默认扩展类， bind和connect方法分别  return new NettyServer(url, handler) 和 return new NettyClient(url, handler)。

**109.AbstractClient。**extends AbstractEndpoint implements Client，和AbstractServer对称（extends AbstractEndpoint implements RemotingServer）。内容大体和AbstractServer类似。构造方法把url和handler传给父类后，初始化线程池、doOpen+doConnect，两个模板方法子类去实现。connect、reconnect、disconnect都使用了connectLock锁。reconnect实际就是disconnect+connect。其send方法如果判断needReconnect && !isConnected()为true，则会先connect（needReconnect是url获取到的）。

**110.NettyClient。**extends AbstractClient。大体结构和NettyServer很像。doOpen方法主要是NettyClient的范式，注意的地方就是.option(ChannelOption.SO_KEEPALIVE, true)说明一般是客户端指定长链接，第二个点就是bootstrap.group(NIO_EVENT_LOOP_GROUP)客户端仅指定一个workerGroup即可。doConnect方法学到的api：bootstrap.connect+future.awaitUninterruptibly+future.isSuccess()+future.channel()+future.cause()。在拿到 future.channel()之后做两个操作Close old channel+copy reference，而channel注意是volatile修饰的。

**111.NettyClientHandler。**extends ChannelDuplexHandler，和NettyServerHandler很像。channelActive、channelInactive、channelRead三个方法都是NettyChannel.getOrAddChannel(ctx.channel(), url, handler)/removeChannel(ctx.channel()) + handler.xxx的方法调用。userEventTriggered方法内部如果判断IdleStateEvent，那么会发送心跳包给服务端。write方法有点特殊处理，在super.write(ctx, msg, promise);之后正常按道理应该和NettyServerHandler一样是直接handler.sent(channel, msg)，但是没有，而是promise.addListener（逻辑如下），原因是 ： 添加监听器以确保out bound事件是正确的。如果我们的out bound事件有错误(在大多数情况下编码器失败)，我们需要直接返回请求，而不是阻塞调用进程。

```java
promise.addListener(future -> {
    if (future.isSuccess()) {
        // if our future is success, mark the future to sent.
        handler.sent(channel, msg);
        return;
    }
    Throwable t = future.cause();
    if (t != null && isRequest) {
        Request request = (Request) msg;
        Response response = buildErrorResponse(request, t);
        handler.received(channel, response);
    }
});
```



**112.cs交互过程。**

（1）服务端角度：当服务端bind绑定、监听之后，客户端发起connect，此时触发链路如下：

```
NettyServerHandler.channelActive------->AbstractServer.connected------->AsbtractPeer.connected------->MultiMessageHandler/AbstractChannelHandlerDelegate.connected------->HeartbeatHandler.connected------->AllChannelHandler.connected------->executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.CONNECTED))------->ChannelEventRunnable的run方法内部------->DecodeHandler/AbstractChannelHandlerDelegate.connected------->HeaderExchangerHandler.connected------->HeartbeatHandlerTest$TestHeartbeatHandler.connected
```

由于testServerHeartbeat的HEARTBEAT_KEY = 1000，这个会被用以计算服务端的IdleStatHandler的读写空闲超时时间（默认是HEARTBEAT_KEY*3），如果客户端一直没有读写，此时触发链路如下：

```
NettyServerHandler.userEventTriggered------->channel.close------->channelInactive------->链路和前面的类似最后------->HeartbeatHandlerTest$TestHeartbeatHandler.disConnected
```

（2）客户端角度：当客户端调用bootstrap.conect之后，此时触发链路如下：

```
NettyClient.channelActive------>[[这部分逻辑和前面一样 AbstractServer.connected------->AsbtractPeer.connected------->AllChannelHandler.connected------->executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.CONNECTED))------->ChannelEventRunnable的run方法内部-------DecodeHandler/AbstractChannelHandlerDelegate.connected------->HeaderExchangerHandler.connected------- ]]]
ExchangeHandlerDispatcher.connected----->ChannelHandlerDispatcher.connected--->遍历cow的ChannelHandler.connect
```

由于testServerHeartbeat的HEARTBEAT_KEY = 1000，这个会被用以计算客户端的IdleStatHandler的读空闲时间（默认是HEARTBEAT_KEY），如果客户端一直没有读服务端数据，此时触发链路如下：

```
userEventTriggered------>channel.send(req)------->AbstractPeer.send--------->NettyChannel.send------>channel.writeAndFlush(message)--------->NettyCodecAdapter的InternalEncoder.encode----->telnetCodec.encode----->NettyCodecAdapter的InternalDecoder.deocde -------->NettyServerHanlder.read
```

（3）心跳机制，cs是通过各自的IdleStateHandler来实现的。c端配置**读空闲**最大时间为1s，那么这1s内如果没有从服务器读取数据的话，就会触发NettyClient的userEventTrigger方法，内部判断如果是空闲超时时间，则会channel.send 发数据。s端会给自己的IdleStateHandler配置**读写空闲的**最大时间，到达最大空闲时间前收到这个数据的话，则会重置，如果一直没有收到，NettyServerHandler内部则会channel.close关闭和客户端的连接。之所以c端仅配置读空闲，是因为本身正常send数据的话，s端收到也会重置。

**113.ChannelEventRunnable。**见名知意，一个任务，主要在AllChannelHandler的事件监听方法触发的时候，方法内部获取线程池进行构建并submit该任务实例。ChannelEventRunnablerun方法主要是判断事件类型，调用持有handler的对应方法，handler为DecodeHandler。

**114.AllChannelHandler。**该类主要是包装DecodeHandler实例，保存到父类的WrappedChannelHandler的属性。AllChannelHandler在接收到connected、disconnected、received、caught事件后/方法触发后，会获取在NettyServer的doOpen方法实现创建好的线程池，进行提交任务，即上面的ChannelEventRunnable。

**115.HeartbeatHandlerTest。**这个程序非常重要！主要理解cs两端的connected和disConnect过程，以及心跳逻辑，遗憾的是没有读写。

**116.PerformanceServerTest**。

**117.ChanelHandlerTest。**mvn clean test -Dtest=*PerformanceClientTest -Dserver=10.20.153.187:9911 执行test程序，server=xxx指定系统属性。一般业务的最终Handler就是 ExchangeHandler 实例，不过一般是通过 ExchangeHandlerAdapter 实现，用以传给Exchanges.connect/bind。

**118.PerformanceClientCloseTest。**没啥的，主要是  线程数* 每个线程执行的次数 发起这么多次客户端连接，连接异常的话打输出信息等然后根据url的onerror值进行System.exit(-1) 还是break、还是sleep一会继续下次connect。以及学习到hang住一个测试程序的方式用的是<u>synchronized</u>(PerformanceClientCloseTest.class){<u>while</u>(true){PerformanceClientCloseTest.class.<u>wait</u>()}}

**119.PerformanceClientFixedTest。**

**120.PerformanceClientTest。**

**121.HeaderExchangeClient。**implements ExchangeClient，主要是Exchanges.connect(url)返回的实例。内部主要有client和ExchangeChannel属性，分别是NettyClient和HeaderExchangeChannel，一些读写等操作交给channel去处理。以及根据传给构造函数的startTimer t/f值决定是否开启调度任务。startReconnectTask+startHeartBeatTask两个任务。

**122.ReconnectTimerTask。**AbstractTimerTask子类，将ChannelProvider和heartbeatTimeoutTick传给父类，分别用以获得HeaderExchangeClient和周期任务的重复间隔，重点是doTask，两种情况会重连：（1）channel.isConnected()。（2lastRead != null && now - lastRead > idleTimeout）。重连会调用((Client) channel).reconnect() ---> AbstractClient.reconnect。

**123.AbstractTimerTask。**implements TimerTask，其run方法就是遍历所有channel，执行doTask(channel)方法，doTask实现的子类有三个。很惯用思想，遍历之后reput，reput方法内部timer.newTimeout(timeout.task(), tick, TimeUnit.MILLISECONDS) 再次延时调度任务，形成周期运行。

**124.CloseTimerTask。**给HeaderExchangeServer使用的，主要是检查上次读、写（代码里面写作ping、pong）是否超过idleTimeout。  lastRead != null && now - lastRead > idleTimeout   ||    lastWrite != null && now - lastWrite > idleTimeout。

**125.HeartbeatTimerTask。**doTask方法，如果(lastRead != null && now() - lastRead > heartbeat) **||** (lastWrite != null && now() - lastWrite > heartbeat)，构造Request，然后channel.send(req)。

**125.HeartBeatTaskTest。**

**126.HeaderExchangeChannel。**作为NettyChannel的属性，key为HeaderExchangeChannel.class.getName() + ".CHANNEL"，value为该类实例。主要被HeaderExchangeHandler调用。注意send和request方法区别，oneWay一个是towWay的，towWay以DefaultFuture方式返回

**127.HeaderExchangeChannelTest。**主要是对HeaderExchangeChannel类的方法进行测试，主要是getOrAddChannel、send、request、close等方法。

**128.DefaultFuture。**和rmq的异步rpc模型一样。发送请求前先把请求数据等相关信息存到内存map，收到响应数据后根据id取出future，然后future.complete(xx)填充数据。不过rmq不是用的CompletableFuture。并且future带有超时，主要是通过TimerTask，在到达延迟点的时候检测一下future.isDone。还有executor不说了。还有一个sent时间戳值，用以标记这个是Request请求已经发过去了！但是因为等待服务器的数据发生了超时，如果没有设置过sent表示是Request请求还没有发过去。

**129.DefaultFutureTest。**学到java8的日期函数。测试类主要测试timeoutSend和timeoutNotSend，分别输出不同的信息。

**130.HeaderExchangeHandler。**非常关键的一个handler，主要是用以channel、request、exHandler之间的交互逻辑。构造方法主要包装业务handler，即exHandler，主要包含connected、disconnected、sent、received、caught方法。关键是received，判断msg的类型进不同的逻辑，msg三种类型：request、response、string。如果是Request且为towWay的，那么会调用exHandler的reply方法获取future，然后future.whenComplete，同步等待结果completed，然后内部channel.send(res);发给对端。如果是Reponse类型，那么找到DefaultFuture，对结果进行complete。

**131.HeaderExchangeHandlerTest。**主要测试了received方法，即处理channel的Request的。所有的test范式为：mock MockedChannel 提供send方法，mock MockedExchangeHandler提供reply方法，然后HeaderExchangeHandler hexhandler = new HeaderExchangeHandler(exhandler)，hexhandler.received(mchannel, request)。方法的todo是测试response。

**132.ExceedPayloadLimitException。**extends IOException，检查payload过大，抛出的异常。

**133.PayloadDropper。**主要是给NettyChannel使用的，在send抛异常的时候抛异常，并且带有msg，这里为了减少msg大小，将请求体和响应体置null。

**134.UrlUtils。**getIdleTimeout和getHeartbeat。一个获取HEARTBEAT_TIMEOUT_KEY、一个获取HEARTBEAT_KEY，且前者至少是后者的两倍。一个作为空闲，一个作为心跳。

**135.ExchangeHandlerAdapter。**虽然是用作实现业务handler的，但是不知道干么的，就实现了reply方法，return null。

**136.MultiMessage。**和MultiMessageHandler配合使用。该类主要implements Iterable，其含有List messages，在MultiMessageHandler的received方法判断msg是该类型的时候，会进行for循环遍历每个消息，然后调用handler.received(channel, obj);

**137.WrappedChannelHandler子类。**AllChannelHandler、ConnectionOrderedChannelHandler、DirectChannelHandler、ExecutionChannelHandler、MessageOnlyChannelHandler 这些由对应的Dispatcher构造出。这些Handler的主要作用就是发生事件之后是交给executor，还是在原有的io线程执行。executor从WrappedChannelHandler获取到。特别说一下ConnectionOrderedChannelHandler，这个主要构建了一个connectionExecutor，专门处理connected、disConnected事件，且该线程池的拒绝策略使用了自定义的AbortPolicyWithReport😆，并且checkQueueLength方法主要检测connectionExecutor.getQueue().size() > queuewarninglimit会打日志。

**138.WrappedChannelHandler。**sendFeedback、getPreferredExecutorService、getSharedExecutorService。

**139.ExchangeHandler。**业务Handler要实现该接口。主要提供了CompletableFuture<Object> reply(ExchangeChannel channel, Object request)

**140.ExchangeClient。**

**141.ExchangeChannel。**

**142.HeaderExchangeServer。**主要包装NettyServer。

**143.IdleSensible。**表示实现(服务器和客户端)是否有能力感知和处理空闲连接。如果服务器有能力处理空闲连接，它应该在它发生时关闭连接，如果客户端有能力处理空闲连接，它应该发送心跳到服务器。

**144.NettyTransporterTest。**主要测试 new NettyTransporter().bind(url, new ChannelHandlerAdapter() 、 new NettyTransporter().bind(url, new ChannelHandlerAdapter()。第二个测试程序结合CountDownLatch，当client connect之后，server的connected触发，然后lock.countDown()。

**145.NettyClientToServerTest、ClientToServerTest。**通过Exchanges.bind和connect进行cs连接。注意传给bind的是 Replier<World>实例，内部会用ExchangeHandlerAdapater包装，然后client.request(new World("world"))，发请求，Replier的reply就会在服务端得到调用。然后回写数据给client，client的AllChannelHandler内部会构建received事件的相关任务，然后给DefaultFuture进行Complete。

**146.ReferenceCountExchangeClient。**ReferenceCountExchangeClient 内部定义了一个引用计数变量 referenceCount，每当该对象被引用一次 referenceCount 都会进行自增。每当 close 方法被调用时，referenceCount 进行自减。ReferenceCountExchangeClient 内部仅实现了一个引用计数的功能，其他方法并无复杂逻辑，均是直接调用被装饰对象的相关方法。注意close的时进行了一个replaceWithLazyClient。

**147.ExchangeHandlerDispatcher。**

```
这个是在cs没有指定 ExchangeHandler 的时候的默认支持的实例，该类主要是含有  ReplierDispatcher 和 ChannelHandlerDispatcher，
// 用以将ExchangeHandler接口的reply和channel事件分开，分别交给这两个属性，后者相当于监听器，可以指定多个，前面的一般指定一个即可，因为回复肯定一个人回复就行了。
```

**148。Serialization。**

```
ExchangeHandlerDispatcher、ReplierDispatcher、ChannelHandlerDispatcher
```

**149.GroupServiceKeyCache。**serviceKey的多级结构，ConcurrentMap<serviceName, ConcurrentMap<serviceVersion, ConcurrentMap<port, String>>>  还有一个Group，搞了多层嵌套的缓存 - - 。如果map层级过多，可以单组一个类，比如GroupServiceKeyCache，不然的话serviceGroup也会作为map的key。

150.AbstractProxyFactory。

**零散：Bytes**。

（1）int2bytes。将int转化为四个字节的字节数组，原理如下：

```java
public static void int2bytes(int v, byte[] b, int off) {
        b[off + 3] = (byte) v;
        b[off + 2] = (byte) (v >>> 8);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 0] = (byte) (v >>> 24);
    }
```

（2）bytes2int。

```java
public static int bytes2int(byte[] b, int off) {
    return ((b[off + 3] & 0xFF) << 0) +
            ((b[off + 2] & 0xFF) << 8) +
            ((b[off + 1] & 0xFF) << 16) +
            ((b[off + 0]) << 24);
}
```

**零散：DynamicChannelBuffer**。 利用抽象工厂模式创建Buffer，三种具体工厂都有自己INSTANCE单例，获取到具体的工厂后，创建具体产品：buffer = factory.getBuffer(estimatedLength); 不过buffer是赋值给ChannelBuffer这个抽象产品，具体产品有堆内、堆外等。

**零散。NetUtils。**getAvailablePort方法，如果传入的port<=0的话，直接ServerSocket ss = new ServerSocket() + ss.bind(null)然后ss.getLocalPortJ就能拿到可用端口值了，如果port>0那么循环（范围[port,65535]）进行new ServerSocket(port)如果成功，就返回port。

**零散。Version。**checkDuplicate
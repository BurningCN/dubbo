# myRPC

这里主要介绍API和Netty4模块。

1. Transporters提供bind和connect方法。两个方法内部逻辑 通过SPI获取自适应的Transporter扩展类实例，然后调用其bind和connect。这里一般是走NettyTransporter实例的逻辑。
2. NettyTransporter主要有bind和connect，两个方法参数都是传入URL和ChannelHandler，分别返回NettyServer和NettyClient。

### 服务端的bind

NettyServer的逻辑主要是创建 “服务提供者” 监听的服务器，用以处理客户端/消费者的请求。逻辑如下

1. 其构造方法调用了父类AbstractServer的同参构造方法，有一个关键的逻辑就是利用ChannelHandlers.wrap 对业务handler进行了包装，包装逻辑如：MultiMessageHandler ( HeartbeatHandler ( AllChannelHandler( handler ) )  ) ，当然handler本身为DecodeHandler( HeaderExchangerHandler( ExchangeHandler)))。

2. 父类从url获取了一些关键信息，比如bind.ip、bind.port用以构建server即将bind的InetSocketAddress、accepts用以控制server最大连接数、idleTimeout用以控制server的读写空闲超时时间（还有connectTimeout、codec、timeout三个信息，这三个主要是在父类AbstractEndpoint获取的。AbstractEndpoint是AbstractServer和AbstractClient共同的父类，用以封装一些公共逻辑，比如codec编解码器就可以公用一个，connectTimeout是控制client连接server的超时时长的，不应该放在AbstractEndpoint类里，应该搞到AbstractClient中）。

3. 然后调用doOpen方法，该方法是模板方法，AbstractServer子类给出自己的实现，这里就介绍NettyServer#doOpen，内部逻辑就是Netty创建服务端的范式:

   -  new ServerBootstrap

   -  serverBootstrap.group(bossGroup, workerGroup)。bossGroup线程池数1即可，线程名称NettyServerBoss，workerGroup的线程数从url获取iothreads参数的值或者默认的 Math.min(Runtime.getRuntime().availableProcessors() + 1, 32)，线程名称为NettyServerWorker。这里的reactor模型是主单线程--从多线程模式。

   - serverBootstrap.channel(nio/epoll-ServerScoketChannel)、option(ChannelOption.SO_REUSEADDR=true)、childOption(TCP_NODELAY=true)。

   - serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>{ 给出protected void initChannel(SocketChannel ch)的实现逻辑}) 内部逻辑为给socketChannel的pipeline添加各种入出站处理器。如下

     ```
     			ch.pipeline()
             .addLast("decoder", adapter.getDecoder()) 
             .addLast("encoder", adapter.getEncoder()) 
             .addLast("server-idle-handler", new IdleStateHandler(0, 0, idleTimeout, MILLISECONDS))
             .addLast("handler", nettyServerHandler); 
     ```

     注意一个顺序，编码顺序：nettyServerHandler  ->IdleStateHandler-> encoder，解码顺序：decoder->IdleStateHandler->nettyServerHandler。

   - channeFuture = bootstrap.bind(bindAddress)+channelFuture.syncUninterruptibly+channelFuture.channel()，bind后，阻塞直到绑定成功，进行监听。  

### 客户端的Connect

NettyClient主要是向server发起连接。主要过程：调用父类构造方法+父类调用子类的两个模板方法doOpen+doConnect。

NettyClient构造方法的逻辑和NettyServer一样。父类AbstractClient的构造方法有一个从url获取send.reconnect参数值的逻辑，这个操作是在该类的send方法中如果发现if (needReconnect && !isConnected()) 的时候会进行connect操作，然后再发数据。

AbstractClient的构造函数内部会调用doOpen模板方法，NettyClient子类的实现逻辑如下，也即NettyClient的范式：

- new Bootstrap()
- bootstrap.group(workerGroup)指定一个group即可，因为客户端不需要监听，workerGroup和NettyServer的workerGroup一样。
- bootstrap.channel(nio/epoll-ScoketChannel)、bootstrap.option()指定SO_KEEPALIVE、TCP_NODELAY为true，ALLOCATOR指定为PooledByteBufAllocator.DEFAULT即池化的ByteBuf。
- bootstrap.childHandler(new ChannelInitializer<SocketChannel>{ 给出protected void initChannel(SocketChannel ch)的实现逻辑}) 内部逻辑为给socketChannel的pipeline添加各种入出站处理器。和server类似，就不说了。

AbstractClient紧接着会调用connect方法，内部用到了connectLock锁保证 isConnected + doConnect的多操作的原子性。再看doConnect模板方法的逻辑：

- bootstrap.connect(getConnectAddress())
- boolean ret =future.awaitUninterruptibly(getConnectTimeout(), MILLISECONDS) 
- if (ret && future.isSuccess()) Channel newChannel = future.channel() 。
- Close old channel + copy reference ，copy reference涉及到一个更新NettyChannel的逻辑，利用volatile保证可见性。

（注意 syncUninterruptibly、awaitUninterruptibly 两个的区别）

### 使用IdleStatHandler实现C/S心跳

我们注意到NettyServer和NettyClient的bootstrap程序都添加了IdleStatHandler.服务端的IdleStatHandler设置了**读写**空闲时间，客户端设置了**读**空闲时间，且前者一般是后者的三倍（两个时间可以向url传递heartbeat和heartbeat.timeout改变）。如果cs建立连接后，客户端没有任何动作，当超过**读**空闲时间的时候，IdleStatHandler的userEventTrigger方法得到触发，会传递（调用fireUserEventTriggered）给NettyClientHandler的userEventTriggered方法，其判断evt instanceof IdleStateEvent满足的话，会立马发心跳请求给服务端。服务端的NettyServerHandler就会收到该请求，并回复响应的心跳数据给客户端。双方的空闲时间得到重置。（编解码逻辑不在这里讲述）

如果客户端没有发送心跳，那么服务端会到达读写空闲之后，close该客户端所代表的的channel。

注：读、写、读写空闲的意思，比如client的读空闲表示这段时间没有收到/读到server的数据；比如server的读写空闲，表示这段时间没有收到client的读写请求。

### 三个Task：ReconnectTimerTask、CloseTimerTask、HeartBeatTimerTask

都继承自AbstractTimerTask父类，该类是一个Runnable，构造方法传入ChannelProvider、interval、threadName，并在内部启动线程。ChannelProvider是一个函数式接口，用以提供Channel list，interval是run方法的检查间隔，标准的while(!isStopped()){xx+休眠}，stop用volatile修饰。run方法内部会调用doTask目标方法，子类给出自己的实现。



- ReconnectTimerTask主要是在NettyClient连接建立完成之后启动的，其doTask的主要逻辑就是判断如果channel未连接或者channel上次的读时间戳超过了最大空闲时间（这个其实就是服务端的IdleStatHandler的读写空闲时间），那么就重新发起连接。

  读时间戳是记录在NettyChannel的，每次客户端读到服务端的数据的时候会经过HeartbeatHandler，就会记录当前时间戳。

- CloseTimerTask。这个是NettyServer启动后，如果该server  !canHandleIdle（表示服务端没能力处理空闲/感知空闲，其实就是服务端没有关闭一个超时的client channel的能力），那么就会启动该任务，该doTask主要会检查channel上次的读或写时间戳~now的时间差超过了idleTimeout（客户端IdleStatHandler的读空闲），那么就会close该客户端代表的client。

- HeartBeatTimerTask。在NettyClient连接建立完成之后启动的，如果该client !canHandleIdle(表示客户端没能力处理空闲/感知空闲，其实就是客户端无法在读空闲时间到达后自动发心跳的能力)，那么就会启动该任务，该doTask主要会检查channel上次的读或写时间戳~now的时间差超过了idleTimeout（客户端IdleStatHandler的读空闲），会立马发心跳请求给服务端。

### NettyServer的Close优雅关闭逻辑

- 在先前doOpen方法最后调用serverBootstrap.bind(bindAddress)返回的ChannelFuture，通过该ChannelFuture.getChannel拿到的channel，首先对其close（这个channel是代表服务端本身的，取消监听、unbind）
- 然后NettyServerHandler里面的channelActive触发的时候会保存每个channel到内存的容器中，且能被NettyServer拿到，进行循环遍历 close channel，然后清空容器。（这里的channel 是client->server的连接channel）
- bossGroup.shutdownGracefully().syncUninterruptibly();  +  workerGroup.shutdownGracefully().syncUninterruptibly();



### CS交互流程过程

从NettyClient.getChannel().request(xx)开始，这里的getChannel就是NettyChannel（在C doConnect S之后，对netty 的 Channel做了包装）。

request需要指定三个参数：Object data, int timeout, ExecutorService executor。第一个就是要发给S的数据，第二个是请求超时时间（request是twoWay的），第三个是给DefaultFuture使用的，作用是检测到超时之后使用线程池（如果有的话）调用notifyTimeout方法。后两个参数非必传，默认超时时间为1s。

request方法内部使用了一个非常牛逼而又常见的操作，针对twoWay的，构造Request之后，存储到DefaultFuture的内部全局容器中。



### 编解码逻辑-NettyCodecAdapter

- 见名知意，实现编解码的，作为NettyClient和NettyServer的childHandler的，核心是利用netty提供的编码器MessageToByteEncoder和解码器ByteToMessageDecoder，将其委托给codec。该codec一般是DefaultCountCodec/ExchangeCodec。

- 这里说下为何叫做Adapter/适配器，原因是因为构造MessageToByteEncoder和ByteToMessageDecoder并没有在后面接泛型，而是直接Object类型，具体的编解码实际传递给了Codec！！

- 内置的编码器：InternalEncoder extends MessageToByteEncoder，实现方法 encode(ChannelHandlerContext ctx, Object msg, ByteBuf out)，将msg编码到out。

- 内置的解码器InternalDecoder extends ByteToMessageDecoder，实现方法  decode(ChannelHandlerContext ctx, ByteBuf input, List<Object> out) ，将input解码填充到out。注意解码的过程是循环解码的while (buffer.readable()) 内部调用的byteBuf.isReadable()，这点和rmq的解码逻辑很相似（rmq是while(byteBuffer.hasRemaining()){}），解码都是循环解的！！因为tcp是粘包的！那么怎么确定某个tcp片段一个完整的包呢？具体在codec的逻辑里。注意循环内部有一个NEED_MORE_INPUT的逻辑判断很重要如下：

  如果数据量大的话，tcp会粘包，比如发送端发送10726字节的数据，那么接受的时候此时Message，第一个过来的分节一般是1024，其并不是一个完整的数据包（还差很多），而codec.decode内部就会有判断逻辑，如果不够的话，返回NEED_MORE_INPUT，进行如下处理，恢复读指针，然后等待tcp传入更多的数据，此时第二次（或者第三次...）进来decode方法的时候此时的input参数很可能就是10726了！！！详见测试程序testDataPackage（大数据量的）

### 编解码逻辑-Codec

一般情况下是这样的，NettyCodecAdapter的codec其实是DefaultCountCodec ，其包装了DefaultCodec，其包装了ExchangeCodec





# 各类介绍

```
Transporters
Transporter
NettyTransporter
NettyServer
AbstractServer
MultiMessageHandler
HeartbeatHandler
AllChannelHandler
DecodeHandler
HeaderExchangerHandler
NettyServerHandler
```
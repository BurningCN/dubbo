testServerHeartbeat方法server = Exchangers.bind(serverURL, handler)执行后的server如下： 

server = {HeaderExchangeServer@2948} // zhuyi 
 logger = {FailsafeLogger@2951} 
 server = {NettyServer@2087} // zhuyi 
  channels = {ConcurrentHashMap@2399}  size = 0
  bootstrap = {ServerBootstrap@2188} "ServerBootstrap(ServerBootstrapConfig(group: NioEventLoopGroup, channelFactory: ReflectiveChannelFactory(NioServerSocketChannel.class), options: {SO_REUSEADDR=true}, childGroup: NioEventLoopGroup, childOptions: {TCP_NODELAY=true, ALLOCATOR=PooledByteBufAllocator(directByDefault: true)}, childHandler: org.apache.dubbo.remoting.transport.netty4.NettyServer$1@73302995))"
  channel = {NioServerSocketChannel@2606} "[id: 0xb8ffea7d, L:/0:0:0:0:0:0:0:0:56780]"
  bossGroup = {NioEventLoopGroup@2376} 
  workerGroup = {NioEventLoopGroup@2382} 
  executor = {ThreadPoolExecutor@2665} "java.util.concurrent.ThreadPoolExecutor@3c9c0d96[Running, pool size = 0, active threads = 0, queued tasks = 0, completed tasks = 0]"
  localAddress = {InetSocketAddress@2157} "localhost/127.0.0.1:56780"
  bindAddress = {InetSocketAddress@2168} "/0.0.0.0:56780"
  accepts = 0
  idleTimeout = 600000
  executorRepository = {DefaultExecutorRepository@2147} 
  codec = {TelnetCodec@2133} 
  timeout = 1000
  connectTimeout = 3000
  handler = {MultiMessageHandler@2119} // zhuyi 
   handler = {HeartbeatHandler@2112}
    handler = {AllChannelHandler@2106} 
     handler = {DecodeHandler@1922} 
      handler = {HeaderExchangeHandler@2108} 
       handler = {HeartbeatHandlerTest$TestHeartbeatHandler@1647} 
        disconnectCount = 0
        connectCount = 0
        connectCountDownLatch = {CountDownLatch@1645} "java.util.concurrent.CountDownLatch@55141def[Count = 1]"
        disconnectCountDownLatch = {CountDownLatch@1646} "java.util.concurrent.CountDownLatch@55182842[Count = 1]"
        this$0 = {HeartbeatHandlerTest@1648} 
     url = {URL@1708} "telnet://localhost:56780?codec=exchange&exchanger=header&heartbeat=1000&transporter=netty4"
  url = {URL@2118} "telnet://localhost:56780?codec=exchange&exchanger=header&heartbeat=1000&threadname=DubboServerHandler-localhost:56780&transporter=netty4"
  closing = false
  closed = false
 closed = {AtomicBoolean@2952} "false"
 closeTimerTask = null
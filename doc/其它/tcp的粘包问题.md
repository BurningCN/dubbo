粘包问题。MultiThreadTest#testDubboMultiThreadInvoke程序，多线程发数据，可以发现NettyCodecAdapter的buffer是一直变大的！！！客户端所有线程发送的数据都是不断追加的到这个bytebufe的，ExchangCodec根据header存储的数据包len来决定是否可以读取一份完整的数据了，不行的话，放弃本次，可以读取的话，读取之后更新rdx，同时注意到wdx和capacity是一直增加的，因为还有别的线程再发数据。 粘包是tcp自带的机制，拆包需要我们自己去实现，即利用header写入身体的大小。（2）注意解码的时候，需要构造一个ChannelBufferInputStream
对象（(ExchangeCode的decode方法中decodeyBody前一句)），且注意传入的buffer和len很重要，这个buffer就是我们前面说的那个，一直追加的那个bytebuf，而len就是记录在header的身体大小，这样我们解码的时候就知道分寸了！且注意其类重写了isAvailable方法，利用endIndex - buffe.readable()，且一定要注意不能直接用buffer.isReadable()!!!!因为我们要获取的是这其中一个完整数据包是否可读（一个完整的数据包用一个ChannelBufferInputStream表示），不是整体粘包的tcp byteBuf是否可读。（3）我的testByFilterChain测试程序多线程发数据一直阻塞的原因，即服务端乱解码的原因就是因为我构造的ChannelBufferInputStream没有限定len，只传入了buffer，且吧buffer.eadableBytes整体作为len 了！！！！这就是问题所在，必须要指定len，即业务方规定的一个完整包的大小。也可以从ChannelBufferOutputStream发现，其并没有指定len，因为多线程写没关系，因为tcp本身就是粘包的



DefaultCountCodec的场景，对于客户端发送很多小数据量的数据，但是次数很多，为了批量处理，就利用了这个。比如发了11次，每次就占92个字节，那么tcp底层很大概率一次性就能写入，服务端也能一次性拿到，NettyCodecAdapter的byteBuf即rdx=0，wdx=1012，此时会调用DefaultCountCodec进行解码，其又会调用defaultCodec解码， 这样每次解码的数据先停冲到multiMessage（里面的list长度就是11），然后发现NEED_INPUT_MORE，再返回给NettyCodecAdapter，其out.add(multiMessage)，然后到下一个handler，即MultiMessageHandler....



现在有一个奇怪的问题，第一次92字节能正常解码，后面一下子发了11个，且不太对劲，ridx: 1012, widx: 1024, cap: 1024，这样的，1012很对就是11*92，DefaultCountCodec的MultiMessage的list也有11个元素，但是他一直处理循环，认为还有可读字节，但是可读字节为12 < HEader.length，等待更多数据传入，但是一直



  // 这里的点很重要，之前就是没有加如下这个条件，导致一直循环无法退出，即NettyCodeAdapter的逻辑递交给DefaultCountCodec
        // 如果rdx=0,wdx=1024,capacity=1024，比如客户端发了12个包，前11个包都是完整的，假设每个包92字节，那么wdx指针[0,1012]是前11个包的
        // 且是完整的，而[1013,1024]是第12个包的部分数据（还有剩余字节等待传输过来-粘包—）那么相当于给multiMessage填充了11个元素
        // 到达第12个的时候，此时进上面的NEED_MORE_INPUT分支，然后退出循环，然后走到最后的return multiMessage，然后返回Adapter，填充到out中
        // 这一次的批量数据就填充好了（以MultiMessage的方式）。然后Adapter判定是readable的（1012<1024）的，然后进入再次进入DefaultCountCodec
        // 然后直接进NEED_MORE_INPUT的分支，然后break，再进入下面的分支，返回NEED_MORE_INPUT，Adapter就能拿到这个信息，正常break，然后能
        // 正常处理第12个包的剩余数据、
        // 之前是因为没有这个判断逻辑（下面的），导致一直出现 NettyCodeAdapter ——> DefaultCountCodec->NettyCodeAdapter.....的死循环。使得
        // cpu繁忙，tcp无法/没空接受更多的数据。还有一点就是上面的do while条件是true，isReadable的逻辑在Adapter
       
package my.rpc;

import my.server.*;

import java.io.IOException;

/**
 * @author geyu
 * @date 2021/2/9 22:57
 */
public class DefaultCountCodec implements Codec2 {

    private DefaultCodec defaultCodec;

    public DefaultCountCodec(URL url) {
        defaultCodec = new DefaultCodec(url);
    }

    @Override
    public void encode(ChannelBuffer buffer, Object msg) throws IOException {
        defaultCodec.encode(buffer, msg);
    }

    // 该类主要做一个批量解码，好让下一个handler能批量处理（NettyClient/ServerHandler），减少频繁交互
    @Override
    public Object decode(ChannelBuffer buffer) throws Exception {
        int save = buffer.readerIndex();
        MultiMessage multiMessage = MultiMessage.create();
        do {
            Object obj = defaultCodec.decode(buffer);
            if (DecodeResult.NEED_MORE_INPUT == obj) {
                buffer.readerIndex(save);
                break;
            } else {
                multiMessage.addMessage(obj);
                logMessageLength(obj, buffer.readerIndex() - save);

                save = buffer.readerIndex();
            }
        } while (true);
        // 这里的点很重要，之前就是没有加如下这个条件，导致一直循环无法退出，即NettyCodeAdapter的逻辑递交给DefaultCountCodec
        // 如果rdx=0,wdx=1024,capacity=1024，比如客户端发了12个包，前11个包都是完整的，假设每个包92字节，那么wdx指针[0,1012]是前11个包的
        // 且是完整的，而[1013,1024]是第12个包的部分数据（还有剩余字节等待传输过来-粘包—）那么相当于给multiMessage填充了11个元素
        // 到达第12个的时候，此时进上面的NEED_MORE_INPUT分支，然后退出循环，然后走到最后的return multiMessage，然后返回Adapter，填充到out中
        // 这一次的批量数据就填充好了（以MultiMessage的方式）。然后Adapter判定是readable的（1012<1024）的，然后进入再次进入DefaultCountCodec
        // 然后直接进NEED_MORE_INPUT的分支，然后break，再进入下面的分支，返回NEED_MORE_INPUT，Adapter就能拿到这个信息，正常break，然后能
        // 正常处理第12个包的剩余数据、
        // 之前是因为没有这个判断逻辑（下面的），导致一直出现 NettyCodeAdapter ——> DefaultCountCodec->NettyCodeAdapter.....的死循环。使得
        // cpu繁忙，tcp无法/没空接受更多的数据。还有一点就是上面的do while条件是true，isReadable的逻辑在Adapter
        if (multiMessage.isEmpty()) {
            return DecodeResult.NEED_MORE_INPUT;
        }
        if (multiMessage.size() == 1) {
            multiMessage.get(0);
        }
        return multiMessage;
    }

    private void logMessageLength(Object result, int bytes) {
        if (bytes <= 0) {
            return;
        }
        if (result instanceof Request) {
            try {
                // DecodeableRpcInvocation
                ((RpcInvocation) ((Request) result).getData()).setAttachment("input", String.valueOf(bytes));
            } catch (Throwable e) {
                /* ignore */
            }
        } else if (result instanceof Response) {
            try {
                // DecodeableRpcResult
                ((AppResponse) ((Response) result).getResult()).setObjectAttachment("output", String.valueOf(bytes));
            } catch (Throwable e) {
                /* ignore */
            }
        }
    }
}

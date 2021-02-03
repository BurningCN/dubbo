package netty.server;


import java.io.IOException;

/**
 * @author geyu
 * @date 2021/2/3 11:11
 */
public abstract class AbstractCodec implements Codec2 {

    protected void checkPayLoad(InnerChannel channel, long size) throws IOException {
        int payload = Constants.DEFAULT_PAYLOAD;
        if (channel != null && channel.getUrl() != null) {
            payload = channel.getUrl().getPositiveParameter(Constants.PAYLOAD_KEY, payload);
        }
        if (size > payload) {
            throw new ExceedPayloadLimitException("Data length too large: " + size + ", max payload: " + payload + ", channel: " + channel);
        }
    }
}

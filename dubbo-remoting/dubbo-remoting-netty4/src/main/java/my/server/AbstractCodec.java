package my.server;


import java.io.IOException;

/**
 * @author geyu
 * @date 2021/2/3 11:11
 */
public abstract class AbstractCodec implements Codec2 {

    protected void checkPayLoad(long size) throws IOException {
        int payload = getUrl().getPositiveParameter(Constants.PAYLOAD_KEY, Constants.DEFAULT_PAYLOAD);
        if (size > payload) {
            throw new ExceedPayloadLimitException("Data length too large: " + size + ", max payload: " + payload);
        }
    }

    protected abstract URL getUrl();
}

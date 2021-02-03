package my.server;

import java.io.IOException;

/**
 * @author geyu
 * @date 2021/2/3 11:16
 */
public class ExceedPayloadLimitException extends IOException {

    public ExceedPayloadLimitException(String msg) {
        super(msg);
    }
}

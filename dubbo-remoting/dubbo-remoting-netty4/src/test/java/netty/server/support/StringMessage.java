package netty.server.support;

import java.io.Serializable;

/**
 * @author geyu
 * @date 2021/2/2 20:12
 */
public class StringMessage implements Serializable {
    private String msg;

    public StringMessage(String msg) {
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return msg;
    }
}

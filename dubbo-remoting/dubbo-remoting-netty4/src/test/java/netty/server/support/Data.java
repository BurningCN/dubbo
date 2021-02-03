package netty.server.support;

import java.io.Serializable;

/**
 * @author geyu
 * @date 2021/2/2 20:09
 */
public class Data implements Serializable {
    String data;

    public Data(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}

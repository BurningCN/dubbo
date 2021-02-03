package netty.server.support;

import java.io.Serializable;

/**
 * @author geyu
 * @date 2021/2/3 14:24
 */
public class MockResult implements Serializable {

    private static final long serialVersionUID = -4799382401774375973L;
    Object result;

    public MockResult(Object result) {
        this.result = result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Object getResult() {
        return result;
    }
}

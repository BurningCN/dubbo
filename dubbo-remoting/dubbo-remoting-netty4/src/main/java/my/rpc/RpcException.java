package my.rpc;

import javax.naming.LimitExceededException;

/**
 * @author geyu
 * @date 2021/2/5 17:15
 */
public class RpcException extends RuntimeException {
    public static final int UNKNOWN_EXCEPTION = 0;
    public static final int NETWORK_EXCEPTION = 1;
    public static final int TIMEOUT_EXCEPTION = 2;
    public static final int LIMIT_EXCEEDED_EXCEPTION = 7;
    private int code;

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcException(String message) {
        super(message);
    }

    public RpcException(Throwable cause) {
        super(cause);
    }

    public RpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    public RpcException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public boolean isLimitExceed() {
        // || 后面的记下
        return code == LIMIT_EXCEEDED_EXCEPTION || getCause() instanceof LimitExceededException;
    }
}

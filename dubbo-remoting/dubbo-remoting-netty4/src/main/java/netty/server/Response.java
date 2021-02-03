package netty.server;

import java.io.Serializable;

/**
 * @author geyu
 * @date 2021/1/28 19:44
 */
public class Response implements Serializable {
    public static final byte OK = 20;
    public static final byte CLIENT_TIMEOUT = 30;
    public static final byte SERVER_TIMEOUT = 31;
    public static final byte CLIENT_ERROR = 90;
    public static final byte SERVICE_ERROR = 70;
    public static final byte CHANNEL_INACTIVE = 35;
    public static final byte SERVER_THREADPOOL_EXHAUSTED_ERROR = 100;
    public static final byte BAD_REQUEST = 40;
    private long id;
    private String version;
    private boolean isEvent;
    private byte status;
    private Object result;
    private String errorMessage;

    public Response(long id) {
        this.id = id;
    }

    public Response(long id, String version) {
        this.id = id;
        this.version = version;
    }

    public boolean isHeartbeat() {
        return isEvent && result == CommonConstants.HEARTBEAT_EVENT;
    }

    public byte getStatus() {
        return status;
    }

    public long getId() {
        return id;
    }

    public Object getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setStatus(byte status) {
        this.status = status;
    }

    public void setEvent(boolean b) {
        this.isEvent = b;
    }

    public void setEvent(String event) {
        isEvent = true;
        this.result = event;
    }

    public boolean isEvent() {
        return isEvent;
    }

    public void setResult(Object result) {
        this.result = result;

    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "Response{" +
                "id=" + id +
                ", version='" + version + '\'' +
                ", isEvent=" + isEvent +
                ", status=" + status +
                ", result=" + safeToString(result) +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }

    private String safeToString(Object data) {
        String ret = null;
        try {
            if (data != null) {
                ret = data.toString();
            }
        } catch (Throwable e) {
            ret = "<Fail toString of " + data.getClass() + ", cause: " +
                    /*StringUtils.toString(e)*/ e.getMessage() + ">";// todo myRPC StringUtils.toString(e)
        }
        return ret;

    }
}

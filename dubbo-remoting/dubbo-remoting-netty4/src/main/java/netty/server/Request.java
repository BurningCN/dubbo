package netty.server;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author geyu
 * @date 2021/1/28 19:44
 */
public class Request {
    private boolean isTwoWay;
    private String version;
    private Object data;
    private long id;
    private boolean isEvent;
    private boolean broken = false;
    private static AtomicLong invokeId = new AtomicLong(0);

    public Request() {
        this.id = newId();

    }

    private long newId() {
        return invokeId.getAndIncrement();
    }

    public Request(long id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isTwoWay() {
        return isTwoWay;
    }

    public boolean isEvent() {
        return isEvent;
    }

    public void isEvent(boolean isEvent) {
        this.isEvent = isEvent;
    }

    public long getId() {
        return id;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setTwoWay(boolean isTwoWay) {
        this.isTwoWay = isTwoWay;
    }

    public boolean isHeartbeat() {
        return isEvent && data == CommonConstants.HEARTBEAT_EVENT;
    }

    public boolean isBroken() {
        return broken;
    }
    public void setEvent(String heartbeatEvent) {
        data = heartbeatEvent;
        isEvent = true;
    }

    public static Request makeHeartbeat(){
        Request request = new Request();
        request.setTwoWay(true);
        request.setVersion(Version.DEFAULT_VERSION);
        request.setEvent(CommonConstants.HEARTBEAT_EVENT);
        return request;
    }
}

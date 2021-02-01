package netty.server;

import org.omg.CORBA.TIMEOUT;

/**
 * @author gy821075
 * @date 2021/1/29 11:32
 */
public interface Constants {
    byte FAST_JSON_SERIALIZATION_ID = 6;
    String CONNECT_TIMEOUT = "connect.timeout";
    int DEFAULT_CONNECT_TIMEOUT = 3000;
    String HEARTBEAT = "heartbeat";
    int DEFAULT_HEARTBEAT = 60 * 1000;
    String HEARTBEAT_TIMEOUT_KEY = "heartbeat.timeout";
    String SENT = "sent";
    String SEND_TIMEOUT = "send.timeout";
    int DEFAULT_SEND_TIMEOUT = 1000;
    String SENT_KEY =  "sent";
    String CHANNEL_ATTRIBUTE_READONLY_KEY = "channel.readonly";
    String TIMEOUT_KEY = "timeout";
    int DEFAULT_TIMEOUT = 1000;
    //  ======== == === = == =
    String READONLY_EVENT = "R";

}

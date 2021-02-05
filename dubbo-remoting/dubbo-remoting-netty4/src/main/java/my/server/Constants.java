package my.server;

import org.omg.CORBA.TIMEOUT;

/**
 * @author gy821075
 * @date 2021/1/29 11:32
 */
public interface Constants {
    byte FAST_JSON_SERIALIZATION_ID = 6;
    byte NATIVE_HESSIAN_SERIALIZATION_ID = 10;
    String CONNECT_TIMEOUT = "connect.timeout";
    int DEFAULT_CONNECT_TIMEOUT = 3000;
    String HEARTBEAT_KEY = "heartbeat";
    int DEFAULT_HEARTBEAT = 60 * 1000;
    String HEARTBEAT_TIMEOUT_KEY = "heartbeat.timeout";
    String SENT = "sent";
    String SEND_TIMEOUT = "send.timeout";
    int DEFAULT_SEND_TIMEOUT = 1000;
    String SENT_KEY = "sent";
    String CHANNEL_ATTRIBUTE_READONLY_KEY = "channel.readonly";
    String TIMEOUT_KEY = "timeout";
    int DEFAULT_TIMEOUT = 1000;
    String RECONNECT_KEY = "reconnect";
    boolean DEFAULT_RECONNECT = true;
    int HEARTBEAT_CHECK_TICK = 3;
    int LEAST_HEARTBEAT_DURATION = 1000;
    String CHECK_KEY = "check";
    String PAYLOAD_KEY = "payload";
    int DEFAULT_PAYLOAD = 8 * 1024 * 1024;
    String VERSION_KEY = "version";
    String GROUP_KEY = "group";
    String IS_SERVER_KEY = "isServer";
    String ASYNC_KEY = "async";
    String ID_KEY = "id";
    String PROXY_KEY = "proxy";
    String INTERFACES = "interfaces";
    String INTERFACE_KEY = "interface";
    String CONNECTIONS_KEY = "connections";
    String SHARE_CONNECTIONS_KEY = "shareconnections";

    //  ======== == === = == =
    String READONLY_EVENT = "R";

    String TOKEN_KEY = "token";
    String PATH = "path";
}

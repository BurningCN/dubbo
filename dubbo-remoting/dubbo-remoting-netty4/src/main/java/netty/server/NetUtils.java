package netty.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * @author geyu
 * @date 2021/1/31 16:26
 */
public class NetUtils {
    public static String toAddressString(InetSocketAddress address) {
        return address.getAddress().getHostAddress() + ":" + address.getPort();
    }

    public static String getIpByHost(String host) {
        try {
            return InetAddress.getByName(host).getHostName();
        } catch (UnknownHostException e) {
            return host;
        }

    }
}

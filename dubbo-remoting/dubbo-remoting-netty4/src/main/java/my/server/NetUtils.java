package my.server;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;

/**
 * @author geyu
 * @date 2021/1/31 16:26
 */
public class NetUtils {

    private static final int RND_PORT_START = 30000;
    private static final int RND_PORT_RANGE = 10000;

    private static final int MIN_PORT = 0;
    private static final int MAX_PORT = 65535;

    private static volatile InetAddress LOCAL_ADDRESS = null;
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}\\:\\d{1,5}$");
    private static final Pattern LOCAL_IP_PATTERN = Pattern.compile("127(\\.\\d{1,3}){3}$");
    private static final Pattern IP_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3,5}$");
    private static final String ANYHOST_VALUE = "0.0.0.0";
    private static final String LOCALHOST_KEY = "localhost";
    private static final String LOCALHOST_VALUE = "127.0.0.1";
    private static final String DUBBO_PREFERRED_NETWORK_INTERFACE = "dubbo.network.interface.preferred";

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

    public static int getAvailablePort() {
        try (ServerSocket sc = new ServerSocket()) {
            sc.bind(null);
            return sc.getLocalPort();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return getRandomPort();
    }

    public static int getAvailablePort(int port) {
        if (port < 0) {
            return getAvailablePort();
        }
        for (int i = port; i < MAX_PORT; i++) {
            try (ServerSocket sc = new ServerSocket(i)) {
                return i;
            } catch (IOException e) {

            }
        }
        return getRandomPort();
    }

    private static int getRandomPort() {
        return RND_PORT_START + ThreadLocalRandom.current().nextInt(RND_PORT_RANGE);
    }

    public static InetAddress getLocalAddress() {
        if (LOCAL_ADDRESS != null) {
            return LOCAL_ADDRESS;
        }
        InetAddress LOCAL_ADDRESS = getLocalAddress0();
        return LOCAL_ADDRESS;
    }

    private static InetAddress getLocalAddress0() {
        InetAddress localAddress = null;

        // @since 2.7.6, choose the {@link NetworkInterface} first
        try {
            NetworkInterface networkInterface = findNetworkInterface();
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                Optional<InetAddress> addressOp = toValidAddress(addresses.nextElement());
                if (addressOp.isPresent()) {
                    try {
                        if (addressOp.get().isReachable(100)) {
                            return addressOp.get();
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        } catch (Throwable e) {
        }

        try {
            localAddress = InetAddress.getLocalHost();
            Optional<InetAddress> addressOp = toValidAddress(localAddress);
            if (addressOp.isPresent()) {
                return addressOp.get();
            }
        } catch (Throwable e) {
        }


        return localAddress;
    }

    private static Optional<InetAddress> toValidAddress(InetAddress address) {
        if (address instanceof Inet6Address) {
            Inet6Address v6Address = (Inet6Address) address;
            if (isPreferIPV6Address()) {
                return Optional.ofNullable(normalizeV6Address(v6Address));
            }
        }
        if (isValidV4Address(address)) {
            return Optional.of(address);
        }
        return Optional.empty();
    }

    static boolean isPreferIPV6Address() {
        return Boolean.getBoolean("java.net.preferIPv6Addresses");
    }

    static boolean isValidV4Address(InetAddress address) {
        if (address == null || address.isLoopbackAddress()) {
            return false;
        }

        String name = address.getHostAddress();
        return (name != null
                && IP_PATTERN.matcher(name).matches()
                && !ANYHOST_VALUE.equals(name)
                && !LOCALHOST_VALUE.equals(name));
    }

    static InetAddress normalizeV6Address(Inet6Address address) {
        String addr = address.getHostAddress();
        int i = addr.lastIndexOf('%');
        if (i > 0) {
            try {
                return InetAddress.getByName(addr.substring(0, i) + '%' + address.getScopeId());
            } catch (UnknownHostException e) {
                // ignore
            }
        }
        return address;
    }

    public static NetworkInterface findNetworkInterface() {

        List<NetworkInterface> validNetworkInterfaces = emptyList();
        try {
            validNetworkInterfaces = getValidNetworkInterfaces();
        } catch (Throwable e) {

        }

        NetworkInterface result = null;

        // Try to find the preferred one
        for (NetworkInterface networkInterface : validNetworkInterfaces) {
            if (isPreferredNetworkInterface(networkInterface)) {
                result = networkInterface;
                break;
            }
        }

        if (result == null) { // If not found, try to get the first one
            for (NetworkInterface networkInterface : validNetworkInterfaces) {
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    Optional<InetAddress> addressOp = toValidAddress(addresses.nextElement());
                    if (addressOp.isPresent()) {
                        try {
                            if (addressOp.get().isReachable(100)) {
                                result = networkInterface;
                                break;
                            }
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        }

        if (result == null) {
            result = first(validNetworkInterfaces);
        }

        return result;
    }

    private static List<NetworkInterface> getValidNetworkInterfaces() throws SocketException {
        List<NetworkInterface> validNetworkInterfaces = new LinkedList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (ignoreNetworkInterface(networkInterface)) { // ignore
                continue;
            }
            validNetworkInterfaces.add(networkInterface);
        }
        return validNetworkInterfaces;
    }

    private static boolean ignoreNetworkInterface(NetworkInterface networkInterface) throws SocketException {
        return networkInterface == null
                || networkInterface.isLoopback()
                || networkInterface.isVirtual()
                || !networkInterface.isUp();
    }

    public static boolean isPreferredNetworkInterface(NetworkInterface networkInterface) {
        String preferredNetworkInterface = System.getProperty(DUBBO_PREFERRED_NETWORK_INTERFACE);
        return Objects.equals(networkInterface.getDisplayName(), preferredNetworkInterface);
    }

    public static <T> T first(Collection<T> values) {
        if ((values == null)) {
            return null;
        }
        if (values instanceof List) {
            List<T> list = (List<T>) values;
            return list.get(0);
        } else {
            // 迭代器模式，不考虑values的具体类型
            return values.iterator().next();
        }
    }
}

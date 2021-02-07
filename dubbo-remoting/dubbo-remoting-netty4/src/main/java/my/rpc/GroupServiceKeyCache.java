package my.rpc;

import my.server.Constants;
import my.server.StringUtils;
import my.server.URL;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author geyu
 * @date 2021/2/4 12:25
 */
public class GroupServiceKeyCache {

    private String group;
    private Map<String, ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>>> serviceKeyMap;

    private static Map<String, GroupServiceKeyCache> groupServiceKeyCacheMap = new ConcurrentHashMap<>();

    public GroupServiceKeyCache(String group) {
        this.group = group;
        serviceKeyMap = new ConcurrentHashMap<>(512);
    }

    public static String serviceKey(URL url) {
        return serviceKey(url.getParameter(Constants.GROUP_KEY), url.getPath(), url.getParameter(Constants.VERSION_KEY), url.getPort());
    }

    public static String serviceKey(String groupName, String serviceName, String version, int port) {
        groupName = groupName == null ? "" : groupName;
        GroupServiceKeyCache group = groupServiceKeyCacheMap.get(groupName);
        if (group == null) {
            group = new GroupServiceKeyCache(groupName);
            groupServiceKeyCacheMap.putIfAbsent(groupName, group);
        }
        return group.getServiceKey(serviceName, version, port);
    }


    public String getServiceKey(String serviceName, String version, int port) {
        version = version == null ? "" : version;
        ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> versionMap = serviceKeyMap.get(serviceName);
        if (versionMap == null) {
            versionMap = new ConcurrentHashMap<>();
            serviceKeyMap.putIfAbsent(group, versionMap);
        }
        ConcurrentHashMap<Integer, String> portMap = versionMap.get(version);
        if (portMap == null) {
            portMap = new ConcurrentHashMap<>();
            versionMap.putIfAbsent(version, portMap);
        }
        String serviceKey = portMap.get(port);
        if (serviceKey == null) {
            serviceKey = createServiceKey(serviceName, version, port);
            portMap.putIfAbsent(port, serviceKey);
        }
        return serviceKey;

    }

    private String createServiceKey(String serviceName, String version, Integer port) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(group)) {
            sb.append(group).append("/");
        }
        if (StringUtils.isNotEmpty(serviceName)) {
            sb.append(serviceName).append(":");
        }
        if (StringUtils.isNotEmpty(version) && !"0.0.0".equals(version)) { // 不要 0.0.0 这个默认的，至于填充处是在客户端序列化version如果没有传入的会用默认的0.0.0，我们不处理即可
            sb.append(version).append(":");
        }
        if (port != null) {
            sb.append(port);
        }
        return sb.toString();
    }
}

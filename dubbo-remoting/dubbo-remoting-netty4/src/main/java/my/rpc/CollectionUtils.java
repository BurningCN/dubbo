package my.rpc;

import java.util.Map;

/**
 * @author geyu
 * @date 2021/2/4 14:22
 */
public class CollectionUtils {
    public static boolean isNotEmptyMap(Map map) {
        return !isEmptyMap(map);
    }

    public static boolean isEmptyMap(Map map) {
        return map == null || map.size() == 0;
    }
}

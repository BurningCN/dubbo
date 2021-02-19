package my.rpc.api.filter.tps;

import my.rpc.GroupServiceKeyCache;
import my.server.URL;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author geyu
 * @date 2021/2/19 11:46
 */
public class DefaultTpsLimiter implements TpsLimiter {
    private ConcurrentHashMap<String, StatItem> stats = new ConcurrentHashMap<>();

    @Override
    public boolean isAllowable(URL url) {
        int rate = url.getParameter("tps", -1);
        long interval = url.getParameter("tps.interval", 60 * 1000);
        String serviceKey = GroupServiceKeyCache.serviceKey(url);
        StatItem statItem = stats.get(serviceKey);
        if (rate > 0) {
            if (statItem == null) {
                stats.putIfAbsent(serviceKey, new StatItem(serviceKey, rate, interval));
                statItem = stats.get(serviceKey);
            } else {
                if (statItem.getInterval() != interval || statItem.getRate() != rate) {
                    stats.putIfAbsent(serviceKey, new StatItem(serviceKey, rate, interval));
                    statItem = stats.get(serviceKey);
                }
            }
            return statItem.isAllowable();
        } else {
            stats.remove(serviceKey);
        }

        return false;
    }
}

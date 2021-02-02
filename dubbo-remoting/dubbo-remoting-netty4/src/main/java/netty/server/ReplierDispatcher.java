package netty.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author geyu
 * @date 2021/2/2 13:00
 */
public class ReplierDispatcher implements Replier<Object> {

    private Replier<?> defaultReplier;

    private Map<Class<?>, Replier<?>> repliers = new ConcurrentHashMap<>();

    public ReplierDispatcher() {
        this(null, null);
    }

    public ReplierDispatcher(Replier<?> defaultReplier) {
        this(defaultReplier, null);
    }

    public ReplierDispatcher(Replier<?> defaultReplier, Map<Class<?>, Replier<?>> repliers) {
        this.defaultReplier = defaultReplier;
        if (repliers != null && repliers.size() > 0) {
            this.repliers.putAll(repliers);
        }

    }

    public <T> ReplierDispatcher addReplier(Class<T> clz, Replier<T> replier) {
        repliers.putIfAbsent(clz, replier);
        return this;
    }

    public <T> ReplierDispatcher removeReplier(Class<T> clz) {
        repliers.remove(clz);
        return this;
    }

    private Replier<?> getReplier(Class<?> clz) {
        for (Map.Entry<Class<?>, Replier<?>> entry : repliers.entrySet()) {
            if (entry.getKey().isAssignableFrom(clz)) {
                return entry.getValue();
            }
        }
        if (defaultReplier != null) {
            return defaultReplier;
        }
        throw new IllegalStateException("Replier not found, Unsupported message object: " + clz);
    }

    @Override
    public Object reply(InnerChannel channel, Object request) {
        Replier<?> replier = getReplier(request.getClass());
        return ((Replier) replier).reply(channel, request);
    }
}
//request = {JSONObject@2440}  size = 1
//        "name" -> "葛宇"
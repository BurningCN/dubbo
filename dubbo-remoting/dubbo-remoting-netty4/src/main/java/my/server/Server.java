package my.server;

/**
 * @author gy821075
 * @date 2021/1/28 18:13
 */
public interface Server extends IdleSensible {
    void close();
//    boolean isBind();
//    Collection<InnerChannel> getChannels();
}

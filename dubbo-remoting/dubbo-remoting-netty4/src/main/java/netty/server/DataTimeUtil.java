package netty.server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author geyu
 * @date 2021/1/30 15:23
 */
public class DataTimeUtil {
    final static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String now() {
        return LocalDateTime.now().format(formatter) + " ==== ";
    }

}

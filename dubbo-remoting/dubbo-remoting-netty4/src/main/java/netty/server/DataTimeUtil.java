package netty.server;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * @author geyu
 * @date 2021/1/30 15:23
 */
public class DataTimeUtil {
    final static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static String now() {
        return LocalDateTime.now().format(formatter) + " ==== ";
    }

    public static String parserTimestamp(long timestamp) {
        return sdf.format(new Date(timestamp));
    }

}

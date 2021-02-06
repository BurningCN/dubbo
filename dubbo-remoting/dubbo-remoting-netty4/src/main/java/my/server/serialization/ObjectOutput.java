package my.server.serialization;

import java.io.IOException;
import java.util.Map;

/**
 * @author gy821075
 * @date 2021/1/29 11:15
 */
public interface ObjectOutput extends DataOutput {
    void flushBuffer() throws IOException;

    void writeObject(Object data) throws IOException;

    default void writeAttachments(Map<String,Object> attachments) throws  IOException{
        writeObject(attachments);
    }
    default void writeThrowable(Throwable throwable) throws IOException{
        writeObject(throwable);
    }
}

package my.server.serialization.fastjson;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import my.server.serialization.ObjectOutput;

import java.io.*;

/**
 * @author geyu
 * @date 2021/1/29 11:20
 */
public class FastJsonObjectOutput implements ObjectOutput {
    private final PrintWriter writer;

    public FastJsonObjectOutput(OutputStream os){
        this(new OutputStreamWriter(os));
    }
    public FastJsonObjectOutput(Writer writer){
        this.writer = new PrintWriter(writer);
    }

    @Override
    public void flushBuffer() {
        writer.flush();
    }

    @Override
    public void writeObject(Object data) throws IOException {
        SerializeWriter serializeWriter = new SerializeWriter();
        JSONSerializer serializer = new JSONSerializer(serializeWriter);
        serializer.config(SerializerFeature.WriteEnumUsingToString,true);
        serializer.write(data);
        serializeWriter.writeTo(writer);
        writer.println();
        writer.flush();
    }

    @Override
    public void writeUTF(String v) throws IOException {
        writeObject(v);
    }
}

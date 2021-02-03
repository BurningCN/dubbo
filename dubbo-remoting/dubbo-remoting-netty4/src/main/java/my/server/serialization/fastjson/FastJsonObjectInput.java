package my.server.serialization.fastjson;

import com.alibaba.fastjson.JSON;
import my.server.serialization.ObjectInput;

import java.io.*;
import java.lang.reflect.Type;

/**
 * @author geyu
 * @date 2021/1/29 13:18
 */
public class FastJsonObjectInput implements ObjectInput {
    private BufferedReader reader;

    public FastJsonObjectInput(InputStream is) {
        this(new InputStreamReader(is));
    }

    public FastJsonObjectInput(Reader reader) {
        this.reader = new BufferedReader(reader);
    }

    public <T> T readObject(Class<T> cls)throws IOException {
        String json = readLine();
        return JSON.parseObject(json,cls);
    }


    public <T> T readObject(Class<T> cls, Type type) throws IOException {
        String json = readLine();
        return (T) JSON.parseObject(json, type);
    }

    @Override
    public Object readObject() throws IOException {
        String json = readLine();
        return JSON.parse(json);
    }

    private String readLine() throws IOException {
        String line = reader.readLine();
        if (line == null || line.trim().length() == 0) {
            throw new EOFException();
        }
        return line;
    }

    @Override
    public String readUTF() throws IOException {
        return readObject(String.class);
    }
}

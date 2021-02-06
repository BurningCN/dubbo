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

    public <T> T readObject(Class<T> cls) throws IOException {
        String json = readLine();
        return JSON.parseObject(json, cls);
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

    private <T> T read(Class<T> tClass) throws IOException {
        String line = readLine();
        return (T) JSON.parseObject(line, tClass);
    }

    @Override
    public boolean readBool() throws IOException {
        return read(boolean.class);
    }

    @Override
    public byte readByte() throws IOException {
        return read(byte.class);
    }

    @Override
    public short readShort() throws IOException {
        return read(short.class);
    }

    @Override
    public int readInt() throws IOException {
        return read(int.class);
    }

    @Override
    public long readLong() throws IOException {
        return read(long.class);
    }

    @Override
    public float readFloat() throws IOException {
        return read(float.class);
    }

    @Override
    public double readDouble() throws IOException {
        return read(double.class);
    }

    @Override
    public String readUTF() throws IOException {
        return readObject(String.class);
    }

    @Override
    public byte[] readBytes() throws IOException {
        return readLine().getBytes();
    }


}

package my.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author geyu
 * @date 2021/1/30 12:33
 */
public class MultiMessage implements Iterable {
    private List messageList = new ArrayList();

    public void addMessage(Object msg) {
        messageList.add(msg);
    }

    public static MultiMessage create() {
        return new MultiMessage();
    }

    @Override
    public Iterator iterator() {
        return messageList.iterator();
    }

    public boolean isEmpty() {
        return messageList.isEmpty();
    }

    public Object get(int index) {
        return messageList.get(index);
    }

    public int size() {
        return messageList.size();
    }


}

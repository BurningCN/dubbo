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

    public void addMessage(Object msg){
        messageList.add(msg);
    }

    @Override
    public Iterator iterator() {
        return messageList.iterator();
    }
}

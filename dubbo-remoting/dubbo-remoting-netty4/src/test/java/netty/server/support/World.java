package netty.server.support;

import java.io.Serializable;

/**
 * @author geyu
 * @date 2021/2/2 12:57
 */
public class World implements Serializable {

    private static final long serialVersionUID = 8563900551013747774L;

    public World() {
    }

    private String name;

    public World(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "World{" +
                "name='" + name + '\'' +
                '}';
    }
}

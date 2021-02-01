package netty.server;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author geyu
 * @date 2021/1/28 18:12
 */
public class URL {
    private String protocol;
    private String username;
    private String pwd;
    private String path;
    private String host;
    private int port;
    private Map<String, String> parameters;
    private Map<String, Number> numberMap = new ConcurrentHashMap<>();
    private String ip;

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }



    public int getPositiveParameter(String key, int defaultValue) {
        if (defaultValue <= 0) {
            throw new IllegalArgumentException("defaultValue <=0");
        }
        int value = getParameter(key);
        return value < 0 ? defaultValue : value;
    }

    private int getParameter(String key) {
        Number number = numberMap.get(key);
        if (number == null && parameters != null) {
            String val = parameters.get(key);
            if (val != null) {
                number = Integer.parseInt(val);
                numberMap.put(key, number);
            }
        }
        return number == null ? -1 : number.intValue();
    }

    public boolean getParameter(String key, boolean defaultValue) {
        if (parameters != null) { // todo myRPC 这里不应该判空
            String value = parameters.get(key);
            return (value == null || value.isEmpty()) ? defaultValue : Boolean.parseBoolean(value);
        }
        return false;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    private URL(Builder b) {
        this.protocol = b.protocol;
        this.username = b.username;
        this.pwd = b.pwd;
        this.path = b.path;
        this.host = b.host;
        this.port = b.port;
        this.parameters = b.parameters;
    }

    public URL() {

    }

    public String getIp() {
        if (ip == null) {
            ip = NetUtils.getIpByHost(host);
        }
        return ip;
    }

    public static class Builder {
        String protocol = null;
        String username = null;
        String pwd = null;
        String path = null;
        String host = null;
        int port = 0;
        Map<String, String> parameters = null;

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder pwd(String pwd) {
            this.pwd = pwd;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder parameters(Map<String, String> parameters) {
            this.parameters = parameters;
            return this;
        }

        public URL build() {
            return new URL(this);
        }
    }

    public static URL valueOf(String url) {
        if (url == null || ((url = url.trim()).length() == 0)) {
            throw new IllegalArgumentException("url == null");
        }
        String protocol = null;
        String username = null;
        String pwd = null;
        String path = null;
        String host = null;
        int port = 0;
        Map<String, String> parameters = null;
        // 1.参数对
        int i = url.indexOf("?");
        if (i > 0) {
            String[] parts = url.substring(i + 1).split("&");
            parameters = new HashMap<>();
            for (String part : parts) {
                part = part.trim();
                if (part.length() > 0) {
                    int j = part.indexOf("=");
                    String k = part.substring(0, j);
                    String v = part.substring(j + 1);
                    parameters.put(k, v);
                } else {
                    parameters.put(part, part);
                }
            }
            url = url.substring(0, i);
        }
        // 2.protocol
        i = url.indexOf("://");
        if (i >= 0) {
            if (i == 0) {
                throw new IllegalArgumentException("url missing protocol");
            } else {
                protocol = url.substring(0, i);
            }
            url = url.substring(i + 3);
        } else {
            // todo myRPC
        }
        // 3.path
        i = url.indexOf("/");
        if (i >= 0) {
            path = url.substring(i + 1);
            url = url.substring(0, i);
        }
        // 4.username:pwd
        i = url.lastIndexOf("@");
        if (i >= 0) {
            username = url.substring(0, i);
            int j = username.indexOf(":");
            if (j >= 0) {
                pwd = username.substring(j + 1);
                username = username.substring(0, j);
            }
            url = url.substring(i + 1);
        }
        // 5.ip:port
        i = url.indexOf(":");
        if (i >= 0) {
            port = Integer.parseInt(url.substring(i + 1));
            url = url.substring(0, i);
        }
        if (url.length() > 0) {
            host = url;
        }
        return new URL.Builder().username(username).host(host).port(port).pwd(pwd).protocol(protocol).parameters(parameters).path(path).build();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}

package my.server;

import my.common.extension.ExtensionLoader;
import my.server.serialization.Serialization;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author geyu
 * @date 2021/2/10 15:33
 */
public class CodecSupport {
    private static Map<Byte, Serialization> ID_SERIALIZATION_MAP = new HashMap<Byte, Serialization>();
    private static Map<Byte, String> ID_SERIALIZATIONNAME_MAP = new HashMap<Byte, String>();
    private static Map<String, Byte> SERIALIZATIONNAME_ID_MAP = new HashMap<String, Byte>();

    static {
        ExtensionLoader<Serialization> loader = ExtensionLoader.getExtensionLoader(Serialization.class);
        Set<String> supportedExtensions = loader.getSupportedExtensions();
        for (String name : supportedExtensions) {
            Serialization serialization = loader.getExtension(name);
            byte id = serialization.getContentTypeId();
            if (ID_SERIALIZATION_MAP.containsKey(id)) {
                System.out.println("Serialization extension " + serialization.getClass().getName()
                        + " has duplicate id to Serialization extension "
                        + ID_SERIALIZATION_MAP.get(id).getClass().getName()
                        + ", ignore this Serialization extension");
                continue;
            }
            ID_SERIALIZATION_MAP.put(id, serialization);
            ID_SERIALIZATIONNAME_MAP.put(id, name);
            SERIALIZATIONNAME_ID_MAP.put(name, id);
        }
    }

    public static Serialization getSerializationById(Byte id) {
        return ID_SERIALIZATION_MAP.get(id);
    }



}

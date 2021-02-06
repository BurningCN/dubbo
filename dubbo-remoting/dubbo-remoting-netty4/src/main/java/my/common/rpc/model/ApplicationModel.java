package my.common.rpc.model;

import my.common.context.FrameworkExt;
import my.common.extension.ExtensionLoader;

/**
 * @author geyu
 * @version 1.0
 * @date 2021/2/6 12:48
 */
public class ApplicationModel {

    private static final ExtensionLoader LOADER = ExtensionLoader.getExtensionLoader(FrameworkExt.class);

    public static ServiceRepository getServiceRepository() {
        return (ServiceRepository) LOADER.getExtension(ServiceRepository.NAME);
    }
}

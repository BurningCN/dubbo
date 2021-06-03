package org.apache.dubbo.configcenter.support.zookeeper;


import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.remoting.zookeeper.EventType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CacheListenerTest {
    @Test
    public void addAndRemoveTest() {
        CacheListener cacheListener = new CacheListener("/dubbo/config");

        ConfigurationListener listenerProvider1 = conf -> {
        };
        cacheListener.addListener("/dubbo/config/dubbo/provider", listenerProvider1);

        ConfigurationListener listenerProvider2 = conf -> {
        };
        cacheListener.addListener("/dubbo/config/dubbo/provider", listenerProvider2);


        Assertions.assertEquals(2, cacheListener.getConfigurationListeners("/dubbo/config/dubbo/provider").size());
        cacheListener.removeListener("/dubbo/config/dubbo/provider", listenerProvider1);
        Assertions.assertEquals(1, cacheListener.getConfigurationListeners("/dubbo/config/dubbo/provider").size());
        cacheListener.removeListener("/dubbo/config/dubbo/provider", listenerProvider2);
        Assertions.assertNull(cacheListener.getConfigurationListeners("/dubbo/provider"));
    }

    @Test
    public void dataChangedTest() {
        CacheListener cacheListener = new CacheListener("/dubbo/config");

        ConfigurationListener configurationListener = conf -> {
            Assertions.assertEquals(conf.getContent(), "hello world");
            Assertions.assertEquals(conf.getChangeType(), ConfigChangeType.MODIFIED);
            Assertions.assertEquals(conf.getKey(), "provider");
            Assertions.assertEquals(conf.getGroup(), "dubbo");
        };
        cacheListener.addListener("/dubbo/config/dubbo/provider", configurationListener);
        cacheListener.dataChanged("/dubbo/config/dubbo/provider", "hello world", EventType.NodeDataChanged);
    }
}

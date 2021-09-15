/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.metadata.report.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.identifier.KeyTypeEnum;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.FILE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.metadata.report.support.Constants.CYCLE_REPORT_KEY;
import static org.apache.dubbo.metadata.report.support.Constants.DEFAULT_METADATA_REPORT_CYCLE_REPORT;
import static org.apache.dubbo.metadata.report.support.Constants.DEFAULT_METADATA_REPORT_RETRY_PERIOD;
import static org.apache.dubbo.metadata.report.support.Constants.DEFAULT_METADATA_REPORT_RETRY_TIMES;
import static org.apache.dubbo.metadata.report.support.Constants.RETRY_PERIOD_KEY;
import static org.apache.dubbo.metadata.report.support.Constants.RETRY_TIMES_KEY;
import static org.apache.dubbo.metadata.report.support.Constants.SYNC_REPORT_KEY;

/**
 *
 */
public abstract class AbstractMetadataReport implements MetadataReport {

    protected final static String DEFAULT_ROOT = "dubbo";

    private static final int ONE_DAY_IN_MILLISECONDS = 60 * 24 * 60 * 1000;
    private static final int FOUR_HOURS_IN_MILLISECONDS = 60 * 4 * 60 * 1000;
    // Log output
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // Local disk cache, where the special key value.registries records the list of metadata centers, and the others are the list of notified service providers
    final Properties properties = new Properties();
    private final ExecutorService reportCacheExecutor = Executors.newFixedThreadPool(1, new NamedThreadFactory("DubboSaveMetadataReport", true));
    final Map<MetadataIdentifier, Object> allMetadataReports = new ConcurrentHashMap<>(4);

    private final AtomicLong lastCacheChanged = new AtomicLong();
    final Map<MetadataIdentifier, Object> failedReports = new ConcurrentHashMap<>(4);
    private URL reportURL;
    boolean syncReport;
    // Local disk cache file
    File file;
    private AtomicBoolean initialized = new AtomicBoolean(false);
    public MetadataReportRetry metadataReportRetry;

    //下面的过程和AbstractRegistry很像
    // url eg zookeeper://127.0.0.1:2181/org.apache.dubbo.metadata.report.MetadataReport?application=demo-provider
    public AbstractMetadataReport(URL reportServerURL) {
        setUrl(reportServerURL);
        // Start file save timer
        // eg /Users/gy821075/.dubbo/dubbo-metadata-demo-provider-127.0.0.1-2181.cache
        String defaultFilename = System.getProperty("user.home") + "/.dubbo/dubbo-metadata-" + reportServerURL.getParameter(APPLICATION_KEY) + "-" + reportServerURL.getAddress().replaceAll(":", "-") + ".cache";
        String filename = reportServerURL.getParameter(FILE_KEY, defaultFilename);
        File file = null;
        if (ConfigUtils.isNotEmpty(filename)) {
            file = new File(filename);
            if (!file.exists() && file.getParentFile() != null && !file.getParentFile().exists()) {
                if (!file.getParentFile().mkdirs()) {
                    throw new IllegalArgumentException("Invalid service store file " + file + ", cause: Failed to create directory " + file.getParentFile() + "!");
                }
            }
            // if this file exist, firstly delete it.
            // getAndSet内部会自动cas，返回值为之前的值，比如返回false。如果cas修改成功，且文件存在，直接删除
            if (!initialized.getAndSet(true) && file.exists()) {
                // todo need pr 这里应该也同步吧lock文件删了
                file.delete();
            }
        }
        this.file = file;
        loadProperties();
        // 默认异步保存
        syncReport = reportServerURL.getParameter(SYNC_REPORT_KEY, false);
        // 100次，3000ms
        metadataReportRetry = new MetadataReportRetry(reportServerURL.getParameter(RETRY_TIMES_KEY, DEFAULT_METADATA_REPORT_RETRY_TIMES),
                reportServerURL.getParameter(RETRY_PERIOD_KEY, DEFAULT_METADATA_REPORT_RETRY_PERIOD));
        // cycle report the data switch 默认true
        if (reportServerURL.getParameter(CYCLE_REPORT_KEY, DEFAULT_METADATA_REPORT_CYCLE_REPORT)) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("DubboMetadataReportTimer", true));
            // 每天凌晨的2~6点执行一次，全量的publishAll，将内存的数据推到远端和本地文件
            scheduler.scheduleAtFixedRate(this::publishAll, calculateStartTime(), ONE_DAY_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
        }
    }

    public URL getUrl() {
        return reportURL;
    }

    protected void setUrl(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("metadataReport url == null");
        }
        this.reportURL = url;
    }

    private void doSaveProperties(long version) {
        if (version < lastCacheChanged.get()) {
            return;
        }
        if (file == null) {
            return;
        }
        // Save
        try {
            // 注意这里调用的是getAbsolutePath，也可以是getPath
            File lockfile = new File(file.getAbsolutePath() + ".lock");
            if (!lockfile.exists()) {
                lockfile.createNewFile();
            }
            // try-with-resource的()里面可以放多个语句
            try (RandomAccessFile raf = new RandomAccessFile(lockfile, "rw");
                 FileChannel channel = raf.getChannel()) {
                // tryLock和Lock，非阻塞和阻塞
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    throw new IOException("Can not lock the metadataReport cache file " + file.getAbsolutePath() + ", ignore and retry later, maybe multi java process use the file, please config: dubbo.metadata.file=xxx.properties");
                }
                // Save
                try {
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    try (FileOutputStream outputFile = new FileOutputStream(file)) {
                        properties.store(outputFile, "Dubbo metadataReport Cache");
                    }
                    /*
                    #Dubbo metadataReport Cache
                    #Sun Apr 18 22:30:59 CST 2021
                    my.metadata.api.store.InterfaceNameTestService\:1.0.0\:\:provider\:vic={"parameters"\:{"testPKey"\:"8989","application"\:"vic","version"\:"1.0.0"},"canonicalName"\:"my.metadata.api.store.InterfaceNameTestService","codeSource"\:"file\:/Users/gy821075/IdeaProjects/myRPC/target/test-classes/","methods"\:[{"name"\:"test","parameterTypes"\:[],"returnType"\:"void"}],"types"\:[{"type"\:"void","typeBuilderName"\:"my.metadata.api.definition.builder.DefaultTypeBuilder"}]}
                    * */
                } finally {
                    lock.release();
                }
            }
        } catch (Throwable e) {
            if (version < lastCacheChanged.get()) {
                return;
            } else {
                reportCacheExecutor.execute(new SaveProperties(lastCacheChanged.incrementAndGet()));
            }
            logger.warn("Failed to save service store file, cause: " + e.getMessage(), e);
        }
    }

    void loadProperties() {
        if (file != null && file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                properties.load(in);
                if (logger.isInfoEnabled()) {
                    logger.info("Load service store file " + file + ", data: " + properties);
                }
            } catch (Throwable e) {
                logger.warn("Failed to load service store file " + file, e);
            }
        }
    }

    private void saveProperties(MetadataIdentifier metadataIdentifier, String value, boolean add, boolean sync) {
        if (file == null) {
            return;
        }

        try {
            if (add) {
                properties.setProperty(metadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY), value);
            } else {
                properties.remove(metadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY));
            }
            // 版本号递增
            long version = lastCacheChanged.incrementAndGet();
            if (sync) {
                new SaveProperties(version).run();
            } else {
                reportCacheExecutor.execute(new SaveProperties(version));
            }

        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }

    @Override
    public String toString() {
        return getUrl().toString();
    }

    private class SaveProperties implements Runnable {
        private long version;

        private SaveProperties(long version) {
            this.version = version;
        }

        @Override
        public void run() {
            doSaveProperties(version);
        }
    }

    // metadataReport的异步/同步同时还囊括了远端的保存，而AbstractRegistry的异步仅针对本地缓存文件，不包括远端
    @Override
    public void storeProviderMetadata(MetadataIdentifier providerMetadataIdentifier, ServiceDefinition serviceDefinition) {
        if (syncReport) {
            storeProviderMetadataTask(providerMetadataIdentifier, serviceDefinition);
        } else {
            reportCacheExecutor.execute(() -> storeProviderMetadataTask(providerMetadataIdentifier, serviceDefinition));
        }
    }

    private void storeProviderMetadataTask(MetadataIdentifier providerMetadataIdentifier, ServiceDefinition serviceDefinition) {
        try {
            logger.info("store provider metadata. Identifier : " + providerMetadataIdentifier + "; definition: " + serviceDefinition);

            allMetadataReports.put(providerMetadataIdentifier, serviceDefinition);
            failedReports.remove(providerMetadataIdentifier);

            Gson gson = new Gson();
            String data = gson.toJson(serviceDefinition);

            // doXX 存远端
            doStoreProviderMetadata(providerMetadataIdentifier, data);

            // 存本地文件。注意最后取了个反，因为storeProviderMetadataTask是被storeProviderMetadata调用的，如果当前是同步的，为了缓解时长，这里就异步保存。
            // 如果当前是被异步调用的，即reportCacheExecutor线程调用的，那么!syncReport为true，表示同步保存，即依然在当前reportCacheExecutor线程中进行本地存储操作
            saveProperties(providerMetadataIdentifier, data, true, !syncReport);
        } catch (Exception e) {
            // retry again. If failed again, throw exception.
            failedReports.put(providerMetadataIdentifier, serviceDefinition);
            metadataReportRetry.startRetryTask();
            logger.error("Failed to put provider metadata " + providerMetadataIdentifier + " in  " + serviceDefinition + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void storeConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, Map<String, String> serviceParameterMap) {
        if (syncReport) {
            storeConsumerMetadataTask(consumerMetadataIdentifier, serviceParameterMap);
        } else {
            reportCacheExecutor.execute(() -> storeConsumerMetadataTask(consumerMetadataIdentifier, serviceParameterMap));
        }
    }

    // 看第二个参数，注意和storeProviderMetadataTask方法对比，消费者是 MetadataIdentifier -> Map ，MetadataIdentifier映射的是map，而provider映射的是FullServiceDefinition
    public void storeConsumerMetadataTask(MetadataIdentifier consumerMetadataIdentifier, Map<String, String> serviceParameterMap) {
        try {
            logger.info("store consumer metadata. Identifier : " + consumerMetadataIdentifier + "; definition: " + serviceParameterMap);

            allMetadataReports.put(consumerMetadataIdentifier, serviceParameterMap);
            failedReports.remove(consumerMetadataIdentifier);

            Gson gson = new Gson();
            String data = gson.toJson(serviceParameterMap);

            doStoreConsumerMetadata(consumerMetadataIdentifier, data);

            saveProperties(consumerMetadataIdentifier, data, true, !syncReport);
        } catch (Exception e) {
            failedReports.put(consumerMetadataIdentifier, serviceParameterMap);
            metadataReportRetry.startRetryTask();
            logger.error("Failed to put consumer metadata " + consumerMetadataIdentifier + ";  " + serviceParameterMap + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveServiceMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
        if (syncReport) {
            doSaveMetadata(metadataIdentifier, url);
        } else {
            reportCacheExecutor.execute(() -> doSaveMetadata(metadataIdentifier, url));
        }
    }

    @Override
    public void removeServiceMetadata(ServiceMetadataIdentifier metadataIdentifier) {
        if (syncReport) {
            doRemoveMetadata(metadataIdentifier);
        } else {
            reportCacheExecutor.execute(() -> doRemoveMetadata(metadataIdentifier));
        }
    }

    @Override
    public List<String> getExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
        // TODO, fallback to local cache
        return doGetExportedURLs(metadataIdentifier);
    }

    @Override
    public void saveSubscribedData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, Set<String> urls) {
        if (syncReport) {
            doSaveSubscriberData(subscriberMetadataIdentifier, new Gson().toJson(urls));
        } else {
            reportCacheExecutor.execute(() -> doSaveSubscriberData(subscriberMetadataIdentifier, new Gson().toJson(urls)));
        }
    }


    @Override
    public List<String> getSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier) {
        String content = doGetSubscribedURLs(subscriberMetadataIdentifier);
        Type setType = new TypeToken<SortedSet<String>>() {
        }.getType();
        return new Gson().fromJson(content, setType);
    }

    public String getProtocol(URL url) {
        String protocol = url.getParameter(SIDE_KEY);
        protocol = protocol == null ? url.getProtocol() : protocol;
        return protocol;
    }

    /**
     * @return if need to continue
     */
    public boolean retry() {
        return doHandleMetadataCollection(failedReports);
    }

    private boolean doHandleMetadataCollection(Map<MetadataIdentifier, Object> metadataMap) {
        if (metadataMap.isEmpty()) {
            return true;
        }
        Iterator<Map.Entry<MetadataIdentifier, Object>> iterable = metadataMap.entrySet().iterator();
        while (iterable.hasNext()) {
            Map.Entry<MetadataIdentifier, Object> item = iterable.next();
            if (PROVIDER_SIDE.equals(item.getKey().getSide())) {
                this.storeProviderMetadata(item.getKey(), (FullServiceDefinition) item.getValue());
            } else if (CONSUMER_SIDE.equals(item.getKey().getSide())) {
                this.storeConsumerMetadata(item.getKey(), (Map) item.getValue());
            }

        }
        // false表示发生了实际变更
        return false;
    }

    /**
     * not private. just for unittest.
     */
    void publishAll() {
        logger.info("start to publish all metadata.");
        // retry方法也调用了这个
        this.doHandleMetadataCollection(allMetadataReports);
    }

    /**
     * between 2:00 am to 6:00 am, the time is random.
     *
     * @return
     */
    long calculateStartTime() {
        Calendar calendar = Calendar.getInstance();
        // 先用变量记录当前时间
        long nowMill = calendar.getTimeInMillis();
        // 将时间调整为今天的00:00:00
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        // 做差，calendar.getTimeInMillis()此时是今天00:00:00的时间戳。
        // 比如现在是23:00，那么下面的subtract就是1h，也就是说距离明天凌晨0:00还有1h
        long subtract = calendar.getTimeInMillis() + ONE_DAY_IN_MILLISECONDS - nowMill;
        // 1 +[2,6]的随机值（记作A），表示距离当前时间还有这么长时间（A）才能触发schedule，注意该类构造方法最后的schedule.scheduleAtFixRate(,initDelay,)的initDelay参数是
        // 从当前时间，延迟A后执行publishAll。
        return subtract + (FOUR_HOURS_IN_MILLISECONDS / 2) + ThreadLocalRandom.current().nextInt(FOUR_HOURS_IN_MILLISECONDS);
    }

    class MetadataReportRetry {
        protected final Logger logger = LoggerFactory.getLogger(getClass());

        final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(0, new NamedThreadFactory("DubboMetadataReportRetryTimer", true));
        volatile ScheduledFuture retryScheduledFuture;
        final AtomicInteger retryCounter = new AtomicInteger(0);
        // retry task schedule period
        long retryPeriod;
        // if no failed report, wait how many times to run retry task.
        int retryTimesIfNonFail = 600;

        int retryLimit;

        public MetadataReportRetry(int retryTimes, int retryPeriod) {
            this.retryPeriod = retryPeriod;
            // 默认100
            this.retryLimit = retryTimes;
        }

        void startRetryTask() {
            // todo need pr 多层嵌套优化
            // 这里双重检查的原因是，该方法可以被多次调用，但是只要第一次调用起作用，调用点比如storeProviderMetadataTask抛异常了
            if (retryScheduledFuture == null) {
                synchronized (retryCounter) {
                    if (retryScheduledFuture == null) {
                        // 注意这种用法，不是scheduleAtFixRate，这种可以拿到返回值，并可以随时cancel
                        retryScheduledFuture = retryExecutor.scheduleWithFixedDelay(new Runnable() {
                            @Override
                            public void run() {
                                // Check and connect to the metadata
                                try {
                                    int times = retryCounter.incrementAndGet();
                                    logger.info("start to retry task for metadata report. retry times:" + times);
                                    // retry() 进行重试，如果返回false表示发生了实际的写远端/本地操作，如果返回true表示failedReports一直是空
                                    if (retry() && times > retryTimesIfNonFail) {
                                        cancelRetryTask();
                                    }
                                    if (times > retryLimit) {
                                        cancelRetryTask();
                                    }
                                } catch (Throwable t) { // Defensive fault tolerance
                                    logger.error("Unexpected error occur at failed retry, cause: " + t.getMessage(), t);
                                }
                            }
                            // 默认3s一次，直到里面满足两个if的一种，走cancelRetryTask，才会结束定时任务
                        }, 500, retryPeriod, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }

        void cancelRetryTask() {
            retryScheduledFuture.cancel(false);
            retryExecutor.shutdown();
        }
    }

    private void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, List<String> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            return;
        }
        List<String> encodedUrlList = new ArrayList<>(urls.size());
        for (String url : urls) {
            encodedUrlList.add(URL.encode(url));
        }
        doSaveSubscriberData(subscriberMetadataIdentifier, encodedUrlList);
    }

    protected abstract void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions);

    protected abstract void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String serviceParameterString);

    protected abstract void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url);

    protected abstract void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier);

    protected abstract List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier);

    protected abstract void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, String urlListStr);

    protected abstract String doGetSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier);

}

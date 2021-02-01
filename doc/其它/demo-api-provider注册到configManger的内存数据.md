  configsCache = {HashMap@1505}  size = 9
  
      // 这三条是demo-api-provider的启动程序里面赋值过来的
     "registry" -> {HashMap@1682}  size = 1
        key = "registry"
        value = {HashMap@1682}  size = 1
         "RegistryConfig#default" -> {RegistryConfig@1586} "<dubbo:registry address="zookeeper:127.0.0.1:2181" protocol="zookeeper" port="2181" />"
     "application" -> {HashMap@1684}  size = 1
        key = "application"
        value = {HashMap@1684}  size = 1
         "dubbo-demo-api-provider" -> {ApplicationConfig@1506} "<dubbo:application hostname="B-RHDTJG5H-2145" name="dubbo-demo-api-provider" />"
     "service" -> {HashMap@1686}  size = 1
         key = "service"
         value = {HashMap@1686}  size = 1
           "ServiceConfig#default" -> {ServiceConfig@1564} "<dubbo:service exported="false" unexported="false" />"
          
         
     // 这两条是利用useRegistryAsConfigCenterIfNecessary、useRegistryAsMetadataCenterIfNecessary 触发的  
     "config-center" -> {HashMap@2271}  size = 1
         key = "config-center"
         value = {HashMap@2271}  size = 1
            "config-center-zookeeper-2181" -> {ConfigCenterConfig@1673} "<dubbo:config-center group="dubbo" timeout="3000" check="true" address="zookeeper:127.0.0.1:2181" protocol="zookeeper" port="2181" configFile="dubbo.properties" highestPriority="false" />"
     "metadata-report" -> {HashMap@2541}  size = 1
         key = "metadata-report"
         value = {HashMap@2541}  size = 1
          "metadata-center-zookeeper-2181" -> {MetadataReportConfig@2485} "<dubbo:metadata-report address="zookeeper:127.0.0.1:2181" />"
     
     
     // 下面都是checkGlobalConfigs触发，添加进来的
     "provider" -> {HashMap@2286}  size = 1
        key = "provider"
        value = {HashMap@2286}  size = 1
         "ProviderConfig#default" -> {ProviderConfig@2262} "ProviderConfig{host='null', port=null, contextpath='null', threadpool='null', threadname='null', threads=null, iothreads=null, alive=null, queues=null, accepts=null, codec='null', charset='null', payload=null, buffer=null, transporter='null', exchanger='null', dispatcher='null', networker='null', server='null', client='null', telnet='null', prompt='null', status='null', wait=null, isDefault=null}"
     "module" -> {HashMap@2422}  size = 1
        key = "module"
        value = {HashMap@2422}  size = 1
         "ModuleConfig#default" -> {ModuleConfig@2403} "<dubbo:module />"
     "monitor" -> {HashMap@2394}  size = 1
        key = "monitor"
        value = {HashMap@2394}  size = 1
         "MonitorConfig#default" -> {MonitorConfig@2344} "<dubbo:monitor />"
     "metrics" -> {HashMap@2409}  size = 1
        key = "metrics"
        value = {HashMap@2409}  size = 1
         "MetricsConfig#default" -> {MetricsConfig@2388} "<dubbo:metrics />"
     "consumer" -> {HashMap@2350}  size = 1
        key = "consumer"
        value = {HashMap@2350}  size = 1
         "ConsumerConfig#default" -> {ConsumerConfig@2280} "<dubbo:consumer />"
     
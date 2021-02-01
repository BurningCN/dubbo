
(1)添加前registryUrl:

registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&pid=44487&registry=zookeeper&timestamp=1610257819542


(2)添加后registryUrl:（执行registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()))）

registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&**export=dubbo%3A%2F%2F192.168.1.7%3A20880%2Forg.apache.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddubbo-demo-api-provider%26bind.ip%3D192.168.1.7%26bind.port%3D20880%26default%3Dtrue%26deprecated%3Dfalse%26dubbo%3D2.0.2%26dynamic%3Dtrue%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26metadata-type%3Dremote%26methods%3DsayHello%2CsayHelloAsync%26pid%3D44836%26release%3D%26side%3Dprovider%26timestamp%3D1610259957162&**pid=44836&registry=zookeeper&timestamp=1610259952150

（加粗部分在这idea自带面板看不出来，用typora打开看,加粗部分是将url encode的了，未encode的url如下）

dubbo://192.168.1.7:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=192.168.1.7&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&metadata-type=remote&methods=sayHello,sayHelloAsync&pid=44970&release=&side=provider&timestamp=1610260794562
在[6.1 如何在spring中自定义xml标签](http://www.cnblogs.com/java-zhao/p/7619922.html)中我们看到了在spring中自定义xml标签的方式。dubbo也是这样来实现的。

![img](https://images2017.cnblogs.com/blog/866881/201710/866881-20171002130603693-691216396.png)

**一 META_INF/dubbo.xsd**

比较长，只列出<dubbo:applicaton>元素相关的。

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```xml
 1 <?xml version="1.0" encoding="UTF-8" standalone="no"?>
 2 <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
 3             xmlns:beans="http://www.springframework.org/schema/beans"
 4             xmlns:tool="http://www.springframework.org/schema/tool"
 5             xmlns="http://code.alibabatech.com/schema/dubbo"
 6             targetNamespace="http://code.alibabatech.com/schema/dubbo">
 7 
 8     。。。
 9 
10    <xsd:complexType name="applicationType">
11         <xsd:attribute name="id" type="xsd:ID">
12             <xsd:annotation>
13                 <xsd:documentation><![CDATA[ The unique identifier for a bean. ]]></xsd:documentation>
14             </xsd:annotation>
15         </xsd:attribute>
16         <xsd:attribute name="name" type="xsd:string" use="required">
17             <xsd:annotation>
18                 <xsd:documentation><![CDATA[ The application name. ]]></xsd:documentation>
19             </xsd:annotation>
20         </xsd:attribute>
21         <xsd:attribute name="version" type="xsd:string">
22             <xsd:annotation>
23                 <xsd:documentation><![CDATA[ The application version. ]]></xsd:documentation>
24             </xsd:annotation>
25         </xsd:attribute>
26         <xsd:attribute name="owner" type="xsd:string">
27             <xsd:annotation>
28                 <xsd:documentation><![CDATA[ The application owner name (email prefix). ]]></xsd:documentation>
29             </xsd:annotation>
30         </xsd:attribute>
31         <xsd:attribute name="organization" type="xsd:string">
32             <xsd:annotation>
33                 <xsd:documentation><![CDATA[ The organization name. ]]></xsd:documentation>
34             </xsd:annotation>
35         </xsd:attribute>
36         <xsd:attribute name="architecture" type="xsd:string">
37             <xsd:annotation>
38                 <xsd:documentation><![CDATA[ The architecture. ]]></xsd:documentation>
39             </xsd:annotation>
40         </xsd:attribute>
41         <xsd:attribute name="environment" type="xsd:string">
42             <xsd:annotation>
43                 <xsd:documentation><![CDATA[ The application environment, eg: dev/test/run ]]></xsd:documentation>
44             </xsd:annotation>
45         </xsd:attribute>
46         <xsd:attribute name="compiler" type="xsd:string">
47             <xsd:annotation>
48                 <xsd:documentation><![CDATA[ The java code compiler. ]]></xsd:documentation>
49             </xsd:annotation>
50         </xsd:attribute>
51         <xsd:attribute name="logger" type="xsd:string">
52             <xsd:annotation>
53                 <xsd:documentation><![CDATA[ The application logger. ]]></xsd:documentation>
54             </xsd:annotation>
55         </xsd:attribute>
56         <xsd:attribute name="registry" type="xsd:string" use="optional">
57             <xsd:annotation>
58                 <xsd:documentation><![CDATA[ The application registry. ]]></xsd:documentation>
59             </xsd:annotation>
60         </xsd:attribute>
61         <xsd:attribute name="monitor" type="xsd:string" use="optional">
62             <xsd:annotation>
63                 <xsd:documentation><![CDATA[ The application monitor. ]]></xsd:documentation>
64             </xsd:annotation>
65         </xsd:attribute>
66         <xsd:attribute name="default" type="xsd:string" use="optional">
67             <xsd:annotation>
68                 <xsd:documentation><![CDATA[ Is default. ]]></xsd:documentation>
69             </xsd:annotation>
70         </xsd:attribute>
71     </xsd:complexType>
72 
73     。。。
74 
75     <xsd:element name="application" type="applicationType">
76         <xsd:annotation>
77             <xsd:documentation><![CDATA[ The application config ]]></xsd:documentation>
78         </xsd:annotation>
79     </xsd:element>
80 
81     。。。
82 
83 </xsd:schema>
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

与上一节完全相似。

 

**二 META_INF/spring.schemas**

```
1 http\://code.alibabatech.com/schema/dubbo/dubbo.xsd=META-INF/dubbo.xsd
```

与上一节完全相似。

 

**三 DubboBeanDefinitionParser**

代码较长，不再贴出来了，与上一节完全相似。

 

**四 DubboNamespaceHandler**

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```java
 1 package com.alibaba.dubbo.config.spring.schema;
 2 
 3 import com.alibaba.dubbo.common.Version;
 4 import com.alibaba.dubbo.config.ApplicationConfig;
 5 import com.alibaba.dubbo.config.ConsumerConfig;
 6 import com.alibaba.dubbo.config.ModuleConfig;
 7 import com.alibaba.dubbo.config.MonitorConfig;
 8 import com.alibaba.dubbo.config.ProtocolConfig;
 9 import com.alibaba.dubbo.config.ProviderConfig;
10 import com.alibaba.dubbo.config.RegistryConfig;
11 import com.alibaba.dubbo.config.spring.AnnotationBean;
12 import com.alibaba.dubbo.config.spring.ReferenceBean;
13 import com.alibaba.dubbo.config.spring.ServiceBean;
14 
15 import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
16 
17 public class DubboNamespaceHandler extends NamespaceHandlerSupport {
18 
19     static {
20         Version.checkDuplicate(DubboNamespaceHandler.class);
21     }
22 
23     public void init() {
24         registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
25         registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
26         registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));
27         registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
28         registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
29         registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
30         registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
31         registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
32         registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
33         registerBeanDefinitionParser("annotation", new DubboBeanDefinitionParser(AnnotationBean.class, true));
34     }
35 }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

功能与上一节完全相似。这里可以看出，dubbo自定义了10个xml元素（也可以从xsd中看出）。从上边也可以看出，<dubbo:service>会被解析成ServiceBean，**该bean极其重要**。

 

**五 META_INF/spring.handlers**

```
1 http\://code.alibabatech.com/schema/dubbo=com.alibaba.dubbo.config.spring.schema.DubboNamespaceHandler
```

与上一节完全相似。
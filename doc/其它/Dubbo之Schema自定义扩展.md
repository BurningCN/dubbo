# [Dubbo之Schema自定义扩展](https://segmentfault.com/a/1190000020035859)

[dubbo](https://segmentfault.com/t/dubbo)

发布于 2019-08-11

![img](https://sponsor.segmentfault.com/lg.php?bannerid=0&campaignid=0&zoneid=25&loc=https%3A%2F%2Fsegmentfault.com%2Fa%2F1190000020035859&referer=https%3A%2F%2Fwww.baidu.com%2Flink%3Furl%3Dy8tSRVmECecUMXMICGKTvO42F7pLRPDvWbhisM6mitacEDvTP2cIAW147QIxiF570fJaBnF5gjqOWAKpemb6sK%26wd%3D%26eqid%3Dc64d2c4500020bef000000035fe97ff8&cb=c68b83778f)

## Dubbo之Schema自定义扩展

`Dubbo`基于`Spring`提供的`NamespaceHandler`和`BeanDefinitionParser`来扩展了自己`XML Schemas`，一切的功能也都是基于这一点来进行的。

### 基于Spring的自定义Schema扩展

#### 1.Maven依赖

```xml
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-context</artifactId>
  <version>5.1.6.RELEASE</version>
</dependency>
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-context</artifactId>
  <version>5.1.6.RELEASE</version>
</dependency>
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-aop</artifactId>
  <version>5.1.6.RELEASE</version>
</dependency>
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-beans</artifactId>
  <version>5.1.6.RELEASE</version>
</dependency>
```

#### 2.创建实体类

```java
package com.lg.spring;

public class People {

    private String name;

    private int age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
```

#### 3.创建Schema文件

在`META-INF`目录下创建名为`people.xsd`的文件

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsd:schema xmlns="http://www.people.com/schema/people"
            xmlns:xsd="http://www.w3.org/2001/XMLSchema"
            xmlns:beans="http://www.springframework.org/schema/beans"
            targetNamespace="http://www.people.com/schema/people"
            elementFormDefault="qualified"
            attributeFormDefault="unqualified">
    <xsd:import namespace="http://www.springframework.org/schema/beans"/>
    <xsd:element name="student">
        <xsd:complexType>
            <xsd:complexContent>
                <xsd:extension base="beans:identifiedType">

                    <xsd:attribute name="name" type="xsd:string">
                        <xsd:annotation>
                            <xsd:documentation>姓名</xsd:documentation>
                        </xsd:annotation>
                    </xsd:attribute>

                    <xsd:attribute name="age" type="xsd:string">
                        <xsd:annotation>
                            <xsd:documentation>年龄</xsd:documentation>
                        </xsd:annotation>
                    </xsd:attribute>

                </xsd:extension>
            </xsd:complexContent>
        </xsd:complexType>
    </xsd:element>
</xsd:schema>
```

#### 4.创建相应的`NamespaceHandler`

```java
package com.lg.spring;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class PeopleNamespaceHandler extends NamespaceHandlerSupport {

    @Override
    public void init() {
        registerBeanDefinitionParser("person", new PeopleBeanDefinitionParser());
    }
}
```

#### 5.创建解析Schema文件的`BeanDefinitionParser`

```java
package com.lg.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

public class PeopleBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    protected Class getBeanClass(Element element) {
        return People.class;
    }

    protected void doParse(Element element, BeanDefinitionBuilder bean) {
        String name = element.getAttribute("name");
        bean.addPropertyValue("name", name);

        String age = element.getAttribute("age");
        if (StringUtils.hasText(age)) {
            bean.addPropertyValue("age", Integer.valueOf(age));
        }
    }
}
```

#### 6.`spring.handler`和`spring.schema`

`META-INF`目录下创建`spring.handler`如下：

```
http\://www.people.com/schema/people=com.lg.spring.PeopleNamespaceHandler
```

`META-INF`目录下创建`spring.schema`如下：

```
http\://www.people.com/schema/people.xsd=META-INF/people.xsd
```

#### 7.测试

在`META-INF`下创建`applicationContext.xml`，如下：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
       xmlns:people="http://www.people.com/schema/people"
       xsi:schemaLocation="http://www.springframework.org/schema/beans 
           http://www.springframework.org/schema/beans/spring-beans.xsd 
           http://www.people.com/schema/people 
           http://www.people.com/schema/people.xsd">

    <people:person id="person1" name="zhangsan" age="18"/>

    <bean id="person2" class="com.lg.spring.People">
        <property name="name" value="lisi"/>
        <property name="age" value="23"/>
    </bean>

</beans>
```

测试类：

```java
package com.lg.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class PeopleSchemaTest {

    public static void main(String[] args) {

        ApplicationContext ctx = new ClassPathXmlApplicationContext("/META-INF/applicationContext.xml");
        People person1 = (People) ctx.getBean("person1");
        People person2 = (People) ctx.getBean("person2");

        System.out.println("name: " + person1.getName() + " age :" + person1.getAge());
        System.out.println("name: " + person2.getName() + " age :" + person2.getAge());
    }
}
```

#### 8.结果

```
name: zhangsan age :18
name: lisi age :23
```

### Dubbo扩展的Schema

#### 1.Schema文件

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
            xmlns:beans="http://www.springframework.org/schema/beans"
            xmlns:tool="http://www.springframework.org/schema/tool"
            xmlns="http://dubbo.apache.org/schema/dubbo"
            targetNamespace="http://dubbo.apache.org/schema/dubbo">

    <xsd:import namespace="http://www.w3.org/XML/1998/namespace"/>
    <xsd:import namespace="http://www.springframework.org/schema/beans" schemaLocation="http://www.springframework.org/schema/beans/spring-beans.xsd"/>
    <xsd:import namespace="http://www.springframework.org/schema/tool"/>

    <xsd:annotation>
        <xsd:documentation>
            <![CDATA[ Namespace support for the dubbo services provided by dubbo framework. ]]></xsd:documentation>
    </xsd:annotation>
    
  ...
  
    <xsd:element name="protocol" type="protocolType">
        <xsd:annotation>
            <xsd:documentation><![CDATA[ Service provider config ]]></xsd:documentation>
            <xsd:appinfo>
                <tool:annotation>
                    <tool:exports type="org.apache.dubbo.config.ProtocolConfig"/>
                </tool:annotation>
            </xsd:appinfo>
        </xsd:annotation>
    </xsd:element>

    <xsd:element name="service" type="serviceType">
        <xsd:annotation>
            <xsd:documentation><![CDATA[ Export service config ]]></xsd:documentation>
            <xsd:appinfo>
                <tool:annotation>
                    <tool:exports type="org.apache.dubbo.config.ServiceConfig"/>
                </tool:annotation>
            </xsd:appinfo>
        </xsd:annotation>
    </xsd:element>

    <xsd:element name="reference" type="referenceType">
        <xsd:annotation>
            <xsd:documentation><![CDATA[ Reference service config ]]></xsd:documentation>
            <xsd:appinfo>
                <tool:annotation>
                    <tool:exports type="org.apache.dubbo.config.ReferenceConfig"/>
                </tool:annotation>
            </xsd:appinfo>
        </xsd:annotation>
    </xsd:element>
  
    ...
  
</xsd:schema>
```

内容过长这里只粘处`service`、`reference`和`protocol`三者

#### 2.`spring.handler`和`spring.schema`文件

```
http\://dubbo.apache.org/schema/dubbo=org.apache.dubbo.config.spring.schema.DubboNamespaceHandler
http\://code.alibabatech.com/schema/dubbo=org.apache.dubbo.config.spring.schema.DubboNamespaceHandler
http\://dubbo.apache.org/schema/dubbo/dubbo.xsd=META-INF/dubbo.xsd
http\://code.alibabatech.com/schema/dubbo/dubbo.xsd=META-INF/compat/dubbo.xsd
```

有两组是为了兼容`Dubbo`早期的版本。

#### 3.DubboBeanDefinitionParser

```java
package org.apache.dubbo.config.spring.schema;

...

import static org.apache.dubbo.common.constants.CommonConstants.HIDE_KEY_PREFIX;

public class DubboBeanDefinitionParser implements BeanDefinitionParser {
  
  ...
  
    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        return parse(element, parserContext, beanClass, required);
    }
}
```

#### 4.DubboNamespaceHandler

```java
package org.apache.dubbo.config.spring.schema;

import org.apache.dubbo.common.Version;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.ModuleConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.MetricsConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.spring.ConfigCenterBean;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.ServiceBean;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class DubboNamespaceHandler extends NamespaceHandlerSupport {

    static {
        Version.checkDuplicate(DubboNamespaceHandler.class);
    }

    @Override
    public void init() {
        registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
        registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
        registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));
        registerBeanDefinitionParser("config-center", new DubboBeanDefinitionParser(ConfigCenterBean.class, true));
        registerBeanDefinitionParser("metadata-report", new DubboBeanDefinitionParser(MetadataReportConfig.class, true));
        registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
        registerBeanDefinitionParser("metrics", new DubboBeanDefinitionParser(MetricsConfig.class, true));
        registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
        registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
        registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
        registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
        registerBeanDefinitionParser("annotation", new AnnotationBeanDefinitionParser());
    }
}
```

可以看出Dubbo所有的组件都是由`DubboBeanDefinitionParser`来解析的，最后解析对应的`ServiceBean`等对象。
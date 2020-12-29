dubbo自定义了很多xml标签，例如<dubbo:application>，那么这些自定义标签是怎么与spring结合起来的呢？我们先看一个简单的例子。

![img](https://images2017.cnblogs.com/blog/866881/201710/866881-20171002121343896-1565569009.png)

**一 编写模型类**

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```java
 1 package com.hulk.testdubbo.model;
 2 
 3 public class Hero {
 4     private  String name;
 5     private  int    age;
 6 
 7     public String getName() {
 8         return name;
 9     }
10 
11     public void setName(String name) {
12         this.name = name;
13     }
14 
15     public int getAge() {
16         return age;
17     }
18 
19     public void setAge(int age) {
20         this.age = age;
21     }
22 }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

 

**二 定义xsd文件**

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```xml
 1 <xsd:schema
 2         xmlns="http://hulk.com/schema"
 3         xmlns:xsd="http://www.w3.org/2001/XMLSchema"
 4         targetNamespace="http://hulk.com/schema">
 5     <xsd:complexType name="elementname1complexType">
 6         <xsd:attribute name="name" type="xsd:string">
 7             <xsd:annotation>
 8                 <xsd:documentation><![CDATA[ The elementname1 name. ]]></xsd:documentation>
 9             </xsd:annotation>
10         </xsd:attribute>
11         <xsd:attribute name="age" type="xsd:int">
12             <xsd:annotation>
13                 <xsd:documentation><![CDATA[ The elementname1 age. ]]></xsd:documentation>
14             </xsd:annotation>
15         </xsd:attribute>
16     </xsd:complexType>
17 
18     <xsd:element name="elementname1" type="elementname1complexType">
19         <xsd:annotation>
20             <xsd:documentation><![CDATA[ elementname1的文档 ]]></xsd:documentation>
21         </xsd:annotation>
22     </xsd:element>
23 </xsd:schema>
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

说明：

- 定义**targetNamespace**（目标命名空间），xmlns的值要与这个相同
- xsd:element定义的就是将来会在xml文件中用到的元素，例如<dubbo:application>中的application
- xsd:attribute定义的就是模型类中的属性，例如<dubbo:application name="xxx">中的name，并且可以指定属性类型，进而起到检测的作用（当我们定义的是int，如果在xml中的值是非int型的，直接会报错）。

 

**三 编写spring.schemas**

作用：该文件用来指定xsd文件的位置。

```
http\://hulk.com/schema/hero.xsd=META-INF/hero.xsd
```

**注意**：等号左边要与xsd文件中的targetNamespace相同。

 

**四 编写BeanDefinition解析器**

作用：主要用来解析自定义的xml标签。

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```java
 1 package com.hulk.testdubbo.schema;
 2 
 3 import org.springframework.beans.factory.config.BeanDefinition;
 4 import org.springframework.beans.factory.support.BeanDefinitionRegistry;
 5 import org.springframework.beans.factory.support.RootBeanDefinition;
 6 import org.springframework.beans.factory.xml.BeanDefinitionParser;
 7 import org.springframework.beans.factory.xml.ParserContext;
 8 import org.w3c.dom.Element;
 9 
10 public class HeroBeanDefinitionParser implements BeanDefinitionParser {
11     private final Class<?> beanClass;
12 
13     public HeroBeanDefinitionParser(Class<?> beanClass) {
14         this.beanClass = beanClass;
15     }
16 
17     public BeanDefinition parse(Element element, ParserContext parserContext) {
18         RootBeanDefinition beanDefinition = new RootBeanDefinition();
19         beanDefinition.setBeanClass(beanClass);
20         beanDefinition.setLazyInit(false);
21         beanDefinition.getPropertyValues().add("name", element.getAttribute("name"));
22         beanDefinition.getPropertyValues().add("age", element.getAttribute("age"));
23         BeanDefinitionRegistry beanDefinitionRegistry = parserContext.getRegistry();
24         beanDefinitionRegistry.registerBeanDefinition(beanClass.getName(),beanDefinition);//注册bean到BeanDefinitionRegistry中
25         return beanDefinition;
26     }
27 }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

 

**五 编写命名空间处理器**

作用：主要用来注册BeanDefinition解析器。

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```java
 1 package com.hulk.testdubbo.schema;
 2 
 3 import com.hulk.testdubbo.model.Hero;
 4 import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
 5 
 6 public class HeroNamespaceHandler extends NamespaceHandlerSupport {
 7     public void init() {
 8         registerBeanDefinitionParser("elementname1", new HeroBeanDefinitionParser(Hero.class));
 9     }
10 }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

说明：通常为每一个xsd:element都要注册一个BeanDefinitionParser。

 

**六 编写spring.handlers文件**

作用：主要用于关联命名空间处理器和xsd中的targetNamespace。

```
http\://hulk.com/schema=com.hulk.testdubbo.schema.HeroNamespaceHandler
```

说明：key是xsd文件中的targetNamespace。

 

**七 测试 - 编写hero.xml**

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```xml
1 <?xml version="1.0" encoding="UTF-8"?>
2 <beans xmlns="http://www.springframework.org/schema/beans"
3        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
4        xmlns:hero="http://hulk.com/schema"
5        xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
6        http://hulk.com/schema http://hulk.com/schema/hero.xsd">
7     <hero:elementname1 name="xiaona" age="18"/>
8 </beans>
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

说明：

- xmlns:hero的value是xsd文件中的targetNamespace。
- xmlns:hero可以写成xmlns:xxx，此时<hero:elementname1/>就要写成<xxx:elementname1/>

 

**八 测试 - 编写测试主类**

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```java
 1 package com.hulk.testdubbo.test;
 2 
 3 import com.hulk.testdubbo.model.Hero;
 4 import org.springframework.context.ApplicationContext;
 5 import org.springframework.context.support.ClassPathXmlApplicationContext;
 6 
 7 public class Main {
 8     public static void main(String[] args) {
 9         ApplicationContext applicationContext = new ClassPathXmlApplicationContext("hero.xml");
10         Hero hero = (Hero) applicationContext.getBean(Hero.class.getName());
11         System.out.println("name: " + hero.getName() + " age: " + hero.getAge());
12     }
13 }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

 

如何在spring中自定义xml标签的方法就结束了。在实际中，随着注解和javaconfg的盛行，xml的方式渐渐的会淡出舞台，但是spring的启动流程还是会的。来看一下上述代码涉及到的流程。

- 使用ResourceLoader将配置文件xml装载为Resource对象；
- 使用BeanDefinitionReader解析配置信息：将每一个<bean>解析为一个BeanDefinition对象，然后存储到BeanDefinitionRegistry中
  - 实际上是BeanDefinitionReader调用BeanDefinitionParser进行了解析操作，解析完成后注册到BeanDefinitionRegistry（代码看上边的HeroBeanDefinitionParser）
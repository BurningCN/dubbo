**001.Comparator的设计**。Prioritized接口继承Comparable接口并以default默认方法的方式实现了，以及提供了外界直接访问的Comparator对象，以及getPriority方法可以被子类重写实现不同子类的自己的优先级定义。详见Prioritized、PrioritizedTest

**002.泛型设计。**Converter<S, T>接口本身支持S->T类型的转化，但是StringConverter子接口的引入限定了仅支持String->T的转化，子类只能传递一个T。

**003.类型转化设计。**Converter SPI接口有多个扩展类，支持String->T的转化。用户给定的S->T是否支持转化，内部用了很多泛型参数、获取父类、父接口等相关API的操作以及结合lambda流式处理和过滤。比如StringToBooleanConverter在accept判断的时候就会获取String.class和Integer.class。



004.模板方法洒水多
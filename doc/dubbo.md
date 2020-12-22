**001.Comparator的设计**。Prioritized接口继承Comparable接口并以default默认方法的方式实现了，以及提供了外界直接访问的Comparator对象，以及getPriority方法可以被子类重写实现不同子类的自己的优先级定义。详见Prioritized、PrioritizedTest

**002.泛型设计。**Converter<S, T>接口本身支持S->T类型的转化，但是StringConverter子接口的引入限定了仅支持String->T的转化，子类只能传递一个T。

**003.类型转化设计。**Converter SPI接口有多个扩展类，支持String->T的转化。用户给定的S->T是否支持转化，内部用了很多泛型参数、获取父类、父接口等相关API的操作以及结合lambda流式处理和过滤。比如StringToBooleanConverter在accept判断的时候就会获取String.class和Integer.class。

**004.MultiConverter和Converter的设计。**都是accept+convert+getConverter/find几个方法。以及都继承了Prioritized，用以在find的时候优先对优先级高的进行accept判定。StringToIterableConverter的Convert方法用了模板方法设计模式。

**005.Completable异步编程技巧。**ScheduledCompletableFuture.submit提交一个任务，返回CompletableFuture对象，把这个future填充到集合里面，在某处会遍历该集合，判断future.isDone并作对应的处理，比如isDone=false，进行future.cancel。isDone完成的时间点在future.complete(xx)的触发，详见ScheduledCompletableFuture。**实现一个带有返回结果的任务**不一定使用callable，可以直接用CompletableFuture。

**006.自定义线程池的拒绝策略。**详见AbortPolicyWithReport。在原生AbortPolicy的基础上做了一些扩展功能：log、dumpJStack、dispatchThreadPoolExhaustedEvent。以及涉及到了事件监听模型，在线程池Exhausted的时候，构建ThreadPoolExhaustedEvent并派遣给对应的监听器处理。

**007.ThreadPool的设计**。ThreadPool是一个**SPI**接口，扩展类给出了不同线程池的实现，**核心还是利用ThreadPoolExecutor**，只是参数不同，参数大都从URL取得，以及用了**自定义的线程工厂、拒绝策略**等。特别看了下**EagerThreadPoolExecutor和TaskQueue**，前者属于自定义的线程池，**重写了afterExecute和execute方法做一些前置后置操作**，以及在发生拒绝异常的时候重试投递到TaskQueue，且**TaskQueue的offer重写了，使得不一定任务队列满了才会创建超过核心线程数大小的线程。**


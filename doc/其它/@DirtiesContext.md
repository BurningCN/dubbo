测试又随机挂了，该怎么办？加上 `@DirtiesContext` 试试。真棒，修好了。

WAIT! WAIT! WAIT! 为什么加 `@DirtiesContext` 就修好，有没有什么副作用？

> `@DirtiesContext` is a spring test annotation which is used to indicate that the application context cached should be removed and reloaded after each test run. The application context removed will also be closed.

如上面所述，`@DirtiesContext` 会导致`application context`不被缓存，也就是说，有可能会对测试运行的速度有影响。官方文档介绍[@DirtiesContext](https://docs.spring.io/spring/docs/current/spring-framework-reference/testing.html#dirtiescontext)：

> ![img](https://segmentfault.com/img/bVbdLwK?w=2020&h=1330)

如果在测试类上，使用`@DirtiesContext`注解，待整个测试类的所有测试执行结束后，该测试的`application context`会被关闭，同时缓存会清除。`@DirtiesContext`分为`method-level`和`class-level`。

- `method-level`只有当`@DirtiesContext`注解设置在**`test method`**上的才会生效，`methodMode`有两种配置：`BEFORE_METHOD`、`AFTER_METHOD`，默认是`AFTER_METHOD`。
- `class-level`只有当`@DirtiesContext`注解设置在**`test class`**上的才会生效，`classMode`有四种配置：`BEFORE_CLASS`、`BEFORE_EACH_TEST_METHOD`、`AFTER_EACH_TEST_METHOD`、`AFTER_CLASS`，默认是`AFTER_CLASS`。
- 生命周期：![图片描述](https://segmentfault.com/img/bVbdLwL?w=295&h=554)

### 写在最后

虽然使用`@DirtiesContext`，可以保证每个`test class`的执行上下文的独立性、隔离性，但是也会有让测试运行速度变慢的副作用。所以在使用`@DirtiesContext`前，弄清楚你是否真的需要使用它。

> [原文链接](http://aikin.me/2018/04/02/do-you-really-need-dirties-context-annotation/)
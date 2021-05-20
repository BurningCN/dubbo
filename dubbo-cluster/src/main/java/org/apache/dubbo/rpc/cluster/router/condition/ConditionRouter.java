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
package org.apache.dubbo.rpc.cluster.router.condition;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.router.AbstractRouter;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.dubbo.common.constants.CommonConstants.ENABLED_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.HOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHOD_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.ADDRESS_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.FORCE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.PRIORITY_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.RULE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.RUNTIME_KEY;

/**
 * ConditionRouter
 */
public class ConditionRouter extends AbstractRouter {
    public static final String NAME = "condition";

    private static final Logger logger = LoggerFactory.getLogger(ConditionRouter.class);
    // 正则验证路由规则 两个括号两个部分，前者是匹配$ ! = ,后者是匹配这三个字符之外的
    protected static final Pattern ROUTE_PATTERN = Pattern.compile("([&!=,]*)\\s*([^&!=,\\s]+)");
    protected Map<String, MatchPair> whenCondition;
    protected Map<String, MatchPair> thenCondition;

    private boolean enabled;

    public ConditionRouter(String rule, boolean force, boolean enabled) {
        this.force = force;
        this.enabled = enabled;
        this.init(rule);
    }

    public ConditionRouter(URL url) {
        this.url = url;
        this.priority = url.getParameter(PRIORITY_KEY, 0);
        this.force = url.getParameter(FORCE_KEY, false);
        this.enabled = url.getParameter(ENABLED_KEY, true);
        // 从 URL 里获取 rule 的字符串格式的规则，解析规则在 ConditionRouter#init 初始化方法中
        init(url.getParameterAndDecoded(RULE_KEY));
    }

    public void init(String rule) {
        try {
            if (rule == null || rule.trim().length() == 0) {
                throw new IllegalArgumentException("Illegal route rule!");
            }
            // 去掉 consumer. 和 provider. 的标识
            rule = rule.replace("consumer.", "").replace("provider.", "");
            // 获取 消费者匹配条件 和 提供者地址匹配条件 的分隔符   host = 10.20.153.10 => host = 10.20.153.11
            int i = rule.indexOf("=>");
            // 消费者匹配条件
            String whenRule = i < 0 ? null : rule.substring(0, i).trim();
            // 提供者地址匹配条件
            String thenRule = i < 0 ? rule.trim() : rule.substring(i + 2).trim();
            // 解析消费者路由规则
            Map<String, MatchPair> when = StringUtils.isBlank(whenRule) || "true".equals(whenRule) ? new HashMap<>() : parseRule(whenRule);
            // 解析提供者路由规则 这里和上面的不一样，这里是为空或false的时候使用null赋值，前面的是使用new HashMap
            Map<String, MatchPair> then = StringUtils.isBlank(thenRule) || "false".equals(thenRule) ? null : parseRule(thenRule);
            // NOTE: It should be determined on the business level whether the `When condition` can be empty or not. 应该在业务级别上确定“When条件”是否为空
            this.whenCondition = when;
            this.thenCondition = then;
            // 以路由规则字符串中的=>为分隔符，将消费者匹配条件和提供者匹配条件分割，解析两个路由规则后，赋值给当前对象的变量。调用 parseRule 方法来解析消费者和服务者路由规则。
        } catch (ParseException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Map<String, MatchPair> parseRule(String rule)
            throws ParseException {
        /**
         * 条件变量和条件变量值的映射关系
         * 比如 host = 127.0.0.1 则保存着 host 和 127.0.0.1 的映射关系
         */
        Map<String, MatchPair> condition = new HashMap<String, MatchPair>();
        if (StringUtils.isBlank(rule)) {
            return condition;
        }
        // Key-Value pair, stores both match and mismatch conditions  键-值对，存储匹配和不匹配条件
        MatchPair pair = null;
        // Multiple values
        Set<String> values = null;
        // 通过正则表达式匹配路由规则，ROUTE_PATTERN = ([&!=,]*)\s*([^&!=,\s]+)
        // 这个表达式看起来不是很好理解，第一个括号内的表达式用于匹配"&", "!", "=" 和 "," 等符号。
        // 第二括号内的用于匹配英文字母，数字等字符。举个例子说明一下：
        //    host = 2.2.2.2 & host != 1.1.1.1 & method = hello
        // 匹配结果如下：
        //     括号一      括号二
        // 1.  null       host
        // 2.   =         2.2.2.2
        // 3.   &         host
        // 4.   !=        1.1.1.1
        // 5.   &         method
        // 6.   =         hello
        final Matcher matcher = ROUTE_PATTERN.matcher(rule);
        while (matcher.find()) { // Try to match one by one
            // 获取正则前部分匹配（第一个括号）的内容
            String separator = matcher.group(1);
            // 获取正则后部分匹配（第二个括号）的内容
            String content = matcher.group(2);
            // 分隔符为空，表示匹配的是表达式的开始部分
            // Start part of the condition expression.
            if (StringUtils.isEmpty(separator)) {
                pair = new MatchPair();  // 创建 MatchPair 对象
                condition.put(content, pair); // 存储 <匹配项, MatchPair> 键值对，比如 <host, MatchPair>
            }
            // The KV part of the condition expression
            // 如果分隔符为 &，则当前 content 为条件变量值 表明接下来也是一个条件
            else if ("&".equals(separator)) {
                // 当前 content 是条件变量，用来做映射集合的 key 的，尝试从 condition 获取 MatchPair 如果没有则添加一个元素
                if (condition.get(content) == null) {
                    pair = new MatchPair();
                    condition.put(content, pair); // 未获取到 MatchPair，重新创建一个，并放入 condition 中
                } else {
                    pair = condition.get(content);
                }
            }
            // 如果当前分割符是 = ，则当前 content 为条件变量值
            // The Value in the KV part.
            else if ("=".equals(separator)) {
                if (pair == null) {
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());
                }
                // 由于 pair 还没有被重新初始化，所以还是上一个条件变量的对象，所以可以将当前条件变量值在引用对象上赋值
                values = pair.matches;// 赋值给values的作用是为了下面的逗号处理
                values.add(content);
            }
            // 如果当前分割符是 = ，则当前 content 也是条件变量值
            // The Value in the KV part.
            else if ("!=".equals(separator)) {
                if (pair == null) {
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());
                }
                // 与 = 时同理
                values = pair.mismatches;// 赋值给values的作用是为了下面的逗号处理
                values.add(content);
            }
            // 如果当前分割符为 ','，则当前 content 也为条件变量值
            // The Value in the KV part, if Value have more than one items.
            else if (",".equals(separator)) { // Should be separated by ','
                if (values == null || values.isEmpty()) {
                    throw new ParseException("Illegal route rule \""
                            + rule + "\", The error char '" + separator
                            + "' at index " + matcher.start() + " before \""
                            + content + "\".", matcher.start());
                }
                // 直接向条件变量值集合中添加数据
                values.add(content);
            } else {
                throw new ParseException("Illegal route rule \"" + rule
                        + "\", The error char '" + separator + "' at index "
                        + matcher.start() + " before \"" + content + "\".", matcher.start());
            }
        }
        return condition;
    }
    // 以上就是路由规则的解析逻辑，该逻辑由正则表达式和一个 while 循环以及数个条件分支组成。下面通过一个示例对解析逻辑进行演绎。示例为 host = 2.2.2.2 & host != 1.1.1.1 & method = hello。正则解析结果如下：
    //
    //    括号一      括号二
    //1.  null       host
    //2.   =         2.2.2.2
    //3.   &         host
    //4.   !=        1.1.1.1
    //5.   &         method
    //6.   =         hello
    //现在线程进入 while 循环：
    //
    //第一次循环：分隔符 separator = null，content = “host”。此时创建 MatchPair 对象，并存入到 condition 中，condition = {“host”: MatchPair@123}
    //
    //第二次循环：分隔符 separator = “="，content = “2.2.2.2”，pair = MatchPair@123。此时将 2.2.2.2 放入到 MatchPair@123 对象的 matches 集合中。
    //
    //第三次循环：分隔符 separator = “&"，content = “host”。host 已存在于 condition 中，因此 pair = MatchPair@123。
    //
    //第四次循环：分隔符 separator = “!="，content = “1.1.1.1”，pair = MatchPair@123。此时将 1.1.1.1 放入到 MatchPair@123 对象的 mismatches 集合中。
    //
    //第五次循环：分隔符 separator = “&"，content = “method”。condition.get(“method”) = null，因此新建一个 MatchPair 对象，并放入到 condition 中。此时 condition = {“host”: MatchPair@123, “method”: MatchPair@ 456}
    //
    //第六次循环：分隔符 separator = “="，content = “2.2.2.2”，pair = MatchPair@456。此时将 hello 放入到 MatchPair@456 对象的 matches 集合中。
    //
    //循环结束，此时 condition 的内容如下：
    //
    //{
    //    "host": {
    //        "matches": ["2.2.2.2"],
    //        "mismatches": ["1.1.1.1"]
    //    },
    //    "method": {
    //        "matches": ["hello"],
    //        "mismatches": []
    //    }
    //}
    //路由规则的解析过程稍微有点复杂，大家可通过 ConditionRouter 的测试类对该逻辑进行测试。并且找一个表达式，对照上面的代码走一遍，加深理解。

    @Override
    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation)
            throws RpcException {
        if (!enabled) {
            return invokers;
        }

        if (CollectionUtils.isEmpty(invokers)) {
            return invokers;
        }
        try {
            // 先对服务消费者条件进行匹配，如果匹配失败，表明服务消费者 url 不符合匹配规则，
            // 无需进行后续匹配，直接返回 Invoker 列表即可。比如下面的规则：
            //     host = 10.20.153.10 => host = 10.0.0.10
            // 这条路由规则希望 IP 为 10.20.153.10 的服务消费者调用 IP 为 10.0.0.10 机器上的服务。
            // 当消费者 ip 为 10.20.153.11 时，matchWhen 返回 false，表明当前这条路由规则不适用于
            // 当前的服务消费者，此时无需再进行后续匹配，直接返回即可。
            if (!matchWhen(url, invocation)) {
                return invokers;
            }
            List<Invoker<T>> result = new ArrayList<Invoker<T>>();
            // 服务提供者匹配条件未配置，表明对指定的服务消费者禁用服务，也就是服务消费者在黑名单中
            if (thenCondition == null) {
                logger.warn("The current consumer in the service blacklist. consumer: " + NetUtils.getLocalHost() + ", service: " + url.getServiceKey());
                return result;
            }
            // 这里可以简单的把 Invoker 理解为服务提供者，现在使用服务提供者匹配规则对
            // Invoker 列表进行匹配
            for (Invoker<T> invoker : invokers) {
                // 若匹配成功，表明当前 Invoker 符合服务提供者匹配规则。
                // 此时将 Invoker 添加到 result 列表中
                if (matchThen(invoker.getUrl(), url)) {
                    result.add(invoker);
                }
            }
            // 返回匹配结果，如果 result 为空列表，且 force = true，表示强制返回空列表，否则返回原始集合
            // 否则路由结果为空的路由规则将自动失效
            if (!result.isEmpty()) {
                return result;
            } else if (force) {
                logger.warn("The route result is empty and force execute. consumer: " + NetUtils.getLocalHost() + ", service: " + url.getServiceKey() + ", router: " + url.getParameterAndDecoded(RULE_KEY));
                return result;
            }
        } catch (Throwable t) {
            logger.error("Failed to execute condition router rule: " + getUrl() + ", invokers: " + invokers + ", cause: " + t.getMessage(), t);
        } // 原样返回，此时 force = false，表示该条路由规则失效
        return invokers;
        // route 方法先是调用 matchWhen 对服务消费者进行匹配，如果匹配失败，直接返回 Invoker 列表。如果匹配成功，再对服务提供者进行匹配，匹配逻辑封装在了 matchThen 方法中。下面来看一下这两个方法的逻辑：
    }

    @Override
    public boolean isRuntime() {
        // We always return true for previously defined Router, that is, old Router doesn't support cache anymore.
//        return true;
        return this.url.getParameter(RUNTIME_KEY, false);
    }

    @Override
    public URL getUrl() {
        return url;
    }

    boolean matchWhen(URL url, Invocation invocation) {
        // 服务消费者条件为 null 或空，均返回 true，比如：
        //     => host = 172.22.3.91
        // 表示所有的服务消费者都可以调用 IP 为 172.22.3.91 的机器上的服务
        return CollectionUtils.isEmptyMap(whenCondition) || matchCondition(whenCondition, url, null, invocation);// 进行条件匹配
    }

    private boolean matchThen(URL url, URL param) {
        // 服务提供者条件为 null 或空，表示禁用服务
        return CollectionUtils.isNotEmptyMap(thenCondition) && matchCondition(thenCondition, url, param, null);
    }
    // 这两个方法长的有点像，不过逻辑上还是有差别的，大家注意看。这两个方法均调用了 matchCondition 方法，但它们所传入的参数是不同的。这个需要特别注意一下，
    // 不然后面的逻辑不好弄懂。下面我们对这几个参数进行溯源。matchWhen 方法向 matchCondition 方法传入的参数为 [whenCondition, url, null, invocation]，
    // 第一个参数 whenCondition 为服务消费者匹配条件，这个前面分析过。第二个参数 url 源自 route 方法的参数列表，该参数由外部类调用 route 方法时传入。比如：
    // ....
    // 接下来再来看看 matchThen 向 matchCondition 方法传入的参数 [thenCondition, url, param, null]。第一个参数不用解释了。第二个和第
    // 三个参数来自 matchThen 方法的参数列表，这两个参数分别为服务提供者 url 和服务消费者 url。搞清楚这些参数来源后，接下来就可以分析 matchCondition 方法了。

    private boolean matchCondition(Map<String, MatchPair> condition, URL url, URL param, Invocation invocation) {
        // 将服务提供者或消费者 url 转成 Map
        Map<String, String> sample = url.toMap();
        boolean result = false;
        // 遍历 condition 列表
        for (Map.Entry<String, MatchPair> matchPair : condition.entrySet()) {
            String key = matchPair.getKey();// 获取匹配项名称，比如 host、method 等
            String sampleValue;
            // 如果 invocation 不为空，且 key 为 mehtod(s)，表示进行方法匹配
            //get real invoked method name from invocation
            if (invocation != null && (METHOD_KEY.equals(key) || METHODS_KEY.equals(key))) {
                // 从 invocation 获取被调用方法的名称
                sampleValue = invocation.getMethodName();
                // address和host的区别就是，address是ip:port，host就是ip
            } else if (ADDRESS_KEY.equals(key)) {
                sampleValue = url.getAddress();
            } else if (HOST_KEY.equals(key)) {
                sampleValue = url.getHost();
            } else {
                // 从服务提供者或消费者 url 中获取指定字段值，比如 host、application 等
                sampleValue = sample.get(key);
                if (sampleValue == null) {
                    // 尝试通过 default.xxx 获取相应的值
                    sampleValue = sample.get(key);
                }
            }
            // --------------------✨ 分割线 ✨-------------------- //
            if (sampleValue != null) {
                // 调用 MatchPair 的 isMatch 方法进行匹配
                if (!matchPair.getValue().isMatch(sampleValue, param)) {
                    // 只要有一个规则匹配失败，立即返回 false 结束方法逻辑
                    return false;
                } else {
                    result = true;
                }
            } else {
                // sampleValue 为空，表明服务提供者或消费者 url 中不包含相关字段。此时如果
                // MatchPair 的 matches 不为空，表示匹配失败，返回 false。比如我们有这样
                // 一条匹配条件 loadbalance = random，假设 url 中并不包含 loadbalance 参数，
                // 此时 sampleValue = null。既然路由规则里限制了 loadbalance 必须为 random，
                // 但 sampleValue = null，明显不符合规则，因此返回 false
                //not pass the condition
                if (!matchPair.getValue().matches.isEmpty()) {
                    return false;
                } else {
                    result = true;
                }
            }
        }
        return result;
        // 如上，matchCondition 方法看起来有点复杂，这里简单说明一下。分割线以上的代码实际上用于获取 sampleValue 的值，分割线以下才是进行条件匹配。条件匹配调用的逻辑封装在 isMatch 中，代码如下：
    }

    protected static final class MatchPair {
        // MatchPair 内部包含了两个 Set 类型的成员变量，分别用于存放匹配和不匹配的条件。这个类两个成员变量会在 parseRule 方法中被用到
        final Set<String> matches = new HashSet<String>();
        final Set<String> mismatches = new HashSet<String>();

        private boolean isMatch(String value, URL param) {
            // 情况一：matches 非空，mismatches 为空
            if (!matches.isEmpty() && mismatches.isEmpty()) {
                // 遍历 matches 集合，检测入参 value 是否能被 matches 集合元素匹配到。
                // 举个例子，如果 value = 10.20.153.11，matches = [10.20.153.*],
                // 此时 isMatchGlobPattern 方法返回 true
                for (String match : matches) {
                    if (UrlUtils.isMatchGlobPattern(match, value, param)) {
                        return true;
                    }
                }
                // 如果所有匹配项都无法匹配到入参，则返回 false
                return false;
            }
            // 情况二：matches 为空，mismatches 非空
            if (!mismatches.isEmpty() && matches.isEmpty()) {
                for (String mismatch : mismatches) {
                    // 只要入参被 mismatches 集合中的任意一个元素匹配到，就返回 false
                    if (UrlUtils.isMatchGlobPattern(mismatch, value, param)) {
                        return false;
                    }
                }
                // mismatches 集合中所有元素都无法匹配到入参，此时返回 true
                return true;
            }

            // 情况三：matches 非空，mismatches 非空
            if (!matches.isEmpty() && !mismatches.isEmpty()) {
                //when both mismatches and matches contain the same value, then using mismatches first
                // matches 和 mismatches 均为非空，此时优先使用 mismatches 集合元素对入参进行匹配。
                // 只要 mismatches 集合中任意一个元素与入参匹配成功，就立即返回 false，结束方法逻辑
                for (String mismatch : mismatches) {
                    if (UrlUtils.isMatchGlobPattern(mismatch, value, param)) {
                        return false;
                    }
                }// mismatches 集合元素无法匹配到入参，此时再使用 matches 继续匹配
                for (String match : matches) {
                    // 只要 matches 集合中任意一个元素与入参匹配成功，就立即返回 true
                    if (UrlUtils.isMatchGlobPattern(match, value, param)) {
                        return true;
                    }
                }
                // 全部失配，则返回 false
                return false;
            }
            // 情况四：matches 和 mismatches 均为空，此时返回 false
            return false;
        }
    }
    //isMatch 方法逻辑比较清晰，由三个条件分支组成，用于处理四种情况。这里对四种情况下的匹配逻辑进行简单的总结，如下：
    //
    //条件	过程
    //情况一	matches 非空，mismatches 为空	遍历 matches 集合元素，并与入参进行匹配。只要有一个元素成功匹配入参，即可返回 true。若全部失配，则返回 false。
    //情况二	matches 为空，mismatches 非空	遍历 mismatches 集合元素，并与入参进行匹配。只要有一个元素成功匹配入参，立即 false。若全部失配，则返回 true。
    //情况三	matches 非空，mismatches 非空	优先使用 mismatches 集合元素对入参进行匹配，只要任一元素与入参匹配成功，就立即返回 false，结束方法逻辑。否则再使用 matches 中的集合元素进行匹配，只要有任意一个元素匹配成功，即可返回 true。若全部失配，则返回 false
    //情况四	matches 为空，mismatches 为空	直接返回 false
    //isMatch 方法是通过 UrlUtils 的 isMatchGlobPattern 方法进行匹配，因此下面我们再来看看 isMatchGlobPattern 方法的逻辑。
}

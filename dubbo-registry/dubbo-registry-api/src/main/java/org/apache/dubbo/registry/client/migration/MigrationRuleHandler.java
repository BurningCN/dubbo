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
package org.apache.dubbo.registry.client.migration;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.cluster.support.migration.MigrationRule;
import org.apache.dubbo.rpc.cluster.support.migration.MigrationStep;

// todo need pr 这个没有用注解
@Activate
// 该类主要是解析 rawRule 的 并进行迁移的
public class MigrationRuleHandler<T> {
    private static final Logger logger = LoggerFactory.getLogger(MigrationRuleHandler.class);

    private MigrationInvoker<T> migrationInvoker;

    public MigrationRuleHandler(MigrationInvoker<T> invoker) {
        this.migrationInvoker = invoker;
    }

    private MigrationStep currentStep;

    public void doMigrate(String rawRule) {
        // 进去 初始rawRule为INIT，返回的rule里面的step为 APPLICATION_FIRST
        MigrationRule rule = MigrationRule.parse(rawRule);

        if (null != currentStep && currentStep.equals(rule.getStep())) {
            if (logger.isInfoEnabled()) {
                logger.info("Migration step is not change. rule.getStep is " + currentStep.name());
            }
            return;
        } else {
            // 赋值
            currentStep = rule.getStep();
        }

        // migrationInvoker 的赋值触发点注意下
        migrationInvoker.setMigrationRule(rule);

        // 默认为false
        if (migrationInvoker.isMigrationMultiRegistry()) {
            // MigrationInvoker为false，子类为true
            if (migrationInvoker.isServiceInvoker()) {
                // 注意和下面的方法区别，带不带 "ServiceDiscovery"，进去
                migrationInvoker.refreshServiceDiscoveryInvoker();
            } else {
                // 进去
                migrationInvoker.refreshInterfaceInvoker();
            }
        } else {
            switch (rule.getStep()) {
                // 默认这个
                case APPLICATION_FIRST:
                    // ServiceDiscoveryMigrationInvoker的方法进去。传入的参数表示是否强制迁移
                    migrationInvoker.migrateToServiceDiscoveryInvoker(false);
                    break;
                case FORCE_APPLICATION:
                    // 这里是FORCE_APPLICATION带有FORCE，所以参数是true
                    migrationInvoker.migrateToServiceDiscoveryInvoker(true);
                    break;
                case FORCE_INTERFACE:
                default:
                    migrationInvoker.fallbackToInterfaceInvoker();
            }
        }
    }
}

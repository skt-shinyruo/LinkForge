# 测试与质量门禁

本文定义 LinkForge 可重复执行的测试层级、共享依赖隔离协议和覆盖率门禁。测试仍围绕同一模块化单体运行；共享容器只优化测试 JVM 内的基础设施生命周期，不改变生产装配或限界上下文边界。

## 标准命令

在 `server/` 目录运行：

```bash
mvn test          # 快速单元、切片、架构与契约测试，不启动 Docker
mvn verify        # 快速测试、JaCoCo 报告和关键行为覆盖率门禁
mvn -Pit verify   # 额外运行真实 MySQL/Redis 集成测试，需要 Docker
```

在 `web/` 目录运行：

```bash
npm test -- --run
npm run test:coverage
npm run build
```

部署镜像 smoke 在仓库根目录运行：

```bash
bash deploy/smoke.sh
```

该脚本使用独立 Compose project，构建实际镜像、等待服务健康并从网关探测首页与公开 CSRF API；不会复用或清理日常开发栈。

## CI Java 版本策略

Java 17 是当前生产基线。CI 中 Java 17 的 `mvn verify` 与 `mvn -Pit verify` 都是合并阻塞门禁；任一失败都不得合并。
Java 21 的 `mvn verify` 目前作为 `continue-on-error` 兼容性观察项，不会掩盖 Java 17 的失败，也暂不作为发布声明。

只有同时满足以下条件，才可删除 Java 21 job 的 `continue-on-error` 并把它提升为阻塞门禁：默认分支连续三次完整
Java 21 `mvn verify` 成功；构建日志中已没有待处理的 JDK、Maven 插件或依赖兼容警告；升级负责人已在变更记录中
确认本地与 CI 使用同一 toolchain。把 Java 21 提升为生产基线属于后续独立决策，不能由一次绿色构建自动触发。

## 集成测试拓扑

`server/integration-tests` 提供一个 opt-in 基类：

- `SharedIntegrationTestSupport`：共享一个 MySQL 和一个 Redis，适合持久化、缓存和端到端测试。

所有集成测试在同一测试 JVM 内复用这两个容器。容器定义只存在于
`SharedIntegrationTopology`，测试类不再声明独立 Testcontainers。新测试若没有明确且已记录的不同拓扑需求，必须接入
该共享入口；确需新拓扑时，应先补充隔离协议和运行成本说明。

Spring Test context cache 的最大容量固定为 `8`，避免完整套件因测试装配变体无界保留上下文。新增装配差异前应先判断
是否能通过现有共享基类和动态属性表达，不能以提高 cache 上限掩盖上下文膨胀。

### Fixture 隔离协议

共享测试基类对每个 opt-in 测试执行以下协议：

1. 通过 JUnit `@ResourceLock("shared-integration-fixture")` 串行执行 shared fixture 测试。
2. 测试前删除 MySQL 中所有业务表数据，并执行 Redis `FLUSHALL`。
3. 测试持锁运行；共享入口默认关闭后台调度，测试可显式调用 job，并可使用事务、HLL、hash、set 和 TTL。

绝大多数数据库主键由业务 Snowflake ID 生成；`integration_events.seq` 与 `redirect_cache_invalidation_outbox.id` 是仅有的业务自增序列。reset 不对业务表泛化执行 `TRUNCATE`：删除事务提交并恢复外键检查后，仅当实时元数据显示这两条序列已经前进时，才对对应空表执行 `ALTER TABLE ... AUTO_INCREMENT = 1`。这样避免 DDL 的隐式提交破坏删除回滚语义，也让未触碰这两张表的 fixture 边界不承担 ALTER 成本；该条件 DDL 的耗时仍计入 `averageResetMillis`。

`FLUSHALL` 会删除 Redis key 和过期时间。Spring 上下文或进程内缓存若承载可变业务状态，测试仍须通过公开用例显式初始化或清理，不能把它们误认为 Redis fixture。

共享 fixture 不依赖 JUnit 方法顺序；`SharedIntegrationTopologyIsolationTest` 使用不同哨兵并重复运行，锁定数据库、Redis 状态以及两条业务自增序列不泄漏。仓库当前不开启集成测试并行执行；若未来开启，只有继承带资源锁共享基类的测试可以操作这套 topology。

Analytics 集成测试直接写入 Redis 聚合或调用 `VisitRecorderPort`，再显式运行 `AnalyticsFlushJob` 验证 V2 marker 的 claim、generation 冲突和 MySQL upsert。该门禁不使用墙钟性能阈值，避免把 CI 主机抖动误判为恢复语义回归。

### 故障诊断

容器启动失败会同时报告 MySQL 和 Redis 镜像名。排查顺序：

1. 确认 Docker daemon 可访问且磁盘空间充足。
2. 检查 Testcontainers/Ryuk 日志及实际映射端口。
3. 检查 MySQL 是否从当前 `database/schema.sql` 完整初始化。
4. 若只在单测间歇失败，先检查测试是否绕过共享扩展或在异步任务仍运行时结束。
5. 若 reset 失败，检查遗留事务、连接池日志与 Redis `FLUSHALL` 返回值。

`SharedIntegrationTopology.metrics()` 暴露本 JVM 的 topology 启动尝试数、容器启动数、启动耗时、reset 次数和平均 reset 耗时；`SharedIntegrationTopologyMetricsListener` 在整个 JUnit launcher session 关闭时只输出一次最终快照，避免随机类顺序产生中途计数。`SharedIntegrationTopologyConcurrencyTest` 以并发调用和重复调用锁定单次启动语义。完整迁移门禁需要连续运行三次，并把中位数及波动与迁移前 `14m36s` 基线对比；未得到可解释改善时不得把共享 topology 宣告为唯一入口。

## 覆盖率原则

覆盖率用于防止高风险边界回退，不代替真实 MySQL/Redis 的并发、恢复或端到端断言。CI 上传各 Maven 模块 JaCoCo 报告和前端 V8 报告；关键认证、HTTP transport、Redirect 缓存、outbox、Analytics job 以及前端 composable/service 另设最低基线。临时豁免必须在仓库内写明负责人、原因和到期日，不能静默降低阈值。

后端 JaCoCo 对 `server/pom.xml` 列出的关键类按模块合计，统一要求行覆盖率不低于 30%、分支覆盖率不低于 9%。
前端 V8 对纳入统计的源码统一要求 statements 60%、branches 65%、functions 55%、lines 60%。阈值调整必须经过
评审，不能通过排除源码绕过门禁。

## Redirect 压测

`bench/redirect` 的 k6 脚本用于容量与性能回归，不属于正确性门禁。压测前应准备独立环境、有效 host/code 和明确的峰值速率、恢复时间目标；不要对共享生产入口直接执行 ramp 或 burst。

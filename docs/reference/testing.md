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

`server/integration-tests` 提供两种 opt-in 基类：

- `SharedIntegrationTestSupport`：业务数据源与 Flyway 都使用 shared primary，适合绝大多数持久化、缓存和端到端测试。
- `SharedReadWriteIntegrationTestSupport`：ShardingSphere 写入使用 shared primary，非事务读取使用独立 replica，适合验证复制延迟与 transactional primary read。

两者在同一测试 JVM 内复用一个 primary、一个 replica 和一个 Redis。集成测试源码迁移已经完成：容器定义只存在于
`SharedIntegrationTopology`，测试类不再声明独立 Testcontainers。新测试若没有明确且已记录的不同拓扑需求，必须接入
上述共享入口；确需新拓扑时，应先补充隔离协议和运行成本说明。

Spring Test context cache 的最大容量固定为 `8`，避免完整套件因测试装配变体无界保留上下文。新增装配差异前应先判断
是否能通过现有共享基类和动态属性表达，不能以提高 cache 上限掩盖上下文膨胀。

### Fixture 隔离协议

`SharedIntegrationFixtureExtension` 对每个 opt-in 测试执行以下协议：

1. 获取 JVM 内公平互斥锁，shared fixture 测试不并行执行。
2. 测试前删除 primary 与 replica 中除 `flyway_schema_history` 外的所有表数据，并执行 Redis `FLUSHALL`。
3. 测试持锁运行；共享入口默认关闭后台调度，测试可显式调用 job，并可使用事务、Redis stream/group/pending、HLL、hash、set 和 TTL。
4. 测试后再次执行同样清理，即使断言失败也释放锁。

绝大多数数据库主键由业务 Snowflake ID 生成；`integration_events.seq` 与 `redirect_cache_invalidation_outbox.id` 是仅有的业务自增序列。reset 不对业务表泛化执行 `TRUNCATE`：删除事务提交并恢复外键检查后，仅当实时元数据显示这两条序列已经前进时，才对对应空表执行 `ALTER TABLE ... AUTO_INCREMENT = 1`。这样避免 DDL 的隐式提交破坏删除回滚语义，也让未触碰这两张表的 fixture 边界不承担 ALTER 成本；该条件 DDL 的耗时仍计入 `averageResetMillis`。

`FLUSHALL` 会一起删除 key、stream、consumer group、pending、DLQ 和过期时间。Spring 上下文或进程内缓存若承载可变业务状态，测试仍须通过公开用例显式初始化或清理，不能把它们误认为 Redis fixture。

共享 fixture 不依赖 JUnit 方法顺序；`SharedIntegrationTopologyIsolationTest` 使用不同哨兵并重复运行，锁定双库、多种 Redis 状态以及两条业务自增序列不泄漏。仓库当前不开启集成测试并行执行；若未来开启，只有持有共享扩展锁的测试可以操作这套 topology。

`AnalyticsVisitStreamRecoveryIntegrationTest` 通过真实访问事件 appender 交错执行固定轮数的峰值生产与多轮调度。每次调度的落库增量不得超过 `ingest-batch-size × ingest-max-batches`；生产停止后，测试在有限调度轮数内要求 consumer group 的 lag 与 pending 都回落为零，并验证 Stream 的 `XLEN` 保持在 `MAXLEN ~` 预算及一个 Redis macro-node 容差内。该门禁不使用墙钟性能阈值，避免把 CI 主机抖动误判为恢复语义回归。

### 故障诊断

容器启动失败会同时报告 primary、replica 和 Redis 镜像名。排查顺序：

1. 确认 Docker daemon 可访问且磁盘空间充足。
2. 检查 Testcontainers/Ryuk 日志及实际映射端口。
3. 检查两库 Flyway 是否停在同一最新版本。
4. 若只在单测间歇失败，先检查测试是否绕过共享扩展或在异步任务仍运行时结束。
5. 若 reset 失败，检查遗留事务、连接池日志与 Redis `FLUSHALL` 返回值。

`SharedIntegrationTopology.metrics()` 暴露本 JVM 的 topology 启动尝试数、容器启动数、启动耗时、reset 次数和平均 reset 耗时；`SharedIntegrationTopologyMetricsListener` 在整个 JUnit launcher session 关闭时只输出一次最终快照，避免随机类顺序产生中途计数。`SharedIntegrationTopologyConcurrencyTest` 以并发调用和重复调用锁定单次启动语义。完整迁移门禁需要连续运行三次，并把中位数及波动与迁移前 `14m36s` 基线对比；未得到可解释改善时不得把共享 topology 宣告为唯一入口。

2026-08-16 在同一工作树快照上执行三次完整随机顺序门禁；每轮均为独立 JVM，命令为
`mvn -q -pl integration-tests -am -Pit -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=<seed> verify`。
每轮 142 项测试全部通过，实测如下：

| 随机种子 | 总耗时 | topology 启动次数 | 容器启动数 | 启动耗时 | reset 次数 | 平均 reset 耗时 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `1701` | 214.51s | 1 | 3 | 55,132ms | 278 | 156ms |
| `2903` | 215.78s | 1 | 3 | 55,326ms | 278 | 163ms |
| `4517` | 213.84s | 1 | 3 | 54,106ms | 278 | 156ms |

总耗时中位数为 214.51s，最小值 213.84s、最大值 215.78s，极差 1.94s（中位数的 0.90%）。
相比迁移前 876s 基线，中位数下降 75.51%；三轮均只启动一个共享拓扑，因此共享 topology 的唯一入口验收通过。

## 覆盖率原则

覆盖率用于防止高风险边界回退，不代替真实 MySQL/Redis 的并发、恢复或端到端断言。CI 上传各 Maven 模块 JaCoCo 报告和前端 V8 报告；关键认证、HTTP transport、Redirect 缓存、outbox、Analytics job 以及前端 composable/service 另设最低基线。临时豁免必须在仓库内写明负责人、原因和到期日，不能静默降低阈值。

后端当前值来自最近一次 `mvn verify` 生成的模块 `jacoco.csv`，最低值来自 `server/pom.xml`。JaCoCo agent 的 class
filter 使用 JVM 路径形式（例如 `com/linkforge/.../Type.class`），class rule 使用点分全限定名；
`LegacyDirtyStreamMetrics` 外部类与 `Aggregate` 嵌套类分别设门禁，防止 include 形式不匹配导致规则空跑。

| 关键类 | 当前行覆盖率 | 最低行覆盖率 | 当前分支覆盖率 | 最低分支覆盖率 |
| --- | ---: | ---: | ---: | ---: |
| `ApiKeyService` | 74.8918% | 74.89% | 53.1250% | 53.12% |
| `ApiKeySecretCodec` | 92.6471% | 92.64% | 69.7368% | 69.73% |
| `AccountStatusService` | 74.6032% | 74.60% | 58.0000% | 58.00% |
| `AuthService` | 84.8101% | 84.81% | 52.9412% | 52.94% |
| `UserAdminService` | 62.3077% | 62.30% | 47.0588% | 47.05% |
| `AuthController` | 90.1639% | 90.16% | 62.5000% | 62.50% |
| `ReportRange` | 93.7500% | 93.75% | 83.3333% | 83.33% |
| `RedisAnalyticsVisitEventAppender` | 94.8276% | 94.82% | 55.8824% | 55.88% |
| `AnalyticsDimensionFlushJob` | 75.6972% | 75.69% | 53.6458% | 53.64% |
| `AnalyticsEventIngestJob` | 80.4878% | 80.48% | 60.5769% | 60.57% |
| `AnalyticsEventRetentionJob` | 100.0000% | 100.00% | 78.5714% | 78.57% |
| `AnalyticsFlushJob` | 76.0656% | 76.06% | 56.3725% | 56.37% |
| `AnalyticsRedirectEventProjectorJob` | 82.4561% | 82.45% | 63.4615% | 63.46% |
| `AnalyticsRedisAggregateWriter` | 81.1060% | 81.10% | 53.7415% | 53.74% |
| `LegacyDirtyStreamMetrics` | 90.0000% | 90.00% | 50.0000% | 50.00% |
| `LegacyDirtyStreamMetrics.Aggregate` | 71.7949% | 71.79% | 78.2609% | 78.26% |
| `RedisStreamBatchConsumer` | 70.1923% | 70.19% | 61.0294% | 61.02% |
| `VersionedDirtyMarkerStore` | 86.5385% | 86.53% | 63.0435% | 63.04% |
| `VisitEventDeadLetterWriter` | 80.9524% | 80.95% | 50.0000% | 50.00% |
| `StatsController` | 30.4762% | 30.47% | 9.5238% | 9.52% |
| `GlobalExceptionHandler` | 51.1111% | 51.11% | 44.4444% | 44.44% |
| `ApiKeyAuthenticationFilter` | 88.2353% | 88.23% | 58.3333% | 58.33% |
| `JwtAuthenticationFilter` | 88.4615% | 88.46% | 59.2593% | 59.25% |
| `RedirectService` | 85.8065% | 85.80% | 68.8889% | 68.88% |
| `LinkCacheService` | 95.8333% | 95.83% | 75.0000% | 75.00% |
| `RedirectCacheInvalidationOutboxJob` | 61.5385% | 61.53% | 35.2941% | 35.29% |

前端当前值来自 `npm run test:coverage` 生成的 V8/lcov 报告，最低值来自 `web/vite.config.ts`：

| 关键文件 | 当前 statements / branches / functions / lines | 最低 statements / branches / functions / lines |
| --- | ---: | ---: |
| `src/composables/useStatsPage.ts` | 87.5940% / 79.2453% / 90.9091% / 87.5940% | 87.59% / 79.24% / 90.90% / 87.59% |
| `src/services/http.ts` | 92.8177% / 75.0000% / 100.0000% / 92.8177% | 92.81% / 75.00% / 100.00% / 92.81% |

最低值按工具支持的配置精度锁定当前报告基线；覆盖率提升后应同步抬高最低值。任何下调都必须走前述有负责人和
到期日的显式豁免，不能用移除 include、改错 class 名或降低阈值绕过门禁。

## Redirect 压测

`bench/redirect` 的 k6 脚本用于容量与性能回归，不属于正确性门禁。压测前应准备独立环境、有效 host/code 和明确的峰值速率、恢复时间目标；不要对共享生产入口直接执行 ramp 或 burst。

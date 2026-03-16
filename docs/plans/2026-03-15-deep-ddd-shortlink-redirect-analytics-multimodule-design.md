# LinkForge：方案 3（深度 DDD）——ShortLink / Redirect / Analytics 事件驱动 + Maven 多模块内部分层改造设计

日期：2026-03-15  
范围：`server/shortlink`、`server/redirect`、`server/analytics`、`server/contracts/*`、`server/app`

> **实现状态（2026-03-16）：** 已按本设计落地，并按“不要保留 Legacy”进一步收敛：仓库中不存在 `LegacyShortLinkService` / `LinkMetaQueryPort` / `LinkCacheOutbox*`。ShortLink 写侧在同一事务内 append `integration_events`；Redirect/Analytics 通过 projector jobs 投影到各自读模型（`redirect_link_projection` / `analytics_link_catalog`）并维护 Redis side effects。  
> 当前关键入口：`server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkApplicationService.java`、`server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/projection/ShortLinkEventProjectorJob.java`、`server/analytics/infrastructure/src/main/java/com/linkforge/analytics/infrastructure/catalog/ShortLinkCatalogProjectorJob.java`。

> 本设计目标是把当前“分层看起来 DDD-ish，但核心用例更像 Transaction Script”的结构，升级为 **编译期强边界** 的 Modular Monolith：  
> - 每个 Bounded Context 内部再按 DDD 四层拆成 Maven 子模块（domain/application/infrastructure/interfaces）  
> - 跨 BC 通过 **Integration Events（Published Language）** 协作（而不是同步调用对方的端口/mapper/缓存）  
> - Redirect/Analytics 各自维护 Projection（读模型），彻底消除 `ShortLinkService` 夹杂缓存一致性/统计耦合/持久化细节的结构性根因

相关背景文档（早期多模块改造设想）：
- `docs/plans/2026-03-07-ddd-maven-multimodule-design.md`

本设计在上述基础上进一步推进到“深度 DDD + 事件驱动 + BC 内部分层模块化”。

---

## 0. 问题陈述（Why）

改造前 `shortlink/analytics` 的分层外观接近 DDD，但在关键类上呈 Transaction Script 特征：

- `server/shortlink/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java` 同时承担：
  - 聚合构建与状态变更（直接 new/改 `ShortLinkEntity`）
  - 持久化（直接调用 MyBatis mapper）
  - 缓存一致性（`LinkCachePort`、`AfterCommit`、outbox enqueue）
  - 标签、分页、导入导出等用例编排与业务规则
- `redirect` 与 `analytics` 仍存在对 `shortlink` 的同步依赖（`LinkMetaQueryPort`），统计查询还有 N+1 回源。

> 注：上述路径/依赖为“改造前”描述用（用于解释问题根因）。改造完成后 shortlink 的入口已迁到 `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/ShortLinkApplicationService.java`，并且跨 BC 查询端口 `LinkMetaQueryPort` 已删除。

这不是“错”，但会带来长期架构后果：
1) 领域规则分散，难以形成可演进的统一模型  
2) 用例难测（单测要 mock mapper/cache/outbox/tx template…），回归慢  
3) 持久化与缓存策略替换成本高（很多逻辑被绑在 service 里）

本设计以“结构约束 + 事件驱动协作”从根因解决。

---

## 1. 目标与非目标（What）

### 1.1 目标（必须达成）

1) **编译期边界**：每个 BC 内部分为 `domain/application/infrastructure/interfaces` 四个 Maven 子模块，依赖方向固定。  
2) **深度用例拆分**：每个用例一个 `CommandHandler/QueryHandler`（或 use-case class），`ShortLinkService` 这种“巨型应用服务”被拆解。  
3) **领域规则集中**：短码、URL、allowlist pattern、归档/删除护栏等规则回收到 domain（聚合/值对象）。  
4) **跨 BC 事件协作**：ShortLink 作为事实源发布 Integration Events；Redirect/Analytics 订阅并维护各自 Projection；不再同步查询 shortlink 写库。  
5) **持久化可替换**：application 只依赖 Repository Port；MyBatis mapper 只存在于 infrastructure 模块。  
6) **可测试性**：domain / application 具备高密度单测；跨模块链路以 integration tests 兜底。

### 1.2 非目标（本阶段不做 / 不强求）

- 不要求保持对外 API/DB/Redis 完全兼容（允许 breaking change）。  
- 不强制引入 DDD 的所有战术模式（例如 SAGA / process manager / event sourcing）。  
- 不改动前端（`web/`）与部署形态（仍是单进程 Spring Boot 单体，可执行 jar）。

---

## 2. 目标架构（To-Be）

### 2.1 每个 BC 的 Maven 子模块

以 `shortlink` 为例（`redirect`、`analytics` 同构）：

- `server/shortlink`（packaging=`pom`，只做聚合）
  - `server/shortlink/domain` → `linkforge-shortlink-domain`
  - `server/shortlink/application` → `linkforge-shortlink-application`
  - `server/shortlink/infrastructure` → `linkforge-shortlink-infrastructure`
  - `server/shortlink/interfaces` → `linkforge-shortlink-interfaces`

依赖方向（只能向内依赖）：

```text
shortlink-interfaces      -> shortlink-application -> shortlink-domain
shortlink-infrastructure  -> shortlink-application -> shortlink-domain
shortlink-domain          -> (JDK + shared-kernel only)
```

约束要点：
- `domain` 禁止 Spring / Web / MyBatis / Redis / JDBC 依赖（纯业务模型）。
- `application` 禁止依赖 MyBatis mapper（只能依赖 Port/Repository 接口）。
- `interfaces` 是唯一依赖 `spring-web/spring-security/jakarta.servlet` 的层。
- `infrastructure` 是唯一持有 DB/Redis/IO 细节的层，并实现 application 的 ports。

> 注：现有 `server/shared-kernel` 目前为空壳，本设计允许逐步把跨 BC 的纯领域原语下沉到 shared-kernel，但不强求立即填充。

### 2.2 组合根（Composition Root）

`server/app` 是唯一 composition root：负责把各 BC 的 interfaces + infrastructure 放进运行时 classpath 并完成 Spring 装配。

**依赖装配模式（本设计的最终决议）**：
- `server/app` 对每个 BC：
  - **compile 依赖**：`*-interfaces`
  - **runtime 依赖**：`*-infrastructure`
- `*-application` / `*-domain` 作为 transitive dependency 由 `interfaces/infrastructure` 带入，无需 `app` 显式依赖。

动机：
- 让 `app` 在**编译期**只能引用 interfaces（避免 app 代码“偷懒 import 某个 mapper/repo 实现”）。
- 同时保证 infrastructure（MyBatis/Redis/consumer job 等 Bean）在**运行期**存在并可被扫描装配。

装配方式保持与现有一致（`server/app/src/main/java/com/linkforge/app/compose/*Module.java`）：
- 每个 BC 一个 `@Configuration`，`@ComponentScan("com.linkforge.<bc>")`。
- domain 模块不声明 Spring Bean（纯模型），扫描不会引入副作用。

> 目标：`app` 是唯一“组装依赖图”的地方；BC 之间不直接相互依赖实现。

### 2.3 跨 BC 协作方式：Integration Events + Projection

核心变化：
- ShortLink 是写模型事实源（source of truth）
- Redirect / Analytics 不再同步回源 shortlink（移除 `LinkMetaQueryPort` 依赖）
- Redirect / Analytics 通过消费 ShortLink 发布的 Integration Events，维护各自的 Projection（读模型表/缓存）

```mermaid
flowchart LR
  SLW[ShortLink Write Model\n(domain+application)] -->|append integration event| EV[(integration_events)]
  EV -->|consume & project| RPR[(redirect_link_projection)]
  EV -->|consume & project| APR[(analytics_link_catalog)]

  RPR --> RC[Redirect Cache (Redis)]
  RC --> RS[RedirectService resolve]
  APR --> AQS[AnalyticsQueryService]
```

一致性模型：
- Redirect/Analytics 的 read model 与 shortlink 写库之间为 **最终一致性**（eventual consistency）
- Redirect 热路径优先走 Redis，cache miss 回源 Redirect 自己的 projection 表

---

## 3. Published Language：ShortLink Integration Events（新 contracts 模块）

### 3.1 新增 contracts 模块

新增：`server/contracts/shortlink`（artifactId：`linkforge-contract-shortlink`）

包含：
- `ShortLinkPublicSnapshot`（对外可见的 link 快照，用于投影）
- Integration Events：
  - `ShortLinkCreatedV1`
  - `ShortLinkUpdatedV1`
  - `ShortLinkArchivedV1`
  - `ShortLinkRestoredV1`
  - `ShortLinkDeletedV1`

事件结构建议（示意）：

```java
public record ShortLinkCreatedV1(
  String eventId,
  Instant occurredAt,
  long tenantId,
  long linkId,
  String code,
  ShortLinkPublicSnapshot snapshot
) {}
```

版本策略：
- `integration_events.event_type` 使用稳定字符串：`shortlink.<EventName>.v1`  
  示例：`shortlink.ShortLinkCreated.v1`、`shortlink.ShortLinkUpdated.v1`。
- 在 `contracts/shortlink` 中提供常量（例如 `ShortLinkEventTypes`）作为唯一真源，producer/consumer 必须引用该常量。
- 向后兼容通过新增字段实现；破坏性变更通过新增 `...v2` event type 实现（consumer 可并行支持 v1/v2）。

序列化策略（最终决议）：
- `payload_json` 为事件对象的 JSON（Jackson），时间字段统一为 **UTC ISO-8601**（例如 `2026-03-15T12:34:56Z`）。
- `occurred_at`、projection 表的 `*_at` 字段统一按 **UTC** 语义写入 `DATETIME`（MySQL 不存 timezone，本设计约定一律为 UTC）。

> 目标：短链变更的“事实”只通过 events 广播。Redirect/Analytics 不再需要 `LinkMetaQueryPort` 这种同步跨 BC 查询端口。

### 3.2 `ShortLinkPublicSnapshot` 字段契约（Projection 唯一真源）

`ShortLinkPublicSnapshot` 的字段需一次性写死，consumer 不允许“猜测字段语义”。

**字段清单（V1）**：
- `tenantId: long`（必填，>0）
- `linkId: long`（必填，>0）
- `code: String`（必填，6–32，`[0-9A-Za-z]`，大小写敏感；建议 ASCII）
- `originalUrl: String`（必填，http/https，长度上限由 domain 校验）
- `enabled: boolean`（必填）
- `expiresAtUtc: Instant`（可空；表示 UTC 时间点；available iff null 或严格晚于 nowUtc）
- `redirectStatusCode: Integer`（可空；仅允许 301/302；空表示使用 Redirect 全局默认）
- `previewEnabled: boolean`（必填）
- `unavailableLandingUrl: String`（可空；若非空必须为 http/https URL）
- `queryForwardMode: String`（可空；`OFF/ALLOWLIST/ALL`；空表示使用 Redirect 全局默认）
- `queryForwardAllowlist: List<String>`（可空/可空列表；每项为 param pattern（允许 `utm_*` 前缀通配）；空表示无白名单）
- `archivedAtUtc: Instant`（可空；仅在 `Archived` 事件中必填；其他事件必须为 null）

**空值/默认值规则**：
- `queryForwardAllowlist=null` 与 `[]` 在语义上等价（无白名单）。
- `redirectStatusCode=null`、`queryForwardMode=null` 表示“按 redirect 全局配置兜底”，consumer 不负责做默认值填充。

**字段到投影表映射（Redirect/Analytics）**：
- Redirect projection 仅使用：`code/tenantId/linkId/originalUrl/enabled/expiresAtUtc/redirectStatusCode/previewEnabled/unavailableLandingUrl/queryForwardMode/queryForwardAllowlist`
- Analytics catalog 仅使用：`tenantId/linkId/code/originalUrl/archivedAtUtc`

---

## 4. 事件投递机制（Integration Event Log）

### 4.1 数据模型：integration_events（追加写）

新增表（Flyway）：

```sql
CREATE TABLE IF NOT EXISTS integration_events (
  seq BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  producer VARCHAR(64) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  tenant_id BIGINT NULL,
  aggregate_type VARCHAR(64) NULL,
  aggregate_id BIGINT NULL,
  occurred_at DATETIME NOT NULL,
  payload_json JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ie_event_id (event_id),
  KEY idx_ie_producer_seq (producer, seq),
  KEY idx_ie_type_seq (event_type, seq),
  KEY idx_ie_tenant_seq (tenant_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

设计动机：
- 追加写 + seq 单调递增：适合“多消费者独立追赶”的投影场景  
- 生产者只负责写事件，不关心有哪些消费者（解耦）

事件 ID（event_id）策略（最终决议）：
- 在线事件：使用 **UUIDv7**（或退化为 UUID），由 producer 在 append 时生成，确保全局唯一且可排序（仅用于排障，不作为消费顺序依据）。

### 4.2 消费进度：integration_consumer_checkpoint

新增表（Flyway）：

```sql
CREATE TABLE IF NOT EXISTS integration_consumer_checkpoint (
  consumer VARCHAR(64) NOT NULL PRIMARY KEY,
  last_seq BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

每个 consumer（例如 `redirect-link-projector`、`analytics-link-catalog-projector`）维护自己的 checkpoint。

### 4.3 幂等与重放

消费逻辑必须幂等（因为“处理成功但 checkpoint 未提交”的崩溃会导致重放）：
- Projection 写入用 **upsert**（按 code/linkId 唯一键覆盖）  
- Redis cache 更新用 `SET`/`DEL`（天然幂等）

### 4.4 Consumer 执行模型（最终决议）

每个 consumer 以“顺序追赶”的方式处理事件：
- 运行方式：`@Scheduled(fixedDelay=...)` 轮询 + `@SchedulerLock`（ShedLock）保证多实例下同一 consumer 同时仅 1 个实例运行。
- 读取方式：
  - 读取当前 checkpoint：`last_seq`
  - 拉取下一批：`SELECT * FROM integration_events WHERE seq > :lastSeq ORDER BY seq ASC LIMIT :batchSize`
- 处理方式：**按 seq 严格递增**逐条处理（保证投影确定性）。
- 典型 batchSize：200（可配置；上限 1000）。

### 4.5 事务边界与 checkpoint 推进（最终决议）

对每条事件，consumer 必须在**同一 DB 事务**内完成：
1) 投影写入（upsert/delete）
2) checkpoint 推进到该 `seq`

这样保证：
- 投影与 checkpoint 不会“投影写成功但 checkpoint 未推进”（导致重复消费）之外的更坏情况；
- 重放时最多造成“重复写同样投影”（依赖幂等 upsert）。

### 4.6 Poison Event 与失败处理（最终决议）

为避免单条坏事件永久阻塞 consumer，引入 dead letter 表（不是可选项）：

```sql
CREATE TABLE IF NOT EXISTS integration_consumer_dead_letter (
  consumer VARCHAR(64) NOT NULL,
  seq BIGINT NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  producer VARCHAR(64) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  occurred_at DATETIME NOT NULL,
  payload_json JSON NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) NULL,
  first_failed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_failed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (consumer, seq),
  KEY idx_icdl_consumer_failed_at (consumer, last_failed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

处理策略：
- transient 错误（DB/Redis 短暂失败）：不推进 checkpoint，等待下次轮询重试。
- 明确不可恢复（JSON 无法反序列化、未知 event_type、schema 不匹配）：记录 dead letter，并**推进 checkpoint 跳过**该事件（避免全局阻塞）。
- attempts 上限：例如 20 次（可配置）；超过上限则转 dead letter 并跳过。

---

## 4.7 可观测性与保留策略（最小要求）

### 指标（metrics）

每个 consumer 至少暴露以下指标（Micrometer）：
- `integration.consumer.lag{consumer=...}`：`maxSeq - lastSeq`（lag=0 表示追平）
- `integration.consumer.dead_letter.total{consumer=...}`：dead letter 总数
- `integration.consumer.processed.total{consumer=...,result=done|retry|dead_letter}`：处理计数

### 日志（logs）

consumer 处理每条事件失败时日志必须包含：
- `consumer`、`seq`、`eventId`、`eventType`、`attempts`、`lastSeq`、`err`

### 表保留（retention）

本版本的最小策略：
- `integration_events`：暂不做自动清理（保留以支持重放）；上线后根据容量再引入归档/分区/清理作业。
- `integration_consumer_dead_letter`：不自动清理（需要人工排查）；后续可引入“已确认忽略”标记与定期清理。

## 5. Redirect Projection（读模型）

### 5.1 redirect_link_projection 表

Redirect 需要按 `code` 高效查找，因此 projection 主键建议就是 code：

```sql
CREATE TABLE IF NOT EXISTS redirect_link_projection (
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  link_id BIGINT NOT NULL,
  original_url TEXT NOT NULL,
  enabled BIT(1) NOT NULL,
  expires_at DATETIME NULL,
  redirect_status_code INT NULL,
  preview_enabled BIT(1) NOT NULL,
  unavailable_landing_url TEXT NULL,
  query_forward_mode VARCHAR(16) NULL,
  query_forward_allowlist VARCHAR(1024) NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_rlp_tenant_link (tenant_id, link_id),
  KEY idx_rlp_link_id (link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.2 Redirect resolve 流程（新）

Redirect 热路径：
1) normalize/validate code  
2) `LinkCache`（Redis）lookup（含负缓存）  
3) miss → 查询 `redirect_link_projection`（按 code 精确匹配）  
4) found → 写入 cache（即使 enabled=false 或已过期，也缓存该 meta，交由 controller 决定 410/落地页）  
5) not found → markNotFound（负缓存，仅表示“不存在或已归档/已删除”）

> Redirect 不再依赖 `LinkMetaQueryPort`，不再读取 short_links 表。

可用性判定（与现有行为保持一致，作为 redirect domain policy）：
- not found：投影无记录（不存在 / 已归档 / 已删除）
- disabled：`enabled=false`
- expired：`expires_at != null && expires_at <= nowUtc`（nowUtc 来自 `Clock`）

### 5.3 Projector（消费 shortlink events）

Redirect BC 内新增 `ShortLinkEventProjector`（application 或 infrastructure 中的 consumer job）：
- 消费 `ShortLink*V1` events
- `Created/Updated/Restored`：upsert 到 `redirect_link_projection`
- `Archived/Deleted`：从 `redirect_link_projection` 删除该 code 对应记录
- 同时维护 Redis cache（最终决议，非可选）：
  - `Created/Updated/Restored`：先 `DEL` 再 `SET`（覆盖负缓存 sentinel，避免“新建后仍 404”）
  - `Archived/Deleted`：`DEL`（不主动写负缓存；负缓存仍由 redirect miss 时写入）

---

## 6. Analytics Projection（Link Catalog）

### 6.1 analytics_link_catalog 表

用于消除 topLinks 的 N+1 回源，并对“链接删除”做软标记：

```sql
CREATE TABLE IF NOT EXISTS analytics_link_catalog (
  tenant_id BIGINT NOT NULL,
  link_id BIGINT NOT NULL,
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  original_url TEXT NULL,
  archived_at DATETIME NULL,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, link_id),
  KEY idx_alp_link_id (link_id),
  KEY idx_alp_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.2 AnalyticsQuery 改造点

`topLinks`：
- 改为 MyBatis query 直接 join `analytics_link_catalog`（或先查 agg 再 join 一次），避免每行调用跨 BC port。

> Analytics 不再依赖 `LinkMetaQueryPort`。

### 6.3 Projector

Analytics BC 内新增 `ShortLinkCatalogProjector`：
- 消费 shortlink integration events
- 投影规则（最终决议）：
  - `Created/Updated`：upsert `(tenantId, linkId)`，写入 `code`、`original_url`、`archived_at=NULL`、`deleted=0`
  - `Archived`：upsert `(tenantId, linkId)`，保持 `code/original_url`，写入 `archived_at=<archivedAtUtc>`、`deleted=0`
  - `Restored`：upsert `(tenantId, linkId)`，写入 `archived_at=NULL`、`deleted=0`
  - `Deleted`：upsert `(tenantId, linkId)`，写入 `deleted=1` 且 `code=NULL`、`original_url=NULL`（与现有 topLinks “deleted=true 时 meta 缺失”语义一致）

---

## 7. ShortLink 写模型（Domain + Use Cases）

### 7.1 Domain（聚合/值对象）

ShortLink 聚合建议包含以下值对象与规则（示意）：
- `ShortCode`：长度/字符集/大小写敏感（替代 scattered `validateCode`）
- `OriginalUrl`：http/https + host + 长度（替代 `UrlValidator` 分散校验）
- `QueryForwardPolicy`：
  - mode: OFF/ALLOWLIST/ALL
  - allowlist: `List<QueryParamPattern>`（替代逗号字符串）
- `RedirectPolicy`：301/302 可空
- `PreviewPolicy`：previewEnabled + unavailableLandingUrl

领域行为（聚合方法）：
- `create(...)`（工厂/构造器 + invariant）
- `update(...)`（基于命令对象更新，聚合内部决定哪些字段可变）
- `archive(now)` / `restore()`
- `requireArchivedBeforeDelete()`（保留“误删护栏”则放 domain）

### 7.2 Use Case Handlers（application）

把当前 `ShortLinkService` 拆为：
- Commands：
  - `CreateShortLinkCommandHandler`
  - `UpdateShortLinkCommandHandler`
  - `ArchiveShortLinkCommandHandler`
  - `RestoreShortLinkCommandHandler`
  - `DeleteShortLinkCommandHandler`
  - `ImportShortLinksCsvCommandHandler`
  - `CreateTagCommandHandler`
  - `SetLinkTagsCommandHandler`（可被 create/update 复用）
- Queries：
  - `GetShortLinkDetailQueryHandler`
  - `SearchShortLinksQueryHandler`
  - `ListTagsQueryHandler`
  - `ExportShortLinksCsvQueryHandler`

application 层只依赖 ports：
- `ShortLinkRepository`（save/findById/findByCode/search/count…）
- `TagRepository`、`LinkTagRepository`（或统一为 `TaggingRepository`）
- `IdGeneratorPort`、`ClockPort`
- `IntegrationEventAppender`（把 integration event append 到 `integration_events`）

> CSV 解析/格式细节可以留在 application，但应拆成独立 handler/service，并以纯函数/小类封装，避免污染聚合。

### 7.3 Integration Event 产生位置

规则：
- command handler 完成聚合变更并持久化后，在同一事务内 append `ShortLink*V1` event 到 `integration_events`
- event payload 使用 `ShortLinkPublicSnapshot`（由 mapper 从聚合映射得到）

这样能保证：
- DB 状态变更与 event 记录原子一致
- 消费方可重放构建投影

---

## 8. 迁移与清理（Breaking Allowed 的前提下）

建议分阶段落地，确保每一步都能编译/启动：

### Phase 0：骨架与依赖图落地
1) 调整 `server/pom.xml` modules，新增各 BC 子模块  
2) `app` 依赖改为：compile `*-interfaces` + runtime `*-infrastructure`（见 2.2）  
3) 拆包移动代码（保持功能先不改）  

### Phase 1：引入 shortlink contracts + integration_events
1) 新增 `contracts/shortlink` 与事件类型  
2) 新增 `integration_events` / `integration_consumer_checkpoint` 表  
3) shortlink 在写侧 append 事件（先不改 redirect/analytics）  

### Phase 2：Redirect/Analytics 投影与切流
1) redirect 新增 projection 表 + projector consumer job  
2) analytics 新增 catalog 表 + projector consumer job  
3) 等待两个 projector 的 checkpoint catch up（lag=0）  
4) redirect resolve 切流为读 projection（cache miss 回源 projection）  
5) analytics topLinks 切流为 join catalog  

> **Decision (2026-03-16):** 本仓库视为 greenfield（无历史数据/可删库重来），不再提供 backfill runner。  
> 若未来需要把存量 `short_links` 转为 integration events，请在仓库外以一次性脚本/运维流程完成（避免把迁移窗口逻辑长期留在主干代码中）。

维护窗口落地方式（最终决议，独立于 backfill）：
- 在 `shortlink-interfaces` 对所有写入类 API（create/update/archive/restore/delete/import/tag mutation）增加统一护栏：
  - 配置项：`app.shortlink.write-enabled`（默认 true）
  - 当为 false 时直接返回 `ErrorCode.SERVICE_UNAVAILABLE` 并提示“维护中”
- Redirect `/r/**` 不受影响（读路径继续可用）

### Phase 3：删旧耦合与收敛 contracts
1) 移除 `LinkMetaQueryPort` 相关代码与 wiring  
2) shortlink 不再依赖 `LinkCachePort`（如仍需内部缓存，可改为 shortlink 自己的端口/实现）  
3) 移除现有 `link_cache_outbox` 表与 job（最终决议）：  
   - 新架构下缓存一致性由 redirect projector + `integration_events` 的重放能力保证  
   - 不再需要 shortlink 写侧 enqueue cache refresh outbox

---

## 9. 测试策略（How to verify）

### 9.1 Domain 单测（高价值）
- `ShortCode`、`OriginalUrl`、`QueryForwardPolicy` 等值对象校验边界
- `ShortLink` 聚合状态机（archive/restore/update/delete guard）

### 9.2 Application 单测
- 每个 command/query handler 使用 fake repository + fake event appender 验证：
  - 行为正确
  - 产出正确的 integration events

### 9.3 集成测试（跨 BC 事件链路）
- 起 Testcontainers MySQL+Redis（复用现有 integration-tests 或新增）
- 写 shortlink → append event → consumer 跑起来 → redirect projection/redis 生效 → `/r/{code}` 可解析
- analytics topLinks 能返回 code/url（来自 catalog），不再 N+1

---

## 10. 风险与缓解

- **风险：模块爆炸导致重构周期过长**  
  缓解：严格按 Phase 0→1→2→3，每 phase 有明确“可启动/可测试”的验收点。

- **风险：事件投影延迟导致用户体验变化**  
  缓解：consumer job 可设置较短 fixedDelay；必要时可加 `AfterCommit` 的 best-effort “快投递”，并保留 event log 兜底重放。

- **风险：Projection schema 演进复杂**  
  缓解：事件版本化 + projection 表只服务本 BC，可自由演进；消费者容忍旧事件（或实现 V1/V2 并行）。

---

## 11. 设计决议（Decision Log）

1) 选择方案 3：BC 内部分层 Maven 子模块 + 事件驱动协作  
2) 选择 integration event log（seq + checkpoint）作为投影基础设施  
3) 允许 breaking change，优先“结构正确 + 可演进”

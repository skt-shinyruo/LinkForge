# 变更提案: shortlink-cache-outbox

## 元信息
```yaml
类型: 修复/优化
方案类型: implementation
优先级: P0（一致性风险治理，建议尽快上线）
状态: ✅完成
创建: 2026-02-25
```

---

## 1. 需求

### 背景
已完成的治理（`shortlink-cache-after-commit`）将 `ShortLinkService` 的 `put/evict Redis` 延后到事务提交后（AFTER_COMMIT），修复了：
- 事务回滚但缓存已写/已删导致的永久不一致
- 事务未提交先驱逐缓存导致 Edge 回源旧值并“旧值回填”的竞态

但 AFTER_COMMIT 仍有一个天然缺口：**DB 已 commit，但进程在 afterCommit 回调执行前崩溃/重启**，会导致该次缓存刷新/驱逐动作丢失（只能等待后续回源或 TTL 自愈）。

本次要把“缓存更新/驱逐”升级为更强的最终一致能力：commit 后即使进程崩溃，也不丢刷新动作，重启后可补偿执行。

### 目标
- `create/update/archive/restore/delete` 的缓存刷新动作具备 **持久化** 与 **可重试** 能力。
- DB commit 后即使进程崩溃，仍可在重启后最终把 Redis 修正到 DB 当前状态。
- 保持幂等：重复投递/重复消费不会产生错误结果（以 DB 为真）。

### 约束条件
```yaml
基础设施: 不引入新中间件（复用 MySQL + Redis）
一致性语义: 以 DB 当前状态为准（REFRESH 语义），避免携带旧 payload
性能: outbox 需天然合并/去重，避免高频更新产生大量行
可运维: 支持重试/退避，避免 Redis 异常时重试风暴
```

### 验收标准
- [x] outbox 表与消费 job 可用：短链变更后存在 outbox 记录，job 可消费并修正 Redis。
- [x] “commit 后崩溃不丢刷新”成立：在测试中模拟“缓存丢失”，仅靠 outbox job 仍能从 DB 刷新回来。
- [x] rollback 不产生 outbox 残留（同事务回滚）。
- [x] 消费逻辑幂等：同一 code 多次 refresh 不影响正确性；归档/删除后 refresh 会驱逐缓存。
- [x] 文档同步：shortlink 模块与 data 文档补充 outbox 机制说明。

---

## 2. 方案

### 方案对比（候选 A/B）
| 方案 | 核心思路 | 优点 | 缺点/成本 | 结论 |
|------|----------|------|-----------|------|
| A) 持久化 Outbox（本次选择） | 事务内写入 MySQL outbox（与业务数据同事务 commit），后台 job（ShedLock）消费并按 DB 当前状态 `put/evict` | commit 后崩溃不丢；可重试；幂等（以 DB 为真）；可观测（attempts/last_error） | 增加一张表 + 一个 job；需要补充监控/清理策略 | **推荐/采纳** |
| B) 仅 AFTER_COMMIT（已实现） | afterCommit 回调里 `put/evict` | 变更小、低延迟 | commit 后崩溃会丢刷新；Redis 短暂不可用时不可补偿 | 不满足目标 |

### 推荐方案（A）
引入 `link_cache_outbox` 表，语义为“对某个 code 触发一次 REFRESH（按 DB 当前状态刷新缓存）”。消费逻辑：
- code 不存在或已归档：`evict(code)`
- code 存在且未归档：`put(meta)`

为了降低写放大与表膨胀，outbox 以 `code` 作为主键：同一 code 的多次变更会合并为一条记录（最新一次 refresh 覆盖前一次）。

**延迟与体验：**
- 保留当前 `AfterCommit.run(...)` 作为“低延迟路径”（commit 后尽快刷新/驱逐）。
- outbox job 作为“可靠兜底”：commit 后崩溃/Redis 短暂不可用也能追赶修正。

### 数据模型（link_cache_outbox）
建议字段（以当前实现为准）：

| 字段 | 类型 | 说明 |
|------|------|------|
| code | varchar(32) PK | 短码（全局唯一，用于天然去重/合并） |
| status | varchar(16) | `PENDING` / `DONE` |
| available_at | datetime | 可执行时间（用于退避重试） |
| attempts | int | 失败次数（用于退避计算/观测） |
| last_error | varchar(512) | 最近一次失败原因（截断） |
| processed_at | datetime | 最近一次成功处理时间 |
| created_at / updated_at | datetime | 记录创建/更新时间 |

### 影响范围
```yaml
涉及模块:
  - server/api-app: 新增 outbox 表迁移；新增 outbox repo + job；ShortLinkService 事务内写 outbox
  - server/shared: LinkCacheService 增加 tryPut/tryEvict（用于消费失败感知与重试）
  - server/api-app tests: 增强集成测试覆盖 outbox 刷新/驱逐与 rollback
  - .helloagents/wiki: modules/shortlink.md + data.md
预计变更文件: 6-10
```

### 风险评估
| 风险 | 等级 | 应对 |
|------|------|------|
| outbox 积压导致缓存长时间不一致 | 中 | 批量消费（limit）；可观测（PENDING 数、最老 available_at）；必要时调大频率/批次 |
| Redis 故障导致重试风暴 | 中 | 指数退避（available_at）；尝试次数记录；错误日志限频（debug） |
| ShedLock 依赖 Redis（锁不可用时 job 不运行） | 低 | Redis 不可用时缓存本就不可用；恢复后 job 可追赶；锁超时需大于单次处理时间 |
| 幂等/乱序问题导致写入旧值 | 低 | REFRESH 语义“每次消费都从 DB 读当前状态”；同 code 合并去重减少乱序风险 |

---

## 3. 核心场景

### 场景 1: create rollback（回滚不产生 outbox 残留）
**条件**：外层事务包裹 `ShortLinkService.create(...)` 并回滚。

**期望**：
- `short_links` 不落库。
- `link_cache_outbox` 无该 code 记录。
- Redis 不新增该 code 的 key。

### 场景 2: update commit + crash（缓存丢失后可由 outbox 补偿刷新）
**条件**：`update` commit 后（模拟崩溃）Redis key 丢失或未更新。

**期望**：
- outbox 记录存在且可被 job 消费。
- job 消费后 Redis 写回最新 `LinkMeta`。

### 场景 3: archive/delete（最终一致驱逐）
**条件**：链接归档/删除后，Redis 被写入旧 meta（模拟“旧值残留/回填”）。

**期望**：
- outbox 消费时按 DB 当前状态判断“不可用”并驱逐 key。

---

## 4. 技术决策

### shortlink-cache-outbox#D001: 引入持久化 outbox 保证短链缓存最终一致
**日期**: 2026-02-25
**状态**: ✅采纳

**背景**:
AFTER_COMMIT 回调不具备持久化能力，commit 后崩溃会丢缓存刷新动作，无法满足“更强最终一致”的目标。

**选项分析**:
| 选项 | 优点 | 缺点 |
|------|------|------|
| A: MySQL outbox + job（REFRESH 语义） | commit 后崩溃不丢；可重试；幂等（以 DB 为真） | 增加表 + job；需要退避/观测 |
| B: 仅 AFTER_COMMIT | 简单、低延迟 | commit 后崩溃丢刷新；Redis 故障不可补偿 |

**决策**: 选择 **A**（并保留 AFTER_COMMIT 作为低延迟路径）。  
**理由**: 以最小系统增量（MySQL 表 + job）获得“持久化 + 可重试”的最终一致能力。  
**影响**: 需要新增 `link_cache_outbox` 迁移、消费 job、并调整缓存写入接口用于重试感知。  

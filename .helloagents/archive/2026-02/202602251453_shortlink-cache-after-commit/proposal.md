# 变更提案: shortlink-cache-after-commit

## 元信息
```yaml
类型: 修复/优化
方案类型: implementation
优先级建议: P0（已出现不一致/错误跳转）或 P1（风险治理，尽快排期）
状态: 草稿
创建: 2026-02-25
```

---

## 1. 需求

### 背景
`ShortLinkService` 的多个 `@Transactional` 方法在事务内直接执行：
- `linkCacheService.put(...)`
- `linkCacheService.evict(...)`

由于 Edge 侧 `RedirectService` 在 cache miss 时会回源 MySQL 并 `put(meta)`，因此事务内直接写/驱逐 Redis 会带来：
- **回滚污染**：DB 回滚但 Redis 已写/删，DB 与缓存不一致。
- **提交前驱逐导致旧值回填**：事务未提交先 `evict`，Edge miss 回源读到旧值并回填 Redis；提交后缓存仍可能是旧值。
  - 对 `archive/delete` 更危险：提交后 DB 已不可用（`archived_at IS NULL` 或记录删除），但缓存里可能仍有可跳转的 `LinkMeta`，不一致可能持续到 TTL。

### 目标
- 仅在 DB 事务 **commit 之后** 才更新/驱逐 Redis。
- DB 事务 **rollback 时不触发** 任何缓存写入/驱逐。
- 避免“提交前驱逐 → Edge 回源旧值 → 旧值回填”的时序问题。
- 可通过自动化测试覆盖 commit/rollback 路径，防止回归。

### 约束条件
```yaml
不引入新基础设施: 优先（本次不做 outbox 表/消费者）
对外兼容: 不改 API 协议、不改数据模型（选 A 时）
性能: afterCommit 回调保持轻量；Redis 失败不影响主链路提交/返回
```

### 验收标准
- [ ] `create/update/archive/restore/delete` 回滚时 Redis 不发生新增/删除/覆盖。
- [ ] `create/update/archive/restore/delete` 提交后才执行对应 `put/evict`。
- [ ] 不再出现“提交前驱逐导致 Edge 旧值回填”的窗口期。
- [ ] 新增/更新测试用例覆盖：`create` 回滚、`update` 回滚、`archive/restore/delete` 提交后的缓存行为。
- [ ] 更新 `.helloagents/wiki/modules/shortlink.md`：补充缓存一致性策略与本次变更记录。

---

## 2. 方案

### 方案对比（候选 A/B）
| 方案 | 核心思路 | 优点 | 缺点/成本 | 结论 |
|------|----------|------|-----------|------|
| A) `TransactionSynchronization` `AFTER_COMMIT` | 在事务内注册 `afterCommit` 回调，commit 后执行 `put/evict`，rollback 不执行 | 变更小、落地快；强一致对齐 commit；无需 DB schema/消息系统；测试易验证 | 依赖 Spring 事务同步；进程在 commit 后回调前崩溃会导致缓存未刷新（可回源自愈）；可能重复注册（可选优化） | **推荐（本次选择）** |
| B) Outbox（最终一致） | 事务内写 outbox，异步消费按 DB 状态刷新/驱逐缓存 | 更鲁棒（重试/幂等/跨系统）；适合平台化事件 | 引入新表+消费者+监控；最终一致；周期与运维成本更高 | 本次不选，后续演进方向 |

### 推荐方案
选择 **A：`AFTER_COMMIT`**。

落地要点：提供一个通用 helper（如 `AfterCommit.run(Runnable)`），在事务同步活跃时注册 `afterCommit`，否则直接执行（防御式）。`ShortLinkService` 中所有缓存 `put/evict` 迁移到 afterCommit。

### 影响范围
```yaml
涉及模块:
  - server/api-app: ShortLinkService（create/update/archive/restore/delete）
  - server/shared: 新增 afterCommit helper（通用 tx 工具）
  - server/api-app tests: 新增/更新集成测试（MySQL + Redis Testcontainers）
  - .helloagents/wiki: modules/shortlink.md
预计变更文件数: 4-6
```

### 风险评估
| 风险 | 等级 | 应对 |
|------|------|------|
| `@Transactional` 未生效导致回调未注册 | 中 | helper 在“无事务”时直接执行；通过集成测试覆盖事务/回滚路径；评审关注调用边界（代理） |
| afterCommit 时 Redis 不可用导致缓存未刷新 | 中 | 保持 best-effort；Edge miss 回源 DB 自愈；必要时补充监控/日志 |
| 同一事务内多次更新导致重复回调（重复 put/evict） | 低 | 先保证正确性；如需优化可做按 code 合并/去重（后续增强） |

---

## 3. 核心场景

### 场景 1: create/update rollback（回滚不污染 Redis）
**条件**：外层事务包裹 `ShortLinkService.create(...)` / `update(...)`，执行后抛异常或显式回滚。

**期望**：
- MySQL 变更不落库。
- Redis 对应短码 key **不新增/不删除/不覆盖**（无缓存污染）。

### 场景 2: archive/restore/delete（提交后再驱逐/刷新，避免旧值回填）
**条件**：管理员执行归档/恢复/删除（均为 `@Transactional`），期间可能有 Edge 请求发生。

**期望**：
- `archive/delete`：commit 后才 `evict(code)`，Edge miss 回源时读取到最新 DB 状态（归档/删除后查不到），不会回填旧值。
- `restore`：commit 后才 `put(meta)`（必要时先 `evict`），保证可用性恢复后缓存为最新值，并覆盖可能存在的负缓存。

---

## 4. 技术决策

### shortlink-cache-after-commit#D001: 事务提交后再写/驱逐短链缓存
**日期**: 2026-02-25
**状态**: ✅采纳

**背景**:
事务内直接 `linkCacheService.put/evict` 会造成回滚污染，以及提交前驱逐引发 Edge 旧值回填，导致短链跳转与 DB 状态不一致。

**A/B 分析**:
| 选项 | 优点 | 缺点 |
|------|------|------|
| A: `TransactionSynchronization.afterCommit` | 快速修复；变更小；强一致对齐 commit；易测试 | 依赖事务同步；进程崩溃/回调失败时需靠回源自愈 |
| B: Outbox | 更鲁棒可重试；适合跨系统 | 引入新表/消费者/监控；最终一致；周期更长 |

**决策**: 选择 **A**。

**理由**:
- 本问题本质是“事务边界内缓存副作用”，A 直接把副作用推迟到 commit 后即可对症解决。
- 以 P0/P1 节奏优先修复一致性缺陷，不在本次引入 outbox 的系统复杂度。

**影响**:
- `ShortLinkService` 缓存写入/驱逐迁移到 afterCommit。
- 新增通用 tx helper。
- 增加回滚/提交路径测试与 shortlink 文档同步。

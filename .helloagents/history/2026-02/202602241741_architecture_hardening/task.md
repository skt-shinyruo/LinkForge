# 任务清单: architecture_hardening

```yaml
@feature: architecture_hardening
@created: 2026-02-24
@status: completed
@mode: R3
```

<!-- LIVE_STATUS_BEGIN -->
状态: completed | 进度: 29/29 (100%) | 更新: 2026-02-24 20:29:51
当前: -
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 26 | 0 | 3 | 29 |

---

## 任务列表

### 0. 决策与预检（开发阶段预检可跳过）

- [√] 0.1 确认 `architecture_hardening#D001`（已选：A email 全局唯一）
- [-] 0.2（可选/上线前）执行 DB 预检 SQL：统计跨租户重复 email、重复租户名、短链/用户规模（用于评估迁移风险；见 `proposal.md` 附录 A）
- [√] 0.3 确认 `architecture_hardening#D003`（已选：Cookie 进生产 + 必须启用 CSRF/等价策略）
- [√] 0.4 明确灰度/回滚约束：允许短暂停机；允许数据清理（跨租户重复 email 的用户全部删除，不保留）

### 1. IAM：登录与唯一性约束（P0）

- [√] 1.1（路径A-已选）新增迁移：为 `users(email)` 增加全局唯一索引（开发阶段可直接执行；上线前/已有数据环境建议先做 0.2 预检）
- [√] 1.2（路径A）补齐回归：新增集成测试覆盖“跨租户重复 email 时注册/创建用户应失败”
- [-] 1.3（路径B）设计并落地 tenant 标识（tenantCode 或等价）：DB 字段/唯一约束/API 契约（D001 已选路径A，本项跳过）
- [-] 1.4（路径B）调整登录 API：`/api/v1/auth/login` request 增加租户字段，并补齐前端登录页与路由逻辑（D001 已选路径A，本项跳过）
- [√] 1.5 更新 `.helloagents/wiki/api.md`：明确登录契约（已选路径A）

### 2. 部署：Edge 最小权限与账号拆分（P1）

- [√] 2.1 为 MySQL 增加初始化脚本：创建 `linkforge_edge`（RO）账号并授权最小表集合；（可选）拆分 `linkforge_api` 时需同时考虑 Flyway 的 DDL 权限（见 `proposal.md` 附录 B）
- [√] 2.2 更新 `deploy/docker-compose.yml`：API/Edge 使用不同 `DB_USERNAME/DB_PASSWORD`（并兼容本地一键启动）
  - 依赖: 2.1
- [√] 2.3 更新 `deploy/.env.example` 与根 `README.md`：新增/解释两套 DB 账号与权限边界
- [√] 2.4 为 Edge 增加启动期“只读权限”自检（可选，至少在文档中给出验证方式）

### 3. 安全：Cookie 模式 + CSRF 策略（P1）

- [√] 3.1 设计 CSRF 策略（已确认 cookie 进生产；默认推荐 Spring Security CSRF + 双提交 cookie）
- [√] 3.2 更新 `server/api-app` 安全配置：cookie 模式下强制满足 CSRF 与 CORS 白名单约束（fail-fast）
  - 依赖: 3.1
- [√] 3.3 更新 `web/src/services/http.ts`：cookie 模式下自动携带 CSRF token（或等价机制）
  - 依赖: 3.2
- [√] 3.4 增加 e2e/集成测试：cookie 模式写接口在无 CSRF 时失败、有 CSRF 时成功

### 4. Analytics：flush 性能与可控性（P1/P2）

- [√] 4.1 优化 `AnalyticsFlushJob`：对 UV `PFCOUNT` 查询使用 pipeline/batch 降 RTT（保持语义不变）
- [√] 4.2 优化 `AnalyticsDimensionFlushJob`：评估 active-set×dimTypes 扫描成本，给出可配置上限/限速策略（或按需降级）
- [√] 4.3 增加基线指标：记录 flush 单次耗时、Redis 命令数、MySQL batch 大小（日志/指标二选一）
- [√] 4.4 文档化统计“best-effort + 回补窗口 + TTL”语义（避免运营误解为强一致）

### 5. Contract：消除隐式耦合（P2）

- [√] 5.1 抽取 `stats:*` key 命名与字段为显式 public contract（避免 package-private 跨模块依赖）
- [√] 5.2 增加契约测试：Redis key 格式变更必须触发测试失败（避免隐式破坏）

### 6. 配置校验：去重与一致性（P2）

- [√] 6.1 抽取 API/Edge 公共配置校验逻辑到 shared（或统一 Validator 组件），减少漂移点
- [√] 6.2 为关键开关增加“组合约束”校验（例如 cookie-enabled=true 时必须满足 CSRF/CORS 等条件）

### 7. OpenAPI：API Key 热点治理（P2）

- [√] 7.1 将 `api_keys.last_used_at` 更新改为节流/异步/采样（选型并实现）
- [√] 7.2 增加性能回归测试或压测脚本说明（避免 OpenAPI 高 QPS 写放大）

### 8. 文档与归档（SSOT）

- [√] 8.1 更新 `.helloagents/wiki/arch.md`：记录本次加固的边界/决策（实施完成后）
- [√] 8.2 执行完成后归档方案包到 `.helloagents/history/` 并更新 `.helloagents/history/index.md`

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|

---

## 执行备注

- 本方案包已执行完成，代码与文档已同步，详细改动见同目录 `why.md` / `how.md`。
- 已选择 D001 路径A（email 全局唯一）。开发阶段若数据库可重建，可直接新增唯一索引；上线前/已有数据环境再执行 0.2 预检与必要的数据清理。
- 统计链路优化建议优先做 pipeline 降 RTT；更大规模（Kafka/OLAP）属于后续演进，不纳入本次必须交付范围。

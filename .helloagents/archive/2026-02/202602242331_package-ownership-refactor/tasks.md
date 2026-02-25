# 任务清单: package-ownership-refactor

```yaml
@feature: package-ownership-refactor
@created: 2026-02-24
@status: completed
@mode: R3
@complexity: complex
@workflow: DELEGATED
```

<!-- LIVE_STATUS_BEGIN -->
状态: completed | 进度: 15/15 (100%) | 更新: 2026-02-25 00:39:00
当前: 已完成（已归档到 `.helloagents/archive/`）
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 15 | 0 | 0 | 15 |

---

## 任务列表

### 1. ANALYZE（现状与问题定位）

- [√] 1.1 盘点 split package 清单并制定 package ownership 规则（analytics/redirect/platform）

### 2. DESIGN（方案包）

- [√] 2.1 创建并填充方案包（proposal.md + tasks.md）
- [√] 2.2 运行 `validate_package.py` 验收方案包结构（⛔ 阻断性）

---

### 3. DEVELOP（API Service：包归属迁移）

- [√] 3.1 迁移 api-app analytics：`com.linkforge.analytics.*` → `com.linkforge.api.analytics.*`（含 web/service/job）
- [√] 3.2 迁移 api-app security/scheduling：`com.linkforge.platform.security|scheduling.*` → `com.linkforge.api.security|scheduling.*`
- [√] 3.3 迁移 api-app iam + shortlink：`com.linkforge.iam|shortlink.*` → `com.linkforge.api.iam|shortlink.*`
- [√] 3.4 更新 api-app 测试与架构规则（ArchUnit 规则仍能约束 web/service/repo 分层）

### 4. DEVELOP（Redirect Edge：包归属迁移）

- [√] 4.1 迁移 edge-app redirect：`com.linkforge.redirect.*`（edge 内实现）→ `com.linkforge.edge.redirect.*`
- [√] 4.2 迁移 shared 中的 `AnalyticsService` 到 edge-app：`com.linkforge.edge.analytics.*`（shared 仅保留 analytics core/contract）
- [√] 4.3 更新 edge-app 测试包名与 import

### 5. TOOLING（防回归）

- [√] 5.1 新增 split package 检测并接入 CI（检测到跨模块同名 package 直接失败）

### 6. VERIFY（构建与最小回归）

- [√] 6.1 后端构建与测试：`cd server && mvn -B test`（⛔ 阻断性）
- [√] 6.2 前端构建：`cd web && npm run build`（⚠️ 警告性，但建议跑）

### 7. KB & ARCHIVE（知识库同步与归档）

- [√] 7.1 更新 `.helloagents/wiki` 架构/模块文档（与代码包归属对齐）
- [√] 7.2 归档方案包到 `.helloagents/archive/YYYY-MM/` 并更新 `.helloagents/CHANGELOG.md`

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|
| 2026-02-25 00:17 | 3.1-3.4 | completed | api-app 包归属迁移完成（analytics/iam/shortlink/security/scheduling）+ ArchUnit 更新 |
| 2026-02-25 00:15 | 4.1-4.3 | completed | edge-app 包归属迁移完成（redirect + edge analytics）+ 测试同步 |
| 2026-02-25 00:10 | 5.1 | completed | split package 检测脚本落地并接入 CI |
| 2026-02-25 00:16 | 6.1 | completed | 后端 `mvn test`（api-app + edge-app + shared）通过 |
| 2026-02-25 00:18 | 6.2 | completed | 前端 `npm run build` 通过（修复 TS cookie 类型问题） |
| 2026-02-25 00:39 | 7.1 | completed | wiki 对齐包归属（analytics/iam/shortlink）+ 修正 `.helloagents/` 路径引用 |
| 2026-02-25 00:39 | 7.2 | completed | 方案包归档到 `.helloagents/archive/2026-02/` + 更新 `.helloagents/CHANGELOG.md` |

---

## 执行备注

> 记录执行过程中的重要说明、决策变更、风险提示等

- 本方案属于“结构治理优先”：允许短期不稳定，但仍以 `mvn test` 作为阻断性验收。
- 迁移期间避免无关格式化与 import 重排，减少冲突与 review 噪声。

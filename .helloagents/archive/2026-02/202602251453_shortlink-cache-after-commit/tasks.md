# 任务清单: shortlink-cache-after-commit

> **@status:** completed | 2026-02-25 15:25

```yaml
@feature: shortlink-cache-after-commit
@created: 2026-02-25
@status: pending
@mode: R3
```

<!-- LIVE_STATUS_BEGIN -->
状态: completed | 进度: 5/5 (100%) | 更新: 2026-02-25 15:24:30
当前: -
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 5 | 0 | 0 | 5 |

---

## 任务列表

### 1. 开发实施

- [√] 1.1 新增 afterCommit helper：在 `server/shared/src/main/java/com/linkforge/platform/tx/AfterCommit.java` 提供 `run(Runnable)`（有事务同步→注册 `afterCommit`；无事务→直接执行）。
- [√] 1.2 改造 `server/api-app/src/main/java/com/linkforge/api/shortlink/service/ShortLinkService.java`：将 `create/update/archive/restore/delete` 内的 `linkCacheService.put/evict` 调整为 afterCommit 执行，避免提交前驱逐与回滚污染。
  - 依赖: 1.1
- [√] 1.3 新增/更新测试：在 `server/api-app/src/test/java/com/linkforge/api/shortlink/service/ShortLinkCacheAfterCommitIntegrationTest.java` 覆盖 commit/rollback：
  - rollback：断言 Redis 不写入/不驱逐
  - commit：断言缓存按预期更新/驱逐（至少覆盖 `create`、`update`、`archive/restore/delete`）
  - 依赖: 1.2
- [√] 1.4 更新文档：修改 `.helloagents/wiki/modules/shortlink.md`，补充“短链缓存仅在事务 commit 后更新”的策略说明，并记录本次变更。
  - 依赖: 1.2
- [√] 1.5 运行测试并记录结果：执行 `cd server && mvn -pl api-app -am test`（需要 Docker/Testcontainers 可用）。
  - 依赖: 1.3

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|
| 2026-02-25 15:15 | 1.1 | completed | 新增 `AfterCommit.run(Runnable)`（shared） |
| 2026-02-25 15:17 | 1.2 | completed | `ShortLinkService` 缓存写/删改为 AFTER_COMMIT |
| 2026-02-25 15:18 | 1.3 | completed | 新增集成测试覆盖 commit/rollback 缓存行为 |
| 2026-02-25 15:20 | 1.4 | completed | 更新 shortlink 模块文档：补充缓存一致性策略 |
| 2026-02-25 15:24 | 1.5 | completed | `mvn test -pl api-app -am` 通过 |

---

## 执行备注

> 记录执行过程中的重要说明、决策变更、风险提示等

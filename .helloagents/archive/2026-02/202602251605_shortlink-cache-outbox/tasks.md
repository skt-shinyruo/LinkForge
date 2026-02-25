# 任务清单: shortlink-cache-outbox

```yaml
@feature: shortlink-cache-outbox
@created: 2026-02-25
@status: completed
@mode: R3
```

<!-- LIVE_STATUS_BEGIN -->
状态: completed | 进度: 7/7 (100%) | 更新: 2026-02-25 16:59:58
当前: 全部任务完成
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 7 | 0 | 0 | 7 |

---

## 任务列表

### 1. 开发实施

- [ ] 1.1 Flyway 迁移：新增 `server/api-app/src/main/resources/db/migration/V7__add_link_cache_outbox.sql`（link_cache_outbox 表 + 索引）。
- [x] 1.1 Flyway 迁移：新增 `server/api-app/src/main/resources/db/migration/V7__add_link_cache_outbox.sql`（link_cache_outbox 表 + 索引）。
- [x] 1.2 Outbox 写入：新增 `server/api-app/src/main/java/com/linkforge/api/shortlink/cache/LinkCacheOutboxRepository.java`，提供 `enqueueRefresh/listPending/markDone/markRetry`（code 主键合并去重）。
  - 依赖: 1.1
- [x] 1.3 Outbox 消费：新增 `server/api-app/src/main/java/com/linkforge/api/shortlink/service/LinkCacheOutboxJob.java`（@Scheduled + @SchedulerLock），按 DB 当前状态幂等 `put/evict`，失败指数退避。
  - 依赖: 1.2
- [x] 1.4 支持重试感知：改造 `server/shared/src/main/java/com/linkforge/redirect/service/LinkCacheService.java`，新增 `tryPut/tryEvict` 返回成功/失败（原 `put/evict` 仍保持 best-effort）。
  - 依赖: 1.2
- [x] 1.5 事务内写 outbox：改造 `server/api-app/src/main/java/com/linkforge/api/shortlink/service/ShortLinkService.java`，在 `create/update/archive/restore/delete` 事务内写 outbox（commit 后崩溃不丢），并保留 AFTER_COMMIT 快速路径。
  - 依赖: 1.2
- [x] 1.6 测试：增强 `server/api-app/src/test/java/com/linkforge/api/shortlink/service/ShortLinkCacheAfterCommitIntegrationTest.java` 覆盖 outbox：
  - create rollback：outbox 不落库
  - create commit：删除缓存后，手动执行 job 仍可刷新缓存（模拟 crash 后补偿）
  - archive commit：写入旧值后，手动执行 job 可驱逐缓存
  - 依赖: 1.3
- [x] 1.7 文档与验证：更新 `.helloagents/wiki/modules/shortlink.md` 与 `.helloagents/wiki/data.md`，补充 outbox 最终一致策略；运行 `cd server && mvn test -pl api-app -am`。
  - 依赖: 1.6

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|

---

## 执行备注

> 记录执行过程中的重要说明、决策变更、风险提示等

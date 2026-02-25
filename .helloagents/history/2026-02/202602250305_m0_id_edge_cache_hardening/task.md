# 任务清单: m0_id_edge_cache_hardening

```yaml
@feature: m0_id_edge_cache_hardening
@created: 2026-02-25
@status: completed
@mode: R3
```

<!-- LIVE_STATUS_BEGIN -->
状态: completed | 进度: 7/7 (100%) | 更新: 2026-02-25 11:21:20
当前: -
<!-- LIVE_STATUS_END -->

## 任务列表

### 1. ID 护栏（P0）

- [√] 1.1 新增共享校验：`StartupValidation.validateIdBasics`（strict/prod 禁止默认 1/1）
- [√] 1.2 API/Edge 启动期接入该校验（fail-fast）
- [√] 1.3 补齐单测覆盖 strict/default/range

### 2. Redirect 抗穿透（P1）

- [√] 2.1 增加配置项：`app.redirect.not-found-cache-ttl-seconds`（默认 60，0 关闭）
- [√] 2.2 `LinkCacheService` 支持 NOT_FOUND 负缓存（lookup + markNotFound）
- [√] 2.3 Edge 解析链路接入负缓存，并增加短码格式快速拒绝（<=32 + 仅字母数字）
- [√] 2.4 补齐 Edge 集成测试：同一 missing code 连续请求仅触发一次 DB 查询

### 3. 归档与验收

- [√] 3.0 同步知识库与变更日志（arch/modules/CHANGELOG）
- [√] 3.1 运行后端测试（`server/mvn test`）

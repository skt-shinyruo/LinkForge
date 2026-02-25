# Technical Design: m0_id_edge_cache_hardening

## 1) ID 护栏：prod/strict 下禁止默认 1/1

### 变更点

- 新增共享校验：`StartupValidation.validateIdBasics(...)`
  - 文件：`server/shared/src/main/java/com/linkforge/platform/config/StartupValidation.java`
  - 规则：
    - `app.id.worker-id` / `app.id.datacenter-id` 必须在 `0~31`（Snowflake 5 bits）
    - 当 `prod` profile 或 `app.strict-config=true` 时，禁止 `workerId=1 && datacenterId=1`
- API/Edge 启动期接入该校验：
  - `server/api-app/src/main/java/com/linkforge/api/ApiStartupValidator.java`
  - `server/edge-app/src/main/java/com/linkforge/edge/EdgeStartupValidator.java`

### 配置方式（示例）

推荐通过环境变量注入（与现有 `application.yml` 约定一致）：

```bash
export ID_WORKER_ID=2
export ID_DATACENTER_ID=1
```

> 注意：该护栏无法自动检测“跨实例是否重复”，但能显著降低“忘配导致灾难”的概率。

## 2) Redirect 抗穿透：短码快速拒绝 + NOT_FOUND 负缓存

### 变更点

- 新增配置项：`app.redirect.not-found-cache-ttl-seconds`（默认 60，0 表示关闭）
  - 文件：`server/shared/src/main/java/com/linkforge/platform/config/AppProperties.java`
  - 启动期校验：`server/shared/src/main/java/com/linkforge/platform/config/StartupValidation.java`
- Link 缓存服务支持负缓存（同 key 空值标记）：
  - 文件：`server/shared/src/main/java/com/linkforge/redirect/service/LinkCacheService.java`
  - 新增：
    - `lookup(code)`：一次 GET 同时识别命中/未命中/NOT_FOUND 负缓存
    - `markNotFound(code)`：按短 TTL 写入负缓存（best-effort）
- Edge 解析链路接入负缓存，并做短码格式快速拒绝：
  - 文件：`server/edge-app/src/main/java/com/linkforge/edge/redirect/service/RedirectService.java`
  - 规则：短码必须满足“非空、长度 <= 32、仅字母数字”；否则直接视为 not found（避免异常输入导致无效回源）

### 语义与影响

- 正常命中：行为不变（Redis 命中 → 直接跳转；未命中 → MySQL 回源 → 写回 Redis）。
- 短码不存在：
  - 第一次请求：MySQL miss → 写入负缓存（短 TTL）→ 返回 404/JSON 或 404 HTML（按 Accept 协商）
  - 负缓存 TTL 内的后续请求：Redis 命中 NOT_FOUND → **不再回源 MySQL**
- 新建/更新短链：API 会写入/刷新 `LinkMeta` 缓存，覆盖此前的 NOT_FOUND 标记（若存在）。

## 3) 测试与回归

- 新增单元测试：`server/shared/src/test/java/com/linkforge/platform/config/StartupValidationTest.java`
  - 覆盖 strict 模式对默认 1/1 的拒绝、范围校验
- 新增 Edge 集成测试：`server/edge-app/src/test/java/com/linkforge/RedirectNegativeCacheIntegrationTest.java`
  - 使用 `@SpyBean ShortLinkLookupRepository` 验证同一 missing code 连续请求仅触发一次 DB 查询


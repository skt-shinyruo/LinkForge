# Task List: redirect_experience_control

Directory: `.helloagents/history/2026-02/202602201026_redirect_experience_control/`

---

## 1. Data Model（MySQL / Flyway）

- [√] 1.1 为 `short_links` 增加跳转策略列（statusCode/preview/queryForward/unavailableLandingUrl），并保证向后兼容（可空 + 默认值），验证 why.md#scenario-per-link-status-code
  - Files (expected): `server/api-app/src/main/resources/db/migration/V*_*.sql`

## 2. API Service（server/api-app）

- [√] 2.1 扩展 `ShortLinkEntity` 与 Repo/Mapper：新增字段映射（redirectStatusCode/previewEnabled/unavailableLandingUrl/queryForwardMode/queryForwardAllowlist），验证 why.md#requirement-redirect-policy-per-link
  - Files (expected): `server/api-app/src/main/java/com/linkforge/shortlink/entity/ShortLinkEntity.java`
- [√] 2.2 扩展创建/更新 API DTO 与校验：允许按链接配置策略（含 URL 校验与枚举校验），验证 why.md#scenario-per-link-status-code
  - Files (expected): `server/api-app/src/main/java/com/linkforge/shortlink/web/ShortLinkController.java`
- [√] 2.3 更新 `ShortLinkService`：落库与更新时的字段处理 + 缓存驱逐（复用现有 evict/put），并在 LinkDto 回显新字段，验证 why.md#scenario-query-forward-allowlist
  - Files (expected): `server/api-app/src/main/java/com/linkforge/shortlink/service/ShortLinkService.java`

## 3. Shared（server/shared）

- [√] 3.1 扩展 `LinkMeta`：携带跳转策略字段（按链接状态码、preview 开关、query 策略、不可用落地页等），并保持缺失字段可回退默认，验证 why.md#requirement-redirect-policy-per-link
  - Files (expected): `server/shared/src/main/java/com/linkforge/redirect/service/LinkMeta.java`
- [√] 3.2 扩展 `AppProperties`：新增 redirect 体验配置（全局 not-found/gone 落地页、默认 query allowlist/mode、内部保留字段），并补充启动期配置校验，验证 why.md#requirement-redirect-unavailable-ui
  - Files (expected): `server/shared/src/main/java/com/linkforge/platform/config/AppProperties.java`, `server/*/src/main/java/*StartupValidator.java`

## 4. Redirect Edge（server/edge-app）

- [√] 4.1 扩展 `ShortLinkLookupRepository` 的查询列与 `LinkCacheService` 缓存结构：读取并缓存跳转策略字段，验证 why.md#requirement-redirect-policy-per-link
  - Files (expected): `server/edge-app/src/main/java/com/linkforge/redirect/service/ShortLinkLookupRepository.java`, `server/shared/src/main/java/com/linkforge/redirect/service/LinkCacheService.java`
- [√] 4.2 实现 query 透传/拼接（OFF/ALLOWLIST/ALL + 冲突策略 + 保留字段不透传），并在跳转前生成最终 URL，验证 why.md#scenario-query-forward-allowlist
  - Files (expected): `server/edge-app/src/main/java/com/linkforge/redirect/web/RedirectController.java` 或新增 `RedirectUrlBuilder`
- [√] 4.3 实现预览页（确认后再跳）：按链接开关 + Accept=html 才展示；确认参数不透传，验证 why.md#scenario-preview-confirm-then-redirect
  - Files (expected): `server/edge-app/src/main/java/com/linkforge/redirect/web/RedirectController.java` 或新增 Handler
- [√] 4.4 不可用体验：针对 LINK_NOT_FOUND / LINK_DISABLED / LINK_EXPIRED 输出 404/410 HTML 或落地页 redirect（按全局/按链接配置），并确保非 html 请求仍返回 JSON，验证 why.md#scenario-unavailable-404-html
  - Files (expected): `server/shared/src/main/java/com/linkforge/platform/api/GlobalExceptionHandler.java`（或 Edge 专用 Advice）, `server/edge-app/src/main/java/com/linkforge/**`
- [√] 4.5 按链接状态码生效：link 配置优先，否则回退全局默认 301/302，验证 why.md#scenario-per-link-status-code
  - Depends on task 4.1

## 5. Web Admin UI（web）【Full-feature】

- [√] 5.1 在创建/编辑短链表单中补齐跳转策略配置项（状态码、preview、query 透传模式与 allowlist、不可用落地页），并在列表/详情中展示，验证 why.md#requirement-redirect-policy-per-link
  - Files (expected): `web/src/views/*`, `web/src/components/*`, `web/src/services/*`

## 6. Security Check

- [√] 6.1 执行安全检查（G9）：query 透传的隐私与注入风险、HTML 输出信息最小化、默认安全配置、预览页内部参数不透传

## 7. Testing

- [√] 7.1 Edge：新增 MockMvc 测试覆盖 404/410 HTML、预览页、query 透传策略（OFF/ALLOWLIST/ALL）与冲突规则
  - Files (expected): `server/edge-app/src/test/java/**`
- [√] 7.2 API：新增/更新集成测试覆盖新字段 create/update/response 回显（必要时更新现有 e2e 测试）
  - Files (expected): `server/api-app/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`

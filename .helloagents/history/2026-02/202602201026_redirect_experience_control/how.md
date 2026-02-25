# Technical Design: redirect_experience_control

## Technical Solution

### Core Technologies
- Java 17 / Spring Boot 3.x（Edge/API）
- MySQL 8.x（短链配置持久化，Flyway 迁移）
- Redis 7.x（Edge 缓存 `LinkMeta`）
- HTML 页面：Edge 侧直接返回 `text/html`（不引入模板引擎，保持 Edge 轻依赖）
- URL 处理：Spring `UriComponentsBuilder`（query 合并与编码），并补充保留字段过滤

### Implementation Key Points

1. **浏览器友好页面（Content Negotiation）**
   - 判定规则：当 `Accept` 包含 `text/html`（或浏览器默认 accept）时，Edge 对 `/r/**` 的“不可用”返回 HTML。
   - 对非浏览器调用方（Accept 不含 html）维持 JSON 错误结构（复用现有 `GlobalExceptionHandler`），避免破坏 OpenAPI/脚本调用。
   - 建议实现方式：
     - 在 Edge 增加一个高优先级 `@ControllerAdvice/@RestControllerAdvice`，仅匹配 `/r/**`，对 `BusinessException(LINK_NOT_FOUND/LINK_DISABLED/LINK_EXPIRED)` 进行“HTML/落地页 redirect”的替代响应。
     - 或在 `RedirectController` 内部捕获并按 Accept 构建 ResponseEntity（但 Controller 逻辑会偏重，建议用 Advice）。

2. **不可用落地页策略**
   - not-found（不存在）：无法解析 tenant/link 配置 → 仅支持全局默认（properties）或内置 404 HTML。
   - gone（禁用/过期）：可基于 link meta 读取该链接的 `unavailableLandingUrl`，优先 redirect；未配置则返回 410 HTML。
   - 落地页跳转建议固定 302（避免浏览器/代理永久缓存），同时带 requestId 便于排障。

3. **预览页（确认后再跳）**
   - link 维度 `previewEnabled` 开关。
   - 交互协议：
     - 未确认：返回 200 HTML 预览页（展示目标域名/安全提示/继续按钮）。
     - 确认后：同一路由携带内部参数（如 `__lf_confirm=1`）触发跳转。
   - 安全处理：
     - 内部参数保留字段，不参与 query 透传。
     - 页面展示尽量仅显示域名与路径摘要，避免泄露完整敏感 query。

4. **按链接配置跳转状态码**
   - DB 增列 `redirect_status_code`（301/302，可空）。
   - Edge 取值：link 上配置优先；否则 fallback 到 `app.redirect.default-status-code`（保持兼容）。

5. **Query 透传/拼接策略**
   - link 维度字段：
     - `query_forward_mode`：OFF / ALLOWLIST / ALL（可空，fallback 到全局默认）。
     - `query_forward_allowlist`：逗号分隔或 JSON（方案建议先用逗号分隔；支持 `utm_*` 前缀通配）。
   - 合并规则（建议默认）：
     - 仅合并短链请求 query；fragment（#）保持原样。
     - 当目标 URL 已存在同名参数：默认不覆盖（目标优先）。
     - 过滤保留字段：`__lf_confirm`、`__lf_preview` 等内部参数不透传。
   - 实现建议：
     - 用 `UriComponentsBuilder.fromUriString(originalUrl)` 解析；
     - 根据模式筛选 request 参数；
     - 调用 `.queryParam(name, values...)` 追加；
     - 构建最终跳转 URL。

## Architecture Decision ADR

### ADR-001: 浏览器体验通过 Accept 协商输出 HTML，而非全局替换为 HTML
**Context:** Edge 与 API 共用统一异常处理；直接把全局异常改为 HTML 会破坏 API 调用方与前端的 JSON 协议。  
**Decision:** 仅对 `/r/**` 且浏览器请求（Accept=html）输出 HTML 页面；其余保持 JSON。  
**Rationale:** 兼容性优先，避免破坏现有 API/前端；同时显著改善浏览器体验。  
**Alternatives:** 全局返回 HTML → Rejection reason: API 端需要 JSON；破坏协议。  
**Impact:** 需要在 Edge 引入专用 Advice/Handler，明确 URL scope 与顺序（@Order）。

### ADR-002: Query 透传默认 OFF/ALLOWLIST（安全默认），支持按链接覆盖
**Context:** 全量透传易造成隐私泄露与不可预期行为，但运营常需要保留 utm 等参数。  
**Decision:** 默认 OFF 或 ALLOWLIST（含 `utm_*` 等默认名单），按链接可配置为 ALL。  
**Rationale:** 以安全默认值控制风险，同时满足增长追踪需求。  
**Impact:** 需要定义保留字段与冲突处理规则，并在文档中明确。

## API Design（新增字段）

### [POST] /api/v1/links（create）
- **Request (新增可选字段):**
  - redirectStatusCode: 301/302
  - previewEnabled: boolean
  - unavailableLandingUrl: string
  - queryForwardMode: OFF/ALLOWLIST/ALL
  - queryForwardAllowlist: string[]
- **Response:** LinkDto 增加上述字段回显（用于 UI 展示与编辑）

### [PUT] /api/v1/links/{id}（update）
- 同 create，允许更新上述字段；并在变更后触发 Edge 缓存驱逐（现有 `LinkCacheService.evict(code)` 机制复用）。

## Data Model（Flyway 迁移）

对 `short_links` 表增列（建议均可空、向后兼容）：
- `redirect_status_code` INT NULL
- `preview_enabled` BIT(1) NOT NULL DEFAULT 0
- `unavailable_landing_url` TEXT NULL
- `query_forward_mode` VARCHAR(16) NULL
- `query_forward_allowlist` VARCHAR(1024) NULL

## Security and Performance

- **Security**
  - 预览确认参数/内部参数不透传
  - query 透传默认关闭或白名单，避免泄露敏感信息
  - 目标 URL 仍由 API 侧强校验 http/https（复用现有 UrlValidator）
  - HTML 页面不输出内部异常细节；仅展示用户可理解信息 + requestId（可选）
- **Performance**
  - HTML 页面与预览页为短路径字符串生成；无模板引擎
  - query 合并只在需要时执行；按策略短路
  - DB 增列不改变主查询索引；Edge 只读回源仍可维持低延迟

## Testing and Deployment

- **Testing**
  - Edge：MockMvc 测试覆盖 Accept=html 时的 404/410/preview 行为
  - Edge：query 透传策略（OFF/ALLOWLIST/ALL）与冲突规则测试
  - API：创建/更新新字段的校验与回显测试
  - 回归：原有 JSON 错误响应在 Accept 非 html 时保持一致
- **Deployment**
  - 先发版 DB 迁移与 API（可写入新字段但不强依赖）
  - Edge 新逻辑默认兼容旧数据（字段可空）
  - 逐步在 UI/租户侧启用新能力（预览/透传/落地页）

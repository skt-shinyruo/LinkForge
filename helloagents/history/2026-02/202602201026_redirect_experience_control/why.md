# Change Proposal: redirect_experience_control（跳转体验与跳转行为可控性增强）

## Requirement Background

当前短链跳转主链路以“解析短码 → 缓存/回源 → 写统计 → 301/302 跳转”为主，在不可用场景（不存在/禁用/过期）会走统一异常处理并返回 JSON 结构。对浏览器用户而言体验较“硬”，常见问题包括：

1. **不可用时缺少浏览器友好页面**：短链不存在/已禁用/已过期时，浏览器直接看到 JSON，不符合用户预期（期望 404/410 的 HTML 提示页，或自定义落地页）。
2. **跳转策略不可控**：跳转状态码为全局默认值（301/302），缺少“按链接配置”的能力。
3. **Query 参数透传策略缺失**：短链请求携带的 query 参数无法按策略透传/拼接到原始链接（常见需求：保留 utm_* 或业务追踪参数）；同时缺少策略开关会导致运营/增长链路不可控。
4. **预览页能力缺失**：缺少“预览页（确认后再跳）”等能力，无法满足“更柔和”的跳转体验与更明确的用户认知（尤其是安全敏感或营销场景）。

本变更目标是在保持现有默认行为兼容的前提下，引入一套“可配置、可灰度、可回滚”的跳转体验与跳转策略体系，覆盖浏览器体验、按链接策略与参数透传策略。

## Product Analysis

### Target Users and Scenarios
- **User Groups:**
  - 访问短链的终端用户（浏览器用户，期望明确提示与可理解的页面）
  - 运营/增长人员（需要追踪参数透传、可控跳转策略）
  - 租户管理员（需要为不同链接配置不同跳转行为与落地页）
- **Usage Scenarios:**
  - 活动链接过期后仍被转发：需要明确提示（410）或引导到新的活动页
  - 追踪投放效果：希望保留 `utm_* / gclid / fbclid` 等参数
  - 安全/合规要求：需要预览确认页，减少“点开即跳”的突兀与误触
- **Core Pain Points:**
  - 不可用 JSON 影响用户理解与信任
  - 301/302 无法按链接控制，影响缓存策略与可控性
  - Query 透传缺失导致归因丢失；盲目全量透传又可能带来安全/隐私风险

### Value Proposition and Success Metrics
- **Value Proposition:**
  - 更友好的不可用体验（HTML 页面/自定义落地页），降低困惑与投诉
  - 更可控的跳转行为（按链接 301/302 + Query 透传策略），提高运营可控性与归因准确性
  - 可选预览确认页，提升“柔和感”与安全感
- **Success Metrics:**
  - 不可用场景页面可读性提升：减少用户反馈/工单（定性）
  - 追踪参数保留率提升（utm 等）：归因链路更完整（定量）
  - 预览页开启的链接：误跳转与用户反感降低（可通过点击确认率/跳出率辅助观察）

### Humanistic Care
- 页面内容避免恐吓式文案，提供明确行动建议（返回/联系/访问主页）
- 提供可访问性（基础语义、对比度、移动端可读）与最小化隐私暴露（不在页面展示完整原始 URL 的敏感部分，可展示域名与安全提示）

## Change Content

1. **不可用 HTML 页面（浏览器友好）**
   - 短链不存在：返回 404 的 HTML 页面（可配置跳转到自定义落地页）。
   - 短链禁用/过期：返回 410 的 HTML 页面（可配置跳转到自定义落地页）。
   - 保留对非浏览器/接口调用方的 JSON 错误响应（通过 Accept 协商）。
2. **自定义落地页/提示页能力**
   - 支持全局默认落地页配置（not-found / gone）。
   - 支持按链接配置“不可用落地页”覆盖全局默认（仅对禁用/过期可用）。
3. **预览页（确认后再跳）**
   - 按链接开关：开启后，浏览器首次访问显示预览页（200 HTML），用户确认后才执行 301/302 跳转。
   - 预览页展示：目标域名、简要提示与确认按钮；保留 query 透传策略（确认参数不透传）。
4. **按链接配置跳转状态码**
   - 在链接维度支持配置 301/302；未配置时使用全局默认值。
5. **Query 参数透传/拼接策略开关**
   - 支持 OFF / ALLOWLIST / ALL 三种策略（默认建议 OFF 或 ALLOWLIST）。
   - 支持按链接配置 allowlist（支持 `utm_*` 前缀通配）并与全局默认 allowlist 组合。
   - 冲突策略与保留字段：当原始 URL 已存在同名参数时，默认不覆盖；保留内部参数（如 `__lf_confirm`）不透传。

## Impact Scope

- **Modules:**
  - `server/edge-app`：Redirect 体验（HTML 页/预览页/Query 拼接/按链接 301/302）
  - `server/shared`：配置项扩展（redirect 体验与策略配置）、错误码/异常映射补齐（如需要）
  - `server/api-app`：短链创建/更新 API 扩展（新增按链接配置字段）+ Flyway 迁移（短链表增列）
  - `web/`：可选：管理后台表单支持配置（预览开关/状态码/query 策略/不可用落地页）
- **Files (expected):**
  - Edge：`server/edge-app/src/main/java/com/linkforge/redirect/web/RedirectController.java`（或新增 web handler/exception handler）
  - Shared：`server/shared/src/main/java/com/linkforge/platform/config/AppProperties.java`、`server/shared/src/main/java/com/linkforge/platform/api/GlobalExceptionHandler.java`
  - API：`server/api-app/src/main/resources/db/migration/V*_*.sql`、`ShortLinkEntity/Service/Controller/DTO`
  - Web：`web/src/views/*`、`web/src/components/*`（如做 UI 配置）
- **APIs:**
  - `/r/{code}`：新增 200（预览页）、404/410 HTML、可选落地页 redirect
  - `/api/v1/links`：create/update/response 增加跳转策略字段
- **Data:**
  - MySQL：`short_links` 增加跳转策略相关列（redirect status、query 策略、preview 开关、不可用落地页等）
  - Redis：缓存 `LinkMeta` 结构扩展（可接受缓存 miss 降级回源）

## Core Scenarios

### Requirement: redirect-unavailable-ui（不可用体验）
**Module:** edge-app / shared
不可用场景对浏览器用户提供友好页面或自定义落地页。

#### Scenario: unavailable-404-html
短码不存在或无法解析
- 浏览器（Accept: text/html）返回 404 HTML 页面（或按配置跳转到全局 not-found 落地页）
- 非浏览器客户端保持 JSON 错误响应（兼容现有）

#### Scenario: unavailable-410-html-or-landing
短码存在但已禁用/过期
- 浏览器返回 410 HTML 页面，或若该链接配置了不可用落地页则跳转至落地页

### Requirement: redirect-preview-page（预览确认页）
**Module:** edge-app
按链接启用预览页，确认后再跳转。

#### Scenario: preview-confirm-then-redirect
链接开启 preview；浏览器访问 `/r/{code}`
- 未确认时返回 200 HTML 预览页（展示目标域名 + “继续访问”按钮）
- 确认后执行 301/302 跳转；确认参数不透传到原始链接

### Requirement: redirect-policy-per-link（按链接跳转策略）
**Module:** api-app / edge-app / shared
支持按链接配置 301/302 与 query 透传策略。

#### Scenario: per-link-status-code
链接配置 redirectStatus=301
- Edge 返回 301；未配置则使用全局默认 302（保持兼容）

#### Scenario: query-forward-allowlist
短链请求携带 `utm_source=...&foo=bar`
- 当策略为 ALLOWLIST（默认 allowlist 包含 `utm_*`）时，仅透传 utm_*；foo 不透传
- 当策略为 OFF 时，均不透传

## Risk Assessment

- **Risk:** Query 全量透传可能导致安全/隐私风险（向目标站点泄露不必要参数、甚至带入敏感信息）
  - **Mitigation:** 安全默认值为 OFF/ALLOWLIST；提供保留字段清单与冲突策略（不覆盖原始 URL 参数）
- **Risk:** 预览页增加一次交互可能影响转化
  - **Mitigation:** 预览页按链接可选；提供 “仅对浏览器生效/仅特定场景启用” 的策略选项（方案中明确默认关闭）
- **Risk:** DB Schema 变更与缓存结构扩展导致兼容性问题
  - **Mitigation:** 新列可空；Edge 对缺失字段有默认值回退；缓存反序列化失败可降级为回源

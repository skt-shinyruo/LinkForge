# edge

## Purpose
提供短码解析与跳转服务（`/r/**`），并以低延迟方式采集统计信息（PV/UV/可选维度/可选访问事件），作为可独立部署的 Redirect Edge Service。

## Module Overview
- **Responsibility:** `/r/{code}` 解析、Redis 缓存（Cache-aside）、MySQL 只读回源、302/301 跳转、失效处理（404/410 HTML/落地页）、预览页（确认后跳转）、Query 透传策略（OFF/ALLOWLIST/ALL）、轻量统计写入（Redis）
- **Status:** 🚧In Development
- **Last Updated:** 2026-02-25
- **Code Root Package:** `com.linkforge.edge.*`（应用编排与实现）；依赖 shared 的 `com.linkforge.platform.*` / `com.linkforge.redirect.*` / `com.linkforge.analytics.*`（契约/可复用能力）

## Specifications

### Requirement: redirect-resolve（短码解析）
**Module:** edge
将短码解析为原始 URL，并处理启用/过期状态。

#### Scenario: redirect-302（默认 302 跳转）
前置条件：短码存在且 enabled=true 且未过期
- 返回 302（Location 指向 originalUrl）
- 推荐默认 302，避免浏览器/代理长期缓存影响统计与可控性

#### Scenario: redirect-archived（归档短链视为不可用）
前置条件：短码对应短链已归档（`archived_at` 非空）
- Edge 回源查询过滤归档记录（表现为短码不存在，返回 404）
- 用于治理场景的“可恢复下线”（由 API Service 提供归档/恢复操作）

#### Scenario: redirect-expired（过期/禁用）
前置条件：短码已过期或已禁用
- 浏览器请求（Accept=html）：返回 410 HTML 或跳转到落地页（按链接/全局配置）
- 非浏览器请求：返回 JSON 错误结构（HTTP 410 + code=41001/41002）

---

### Requirement: redirect-experience-control（跳转体验与行为可控性）
**Module:** edge
为浏览器用户提供更友好的不可用体验，并支持按链接配置跳转行为（状态码/预览页/query 透传策略）。

#### Scenario: unavailable-404-html（短码不存在）
前置条件：短码不存在
- Accept 包含 `text/html`：返回 404 的 HTML 页面，或按配置跳转到 `app.redirect.not-found-landing-url`
- 非 HTML 请求：保持 JSON 错误响应（HTTP 404 + code=40410）

#### Scenario: unavailable-410-html-or-landing（禁用/过期）
前置条件：短码存在但已禁用/过期
- Accept=html：优先跳转到该链接的 `unavailableLandingUrl`；否则跳转到全局 `app.redirect.gone-landing-url`；都未配置则返回 410 HTML
- 非 HTML 请求：保持 JSON 错误响应（HTTP 410 + code=41001/41002）

#### Scenario: preview-confirm-then-redirect（预览确认页）
前置条件：短码可用且 `previewEnabled=true`
- Accept=html 且未确认：返回 200 HTML 预览页（按钮链接为同路径 `__lf_confirm=1`）
- 确认后：执行 301/302 跳转，并写入统计（PV/UV）
- 内部参数（`__lf_confirm`）为保留字段，不参与 query 透传

#### Scenario: per-link-status-code（按链接状态码）
前置条件：短码可用且配置了 `redirectStatusCode`
- Edge 按链接配置返回 301/302
- 未配置时回退全局默认 `app.redirect.default-status-code`

#### Scenario: query-forward-policy（Query 透传策略）
前置条件：短码可用且短链请求携带 query 参数
- OFF：不透传任何 query
- ALLOWLIST：仅透传命中 allowlist 的参数（支持 `utm_*` 前缀通配；全局 allowlist 与 per-link allowlist 合并）
- ALL：透传所有非保留字段参数
- 冲突策略：目标 URL 已存在同名参数时不覆盖（目标优先）

### Requirement: redirect-cache（缓存）
**Module:** edge
优先读 Redis 缓存，缓存未命中再回源 MySQL。

#### Scenario: cache-hit（命中）
- 直接解析并跳转

#### Scenario: cache-miss（未命中）
- 回源 MySQL 查询后写回 Redis（设置 TTL）
- 若短码不存在：写入短 TTL 的负缓存（避免缓存穿透导致 DB 回源被打穿）

---

### Requirement: redirect-benchmark（压测）
**Module:** edge
提供 Redirect 链路的基线压测脚本与结果记录，便于容量评估与回归对比。

#### Scenario: k6-baseline
- 使用 k6 对 `/r/{code}` 进行压测
- 脚本与结果记录位置：`bench/redirect/`

---

### Requirement: edge-abuse-protection（防滥用 / 可信代理链 / 风控）
**Module:** edge
为公网跳转链路提供基础保护：可信代理链校验、安全取 IP、IP 黑白名单、限流与 bot 降频策略。

#### Scenario: trusted-proxy-chain（可信代理链）
前置条件：网关/反代为受信代理，且 `app.edge.trusted-proxies` 配置包含该代理的 IP/CIDR
- Edge 仅在 `remoteAddr` 命中 trustedProxies 时采信 `X-Real-IP` / `X-Forwarded-For`
- `VisitInfo.ip` 使用“可信解析后的客户端 IP”，用于 UV 指纹与日志定位

#### Scenario: spoofed-forwarded-headers（伪造转发头防护）
前置条件：公网客户端直连 Edge 或从非受信来源访问
- Edge 忽略 `X-Forwarded-For` / `X-Real-IP` 等头部，直接使用 `remoteAddr`
- 避免伪造 IP 污染 UV 指纹与风控日志

#### Scenario: risk-control（风控策略）
前置条件：开启 `app.edge.risk-control.enabled=true`
- IP denylist 命中返回 403
- 限流命中返回 429（可选 `Retry-After`）
- bot UA 命中可配置更严格阈值（默认“降频优先”，降低误伤）

## API Interfaces

### [GET] /r/{code}
**Description:** 短码解析并跳转

## Implementation Notes（实现约定）

- **Edge 侧避免引入 JPA**：只做只读回源查询（JDBC），减少依赖与启动成本
- **业务用例去 Servlet 化**：Service 只接收稳定值对象（如 `VisitInfo`），由 Controller 做协议适配
- **归档过滤**：回源查询带 `archived_at IS NULL`；归档短链在 Edge 侧视为不可用（表现为短码不存在）
- **短码快速拒绝**：进入缓存/回源前先校验短码长度与字符集（仅字母数字，且 `<=32`），减少异常输入导致的 key/日志/路由复杂度与无效回源
- **统计采集**：仅写 Redis（PV/UV + 活跃索引；可选维度 PV；可选访问事件 Stream），不在跳转链路写 MySQL 明细（明细由 API Service 异步落库）
- **浏览器体验（Accept 协商）**：仅当 Accept 包含 `text/html` 时输出 HTML 页面/预览页，避免破坏脚本/OpenAPI 调用方的 JSON 协议
- **Query 透传安全默认**：默认 OFF/ALLOWLIST；过滤内部保留字段（默认包含 `__lf_confirm`）
- **安全取 IP（可信代理链）**：默认不信任 forwarded headers；仅当 `remoteAddr` 命中 `app.edge.trusted-proxies` 才采信 `X-Real-IP/X-Forwarded-For`
- **风控（可配置）**：`/r/**` 可开启 IP 黑白名单/Redis 限流/bot 降频（默认关闭，建议先配置 trusted-proxies 再逐步灰度开启）

## Deployment Notes（部署建议）

- **网关第一道防线**：建议在网关/反代侧清洗 forwarded headers（覆盖客户端注入的 XFF）
- **Nginx real_ip（生产建议）**：如存在多层代理（CDN/Ingress/网关），建议使用 `real_ip_header` + `set_real_ip_from` + `real_ip_recursive on` 仅信任受控上游网段，再向 Edge 转发清洗后的 `X-Real-IP/X-Forwarded-For`
- **Edge trustedProxies（应用侧）**：`app.edge.trusted-proxies` 只填写“受控代理/网关”的 IP/CIDR，避免把真实客户端网段误配置为 trusted（否则会影响链路解析与风控准确性）
- **Docker Compose（开箱即用）**：`deploy/docker-compose.yml` 默认给 `EDGE_TRUSTED_PROXIES` 填充常见内网网段（适配 `web(Nginx)` → `server-edge` 反代）；生产环境务必改为你实际受控代理的 IP/CIDR（避免信任范围过大）

### 配置示例（建议从“先可信 IP，再逐步开启风控”开始）

```yaml
app:
  edge:
    # 仅当 remoteAddr 命中该列表时，Edge 才采信 X-Real-IP / X-Forwarded-For
    trusted-proxies:
      - 10.0.0.0/8
      - 192.168.0.0/16
    risk-control:
      enabled: true
      # deny 优先；allowlist 非空时将强制只允许白名单
      ip-denylist:
        - 203.0.113.0/24
      rate-limit:
        enabled: true
        window-seconds: 60
        ip-max-requests: 120
        ip-code-enabled: true
        ip-code-max-requests: 60
        fail-open: true
      bot:
        enabled: true
        user-agent-keywords: [bot, crawler, spider, curl, wget]
        ip-max-requests: 30
        block: false
```

### 配置示例（跳转体验与 Query 透传策略）

```yaml
app:
  redirect:
    # 可选：短码不存在（404）/禁用或过期（410）的浏览器落地页
    not-found-landing-url: https://example.com/404
    gone-landing-url: https://example.com/410

    # 可选：全局默认的 query 透传策略（安全默认建议 OFF 或 ALLOWLIST）
    query-forward-mode: ALLOWLIST
    query-forward-allowlist: [utm_*, gclid, fbclid]

    # 内部保留字段（不会被透传到原始链接）
    query-forward-reserved-params: [__lf_confirm, __lf_preview]
```

## Data Models

短链主数据来自 `short_links`（见 `.helloagents/wiki/data.md`）。

## Dependencies
- shortlink（短链数据来源：MySQL 只读回源）
- analytics（统计采集写入：Redis）
- platform/shared（ApiResponse、ErrorCode、RequestId、配置与基础设施封装）

## Change History
- [202602182227_shortlink_system_mvp](../../history/2026-02/202602182227_shortlink_system_mvp/) - MVP：缓存解析 + 302/301 跳转 + 统计采集
- [202602191324_ui_tags_charts_top_bench](../../history/2026-02/202602191324_ui_tags_charts_top_bench/) - 新增 Redirect 基线压测脚本与结果记录
- [202602191426_edge_api_split_refactor](../../history/2026-02/202602191426_edge_api_split_refactor/) - 后端拆分：Redirect 独立为 Edge Service（/r），统计采集与缓存治理增强
- [202602200935_edge_abuse_protection](../../history/2026-02/202602200935_edge_abuse_protection/) - Edge 防滥用/可信代理链/风控加固（安全取 IP、限流、黑白名单、bot 降频）
- [202602201026_redirect_experience_control](../../history/2026-02/202602201026_redirect_experience_control/) - 跳转体验与跳转策略增强（404/410 HTML、预览页、按链接 301/302、Query 透传策略）
- [202602201217_analytics_visit_events_dims](../../history/2026-02/202602201217_analytics_visit_events_dims/) - 统计增强：扩展 VisitInfo 采集维度，并支持写入维度 PV 与访问明细事件 Stream（可配置）
- [202602201407_lifecycle_governance_closure](../../history/2026-02/202602201407_lifecycle_governance_closure/) - 生命周期治理闭环：归档短链 Edge 侧不可达（回源过滤）
- [202602250305_m0_id_edge_cache_hardening](../../history/2026-02/202602250305_m0_id_edge_cache_hardening/) - M0 护栏：短码快速拒绝 + NOT_FOUND 负缓存（抗穿透）

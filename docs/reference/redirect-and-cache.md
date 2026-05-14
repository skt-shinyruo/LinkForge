# 跳转与缓存链路

## 业务目标

Redirect 是流量面，负责把 `/r/{code}` 请求解析成最终跳转响应。它不拥有短链事实数据，只维护 Redis 缓存；缓存未命中时必须回源 Shortlink 权威读端口。它还承担跳转前风控、预览页、不可用页、query 转发和点击额度预留。

## 流程图

<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="560" viewBox="0 0 1080 560" role="img" aria-label="跳转与缓存链路">
  <defs>
    <marker id="arrow-redirect" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="#334155"/>
    </marker>
    <style>
      .box{fill:#f8fafc;stroke:#64748b;stroke-width:1.4;rx:8}
      .cache{fill:#eef2ff;stroke:#4f46e5;stroke-width:1.4;rx:8}
      .warn{fill:#fff7ed;stroke:#ea580c;stroke-width:1.4;rx:8}
      .ok{fill:#ecfdf5;stroke:#059669;stroke-width:1.4;rx:8}
      .err{fill:#fef2f2;stroke:#dc2626;stroke-width:1.4;rx:8}
      .text{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:14px;fill:#0f172a}
      .small{font-family:Arial,"Microsoft YaHei",sans-serif;font-size:12px;fill:#475569}
      .line{stroke:#334155;stroke-width:1.5;fill:none;marker-end:url(#arrow-redirect)}
    </style>
  </defs>
  <rect class="box" x="30" y="55" width="145" height="70"/>
  <text class="text" x="103" y="84" text-anchor="middle">浏览器 / 客户端</text>
  <text class="small" x="103" y="106" text-anchor="middle">GET /r/{code}</text>

  <rect class="warn" x="230" y="40" width="175" height="100"/>
  <text class="text" x="318" y="70" text-anchor="middle">RedirectRiskControlFilter</text>
  <text class="small" x="318" y="92" text-anchor="middle">解析 IP / UA / Referer</text>
  <text class="small" x="318" y="112" text-anchor="middle">黑白名单 / 限流 / bot</text>

  <rect class="box" x="460" y="50" width="175" height="80"/>
  <text class="text" x="548" y="80" text-anchor="middle">RedirectController</text>
  <text class="small" x="548" y="102" text-anchor="middle">RequestMapper</text>
  <text class="small" x="548" y="122" text-anchor="middle">html / confirmed</text>

  <rect class="box" x="695" y="50" width="175" height="80"/>
  <text class="text" x="783" y="80" text-anchor="middle">RedirectService</text>
  <text class="small" x="783" y="102" text-anchor="middle">normalize code</text>
  <text class="small" x="783" y="122" text-anchor="middle">resolve meta</text>

  <rect class="cache" x="230" y="215" width="175" height="105"/>
  <text class="text" x="318" y="247" text-anchor="middle">LinkCacheService</text>
  <text class="small" x="318" y="270" text-anchor="middle">host + code 正缓存</text>
  <text class="small" x="318" y="290" text-anchor="middle">not found 负缓存</text>
  <text class="small" x="318" y="310" text-anchor="middle">Redis 异常 fail-open</text>

  <rect class="box" x="460" y="215" width="175" height="105"/>
  <text class="text" x="548" y="247" text-anchor="middle">ShortLinkReadPort</text>
  <text class="small" x="548" y="270" text-anchor="middle">权威回源</text>
  <text class="small" x="548" y="290" text-anchor="middle">host + code</text>
  <text class="small" x="548" y="310" text-anchor="middle">legacy 兼容</text>

  <rect class="warn" x="695" y="210" width="175" height="115"/>
  <text class="text" x="783" y="242" text-anchor="middle">可用性与额度</text>
  <text class="small" x="783" y="265" text-anchor="middle">enabled / active / expiresAt</text>
  <text class="small" x="783" y="285" text-anchor="middle">preview 确认</text>
  <text class="small" x="783" y="305" text-anchor="middle">应用点击额度</text>

  <rect class="ok" x="925" y="55" width="125" height="70"/>
  <text class="text" x="988" y="84" text-anchor="middle">302 / 301</text>
  <text class="small" x="988" y="106" text-anchor="middle">Location</text>

  <rect class="err" x="925" y="215" width="125" height="70"/>
  <text class="text" x="988" y="244" text-anchor="middle">HTML 错误页</text>
  <text class="small" x="988" y="266" text-anchor="middle">404 / unavailable</text>

  <rect class="box" x="695" y="400" width="175" height="82"/>
  <text class="text" x="783" y="430" text-anchor="middle">VisitRecorderPort</text>
  <text class="small" x="783" y="452" text-anchor="middle">只在真实跳转前记录</text>
  <text class="small" x="783" y="472" text-anchor="middle">交给 Analytics</text>

  <rect class="box" x="925" y="395" width="125" height="92"/>
  <text class="text" x="988" y="425" text-anchor="middle">RedirectUrlBuilder</text>
  <text class="small" x="988" y="447" text-anchor="middle">query 转发</text>
  <text class="small" x="988" y="467" text-anchor="middle">保留参数过滤</text>

  <path class="line" d="M175 90 H230"/>
  <path class="line" d="M405 90 H460"/>
  <path class="line" d="M635 90 H695"/>
  <path class="line" d="M783 130 V210"/>
  <path class="line" d="M695 268 H635"/>
  <path class="line" d="M460 268 H405"/>
  <path class="line" d="M405 245 H460"/>
  <path class="line" d="M870 268 H925"/>
  <path class="line" d="M783 325 V400"/>
  <path class="line" d="M870 441 H925"/>
  <path class="line" d="M870 90 H925"/>
</svg>

## 主流程

1. `RedirectRiskControlFilter` 只过滤 `/r/**`，解析 `VisitInfo` 并执行风控。
2. `RedirectController.redirect()` 接收 path code。
3. `RedirectHttpRequestMapper.fromHttp()` 读取 host、Accept、`__lf_confirm` 和 request attribute 中的 `VisitInfo`。
4. `RedirectService.resolve()` 规范化短码。短码非法直接返回 not found。
5. 按 `host + code` 查询 `LinkCachePort`。
6. 命中负缓存直接 not found。
7. 命中正缓存直接得到 `LinkMeta`。
8. 未命中时调用 `ShortLinkReadPort.findRedirectMetaByHostAndCode()` 回源。
9. 回源命中写正缓存；真实未命中写短 TTL 负缓存。
10. 校验 `enabled`、生命周期、过期时间。
11. HTML 请求且开启 preview、未确认时返回预览页，不计访问。
12. 应用级短链执行点击额度预留。
13. 真正跳转前调用 `VisitRecorderPort.recordVisit()`。
14. `RedirectHttpResponseWriter` 返回 preview、not found、unavailable 或 redirect 响应。

## 风控规则

- 默认只信任 `request.remoteAddr`。
- 只有 remoteAddr 命中 `app.edge.trusted-proxies`，才采信 `X-Real-IP` 或 `X-Forwarded-For`。
- XFF 从右向左剔除可信代理，取第一个非可信代理。
- denylist 优先。
- allowlist 非空时，不在 allowlist 的 IP 被拒绝。
- bot 可直接阻断，或使用更低的 bot IP 限流阈值。
- IP 限流和 IP+code 限流通过 Redis 固定窗口计数。
- 限流 Redis 异常可按配置 fail-open。

## 缓存规则

- 无 host key：`link:code:{code}`。
- host key：`link:host:{host}:code:{code}`。
- 正缓存 TTL 使用 `app.redirect.cache-ttl-seconds`。
- 负缓存 sentinel 是 `__lf_not_found__`，TTL 使用 `app.redirect.not-found-cache-ttl-seconds`。
- Redis 读异常当作 miss，回源 Shortlink。
- Redis 写异常只记录 debug，不影响跳转。
- 反序列化失败会删除缓存并按 miss 处理。

## 预览、不可用与跳转响应

`RedirectHttpResponseWriter` 根据 `RedirectResolution.kind()` 分派：

- `PREVIEW`：返回预览 HTML，用户确认链接由 `RedirectConfirmHrefBuilder` 生成。
- `NOT_FOUND`：HTML 请求返回 not found 页面或全局落地页；非 HTML 抛业务 404。
- `UNAVAILABLE`：HTML 请求返回禁用、过期、额度耗尽页面或落地页；非 HTML 抛对应业务错误。
- `REDIRECT`：用 `RedirectUrlBuilder` 构造最终 URL，设置 `Location`，状态码取短链级 301/302，否则取全局默认。

## query 转发

`RedirectUrlBuilder` 执行最终 URL 构造：

- 模式优先级：短链级配置 > 全局配置 > 默认 `OFF`。
- `ALLOWLIST` 会合并全局 allowlist 和短链 allowlist。
- `__lf_confirm`、`__lf_preview` 等保留参数永不转发。
- 目标 URL 已有 query key 优先，请求参数不能覆盖。
- 追加 query 最大 2048，最终 URL 最大 4096；超限或异常时回退原始 URL。

## 点击额度

`RedirectQuotaGuard` 只对带 `applicationId` 的短链生效：

- 通过 `ApplicationScopePort.findApplicationQuota()` 读取额度。
- `monthlyClickLimit <= 0` 表示不限。
- 按 UTC 月度窗口调用 `ApplicationClickQuotaReservationPort.tryReserveMonthlyClick()`。
- Analytics 的 Redis quota adapter 会先用 Redis Lua 预留；counter 缺失时从 MySQL 聚合统计读取基线再 seed。
- 额度查询或预留失败时是否放行由配置决定。

## 源码分析

- `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectRiskControlFilter.java`
  - `doFilterInternal()` 解析 `VisitInfo`，调用 `RedirectRiskControl.check()`，拒绝时直接写响应。
- `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectClientIpResolver.java`
  - 实现可信代理和 XFF 解析。
- `server/redirect/application/src/main/java/com/linkforge/redirect/application/risk/RedirectRiskControl.java`
  - denylist、allowlist、bot、IP 限流、IP+code 限流。
- `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectController.java`
  - `GET /r/{code}` 入口。
- `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectHttpRequestMapper.java`
  - 从 HTTP 请求构造 `ResolveRedirectRequest`。
- `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectService.java`
  - `resolve()` 是核心决策方法。
  - `findMeta()` 先查缓存，再回源 Shortlink，最后写正缓存或负缓存。
- `server/redirect/infrastructure/src/main/java/com/linkforge/redirect/infrastructure/cache/LinkCacheService.java`
  - Redis 正/负缓存实现。
- `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectQuotaGuard.java`
  - 点击额度校验。
- `server/redirect/application/src/main/java/com/linkforge/redirect/application/RedirectUrlBuilder.java`
  - query 转发和最终跳转 URL 构造。
- `server/redirect/interfaces/src/main/java/com/linkforge/redirect/interfaces/web/RedirectHttpResponseWriter.java`
  - 把应用层 resolution 转成 HTTP 响应。

## 一致性边界

Redirect 的缓存只是加速层。短链创建、更新、归档、恢复、删除都在事务提交后驱逐缓存。如果缓存失效或 Redis 不可用，Redirect 会回源 Shortlink 权威读端口，因此不会依赖异步投影来保证跳转正确性。

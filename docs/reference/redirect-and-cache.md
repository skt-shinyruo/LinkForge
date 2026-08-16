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

Shortlink 对 `findRedirectMetaByHostAndCode()` 的发布实现使用只读事务；生产 ShardingSphere 配置把事务内读取路由到 primary，避免副本延迟产生错误负缓存或旧目标正缓存。该边界只覆盖跳转元数据，ownership、summary 等可容忍延迟的控制面查询仍保持普通读路由。Redirect 继续只依赖 `ShortLinkReadPort`，不维护第二份链接事实。

### 输入归一化与内容协商

- HTTP 入口只有 `GET /r/{code}`。短码会先 `trim`，随后要求长度为 1-32 且只包含 ASCII 字母数字；短码大小写敏感。非法短码在读缓存前即归为 `NOT_FOUND`。
- host 优先取 Servlet 的 `serverName`，为空才回退 `Host` header；值会转为小写并去掉端口。它不读取 forwarded host，因此反向代理必须正确设置请求的 server name/host。
- 只有 `Accept` 包含 `text/html` 或 `application/xhtml+xml` 才是 HTML 请求；缺少或其他 `Accept` 均按非 HTML 处理。
- `__lf_confirm` 只判断参数是否存在，不校验其值。HTML 预览页的确认链接会保留受上限约束的原始参数，再追加 `__lf_confirm=1`；内部 `__lf_confirm`、`__lf_preview` 不会被重复带入。

## 风控规则

- `app.edge.risk-control.enabled` 默认是 `false`；关闭时 filter 仍构造访问上下文，但 `RedirectRiskControl` 直接允许请求，不执行名单、bot 或限流判定。
- 默认只信任 `request.remoteAddr`。
- 只有 remoteAddr 命中 `app.edge.trusted-proxies`，才采信 `X-Real-IP` 或 `X-Forwarded-For`。
- 在可信代理前提下，合法的 `X-Real-IP` 优先于 XFF；否则 XFF 从右向左剔除可信代理，取第一个非可信代理。remoteAddr 非法、XFF 过长或无法解析时保留 remoteAddr。
- denylist 优先。
- allowlist 非空时，不在 allowlist 的 IP 被拒绝。
- bot 可直接阻断，或使用更低的 bot IP 限流阈值。
- IP 限流和 IP+code 限流通过 Redis 固定窗口计数。
- 限流 Redis 异常可按配置 fail-open。

拒绝名单或 bot 时返回 403；限流或 fail-closed 的限流故障返回 429，并在有窗口信息时带 `Retry-After`。这条 filter 直接按 `Accept` 输出 HTML 或 JSON，并始终设置 `Cache-Control: no-store` 与 `Pragma: no-cache`。它把 IP、截断后的 User-Agent、Referer、Accept-Language 和受 Analytics tracking allowlist 限制的 query 参数放入 `VisitInfo`；这只是访问上下文，不是对任意 query 的采集授权。

## 缓存规则

- 无 host key：`link:code:{code}`。
- host key：`link:host:{host}:code:{code}`。
- 正缓存 TTL 使用 `app.redirect.cache-ttl-seconds`，启动校验要求其大于 0。
- 负缓存 sentinel 是 `__lf_not_found__`，TTL 使用 `app.redirect.not-found-cache-ttl-seconds`；值为 0 时关闭负缓存。
- Redis 读异常当作 miss，回源 Shortlink。
- Redis 写异常只记录 debug，不影响跳转。
- 反序列化失败会删除缓存并按 miss 处理。

缓存查询有严格的三态，不能把它们混为“找不到”：

| 结果 | 含义 | 后续行为 |
| --- | --- | --- |
| 正缓存命中 | 已有 `LinkMeta` 快照 | 直接进入 Redirect 可用性判断 |
| 负缓存命中 | 权威读曾确认不存在 | 直接返回 not found，不回源 |
| miss | 无 key、Redis 故障或坏值 | 同步调用 `ShortLinkReadPort` |

Redis 读故障和坏序列化必须是 miss，而不是负缓存。正/负缓存写入失败只影响性能，不能改变跳转结论。Shortlink 写侧在事务内写 durable cache-invalidation outbox，提交后快速驱逐、worker 重试；它会重复尝试驱逐已知的正/负 key，但不提供固定交付时限。若 `domainId` 已无法反查 hostname，worker 只能驱逐无 host key，残留 host key 依赖 TTL 收敛。

权威读端口抛出异常不会被伪装成不存在，也不会写负缓存；异常会继续进入 Redirect 的异常处理路径。只有 `ShortLinkReadPort` 正常返回空结果时，才允许写 `__lf_not_found__`。

## 预览、不可用与跳转响应

`RedirectHttpResponseWriter` 根据 `RedirectResolution.kind()` 分派：

- `PREVIEW`：只会由 HTML 请求产生，返回带 `no-store` 的 200 预览 HTML，用户确认链接由 `RedirectConfirmHrefBuilder` 生成。
- `NOT_FOUND`：HTML 请求在配置了全局 not-found landing 时返回 302；否则返回带 `no-store` 的 404 页面。非 HTML 抛业务 404。
- `UNAVAILABLE`：额度耗尽的 HTML 请求始终返回带 `no-store` 的 429 页面，不使用 landing URL。禁用或过期的 HTML 请求依次尝试短链 `unavailableLandingUrl`、全局 gone landing（命中时均为 302），都未配置时才返回带 `no-store` 的 410 页面；非 HTML 抛对应业务错误。
- `REDIRECT`：用 `RedirectUrlBuilder` 构造最终 URL，设置 `Location`，状态码取短链级 301/302，否则取全局默认。

静态可用性要求 `enabled=true` 且 lifecycle 为 `ACTIVE`；`expiresAt` 按 UTC 判断，恰好等于当前时刻时已经不可跳转。

| 事实/策略 | resolution | HTML 请求 | 非 HTML 请求 | 是否记录访问 |
| --- | --- | --- | --- | --- |
| code 不合法、负缓存、权威未命中 | `NOT_FOUND` | 302 全局 landing 或 404 HTML | 404 JSON 错误 | 否 |
| disabled、非可跳转 lifecycle、过期 | `UNAVAILABLE` | 302 短链/全局 landing 或 410 HTML | 410 JSON 错误 | 否 |
| 应用点击额度拒绝 | `UNAVAILABLE(QUOTA_EXCEEDED)` | 429 HTML，不走 landing | 429 JSON 错误 | 否 |
| preview enabled 且 HTML 未确认 | `PREVIEW` | 200 确认页 | 不适用，继续跳转决策 | 否 |
| 所有检查通过 | `REDIRECT` | 301/302 Location | 301/302 Location | 是，跳转前 |

响应 writer 对动态 HTML 文本进行转义。HTML 页面和风控 filter 的直接错误响应使用 `Cache-Control: no-store`；普通非 HTML 业务异常由 `RedirectGlobalExceptionHandler` 返回 JSON，当前没有额外设置该响应头。全局 landing URL 在启动期校验为 http(s)；短链 landing URL 也只会在运行时通过 http(s) 检查后使用。

## query 转发

`RedirectUrlBuilder` 执行最终 URL 构造：

- 模式优先级：短链级配置 > 全局配置 > 默认 `OFF`。
- `ALLOWLIST` 会合并全局 allowlist 和短链 allowlist。
- `__lf_confirm`、`__lf_preview` 等保留参数永不转发。
- 目标 URL 已有 query key 优先，请求参数不能覆盖。
- 追加 query 最多 2048 个字符；会使该部分超限的参数及其后的参数被丢弃，已经接受的参数仍保留。最终 URL 超过 4096，或构造异常时才回退原始 URL。

## 点击额度

`RedirectQuotaGuard` 只对带 `applicationId` 的短链生效：

- 通过 `ApplicationScopePort.findApplicationQuota()` 读取额度。
- quota 查询按 `tenantId + applicationId` 缓存在进程内，默认 TTL 为 30 秒；空 quota 表示该链接在 Redirect 侧不受月点击额度限制。
- `monthlyClickLimit <= 0` 表示不限。
- 按 UTC 月度窗口调用 `ApplicationClickQuotaReservationPort.tryReserveMonthlyClick()`。
- Analytics 的 Redis quota adapter 会先用 Redis Lua 预留；counter 缺失时从 MySQL 聚合统计读取基线再 seed。
- 额度故障有两层：`RedisApplicationClickQuotaReservationPort` 内的 Redis script、null 返回、baseline 查询和 seed 异常固定返回允许（fail-open）；`RedirectQuotaGuard` 仍收到的 Platform quota 查询或端口异常才由 `app.analytics.quota.fail-open` 决定放行或按额度不可用处理。
- Redis counter 首次建立使用 MySQL 已 flush PV 作为 baseline，因此 stream/flush 延迟和 fail-open 窗口可能暂时低估实际点击量。

`monthlyClickLimit <= 0` 表示不限制。额度以 UTC 月 `[monthStart,nextMonthStart)` 计算，counter 会延长到下月后两天以覆盖边界恢复。

额度预留发生在 `VisitRecorderPort.recordVisit()` 和 HTTP 301/302 写出之前，之后的统计写入失败或浏览器未实际到达目标站点都不会回滚预留。Analytics 访问事件的默认 `events.fail-open=true` 会吞掉 append 故障；改为 false 时异常会中断本次跳转响应，但已经成功的额度预留仍会保留。

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

Redirect 的缓存只是加速层。短链创建、更新、归档、恢复、删除都在业务事务中写 outbox，提交后快速驱逐缓存，并由 worker 重试。如果缓存失效或 Redis 不可用，Redirect 会回源 Shortlink 权威读端口，因此不会依赖异步投影来保证跳转正确性。

排障顺序：先确认当前 host/code 的权威 `ShortLinkReadPort` 结果，再检查正/负缓存和失效 outbox；风控问题检查 remoteAddr 是否真的落在 trusted proxy CIDR 后才读取 `X-Real-IP/X-Forwarded-For`；额度问题同时检查 Redis counter、MySQL baseline、quota 配置和 fail-open 日志。访问上下文会传给 Analytics；Redirect 在风控拒绝日志中也会记录解析后的客户端 IP，因此生产日志应按隐私政策做访问控制、脱敏和保留期管理。页面只展示 code 和 requestId，不展示 IP。

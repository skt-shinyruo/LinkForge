# Change Proposal: edge_abuse_protection（Edge 防滥用 / 可信代理 / 风控加固）

## Requirement Background

当前 Redirect Edge 主链路基本为“解析短码 → 缓存/回源 → 写统计 → 302/301”，但缺少面向公网的基本保护（限流、IP 黑白名单、bot/crawler 识别、异常流量熔断/降级等）。同时客户端 IP 获取逻辑优先取 `X-Forwarded-For` 首段，若网关/反代未强制清洗并约束可信代理链，存在被伪造的风险，会直接影响：

1. UV 指纹（统计去重）准确性（当前 UV 指纹依赖 `ip + user-agent`）。
2. 日志定位与风控策略的可信度（同一攻击者可伪造大量来源 IP）。
3. 回源与缓存链路的抗压能力（被刷量/枚举时，MySQL/Redis 压力显著上升）。

本变更目标是在“网关 + 应用”两层同时建立防线：网关负责清洗与基础限流，Edge 应用负责可信边界校验与二级策略，避免单点配置失误造成安全退化。

## Change Content

1. **可信代理链（Trusted Proxy Chain）与安全取 IP**
   - 仅当 `remoteAddr` 属于配置的 `trustedProxies` 时，才采信 `Forwarded/X-Forwarded-For/X-Real-IP`。
   - 支持多层代理链：从右向左剔除可信代理后选取客户端 IP，并做 IP 合法性校验与降级回退。
2. **/r/** 防滥用与风控（应用侧二级防线）**
   - IP 黑白名单（支持 CIDR）。
   - 基于 Redis 的限流（至少按 IP 维度；可选增加按 code 维度防枚举/防热点刷量）。
   - bot/crawler 识别策略（UA 规则可配置，支持更严格限流或直接拒绝）。
3. **网关/反代加固（第一道防线）**
   - 默认部署配置下强制覆盖/清洗 `X-Forwarded-For`，避免客户端直传头部污染。
   - 提供生产建议配置：`real_ip_header` + `set_real_ip_from` + `real_ip_recursive`（仅信任受控上游）。
4. **可观测与可回滚**
   - 拦截/限流返回明确的 HTTP 状态码（403/429）与可定位的 requestId。
   - 关键策略均配置化，支持灰度与快速回滚（关闭风控或放宽阈值）。

## Impact Scope

- **Modules:**
  - `server/edge-app`：增加风控 Filter/组件、调整 VisitInfo 构造与日志
  - `server/shared`：补充配置项（AppProperties 扩展）、可复用 IP 解析工具/值对象（如需要）
  - `web/`：Nginx 反代配置加固（/r/**）
- **Files (expected):**
  - `server/edge-app/src/main/java/com/linkforge/redirect/web/RedirectController.java`
  - `server/edge-app/src/main/java/com/linkforge/**`（新增 filter / resolver / risk-control 相关类）
  - `server/shared/src/main/java/com/linkforge/platform/config/AppProperties.java`
  - `web/nginx.conf`
  - `server/**/src/test/**`（单元/集成测试新增）
  - `helloagents/wiki/modules/redirect-edge.md`
  - `helloagents/CHANGELOG.md`
- **APIs:**
  - `/r/{code}`（对外路径不变；新增 403/429 拒绝语义）
- **Data:**
  - Redis：新增限流与风控计数 key（带 TTL）；不引入 MySQL 表结构变更

## Core Scenarios

### Requirement: trusted-proxy-chain
**Module:** server/edge-app / server/shared / web
在可信边界内安全解析客户端 IP，避免 `X-Forwarded-For` 伪造污染统计与日志。

#### Scenario: spoofed-xff
公网客户端携带伪造 `X-Forwarded-For: 1.2.3.4` 发起请求。
- 若请求直达 Edge（`remoteAddr` 非受信代理），Edge 必须忽略 XFF，使用 `remoteAddr` 作为 IP。
- 若请求经受信网关转发，Edge 从受信链路中解析出真实客户端 IP；UV 指纹与日志使用该 IP。

### Requirement: edge-risk-control
**Module:** server/edge-app
对 `/r/**` 增加可配置的二级防滥用策略，减少刷量与回源压力。

#### Scenario: burst-request
同一 IP 在短时间内高频访问 `/r/{code}`。
- 命中阈值后返回 `429 Too Many Requests`，且尽量不触发 DB 回源（拦截应发生在 resolve 前）。

#### Scenario: ip-allow-deny
命中 IP denylist 或不在 allowlist。
- 返回 `403 Forbidden`；日志记录拒绝原因与 requestId。

### Requirement: bot-traffic-policy
**Module:** server/edge-app
识别明显的 bot/crawler UA 并施加更严格策略。

#### Scenario: known-bot-ua
User-Agent 命中规则（如包含 bot/crawler/spider/curl 等）。
- 可配置为：更低限流阈值或直接拒绝（默认建议“更低阈值”，降低误伤）。

### Requirement: gateway-hardening
**Module:** web
网关侧清洗/覆盖转发头，降低上游信任模型配置错误时的风险。

#### Scenario: overwrite-forwarded-headers
客户端携带自定义 `X-Forwarded-For` 进入网关。
- 网关必须覆盖/清洗后再转发，确保 Edge 不会看到客户端注入的首段 IP。

## Risk Assessment

- **Risk:** trustedProxies 配置错误导致 IP 识别退化或误判
  - **Mitigation:** 安全默认值（不配 trustedProxies 即不信任任何 forwarded headers）；启动期配置校验；灰度发布
- **Risk:** 限流/黑白名单误伤正常用户
  - **Mitigation:** 阈值与策略配置化；提供白名单；bot 策略默认“降频不封禁”；监控拦截量与 4xx 变化
- **Risk:** Redis 异常导致风控不可用或引入额外延迟
  - **Mitigation:** 失败降级策略（默认 fail-open + 记录日志）；限流实现尽量 O(1) 且少 RTT（Lua 脚本原子操作）

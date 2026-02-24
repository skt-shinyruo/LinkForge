# Technical Design: edge_abuse_protection

## Technical Solution

### Core Technologies
- Java 21 / Spring Boot 3.x（Edge：`spring-boot-starter-web`）
- Redis 7.x（现有依赖：`spring-boot-starter-data-redis`）
- Nginx（web 容器反代 / 生产环境网关示例）
- IP/CIDR 解析：JDK `InetAddress`（必要时自实现 CIDR 工具类，避免引入新依赖）

### Implementation Key Points

1. **可信代理链与客户端 IP 解析（安全默认）**
   - 新增配置项：`app.edge.trusted-proxies`（CIDR 列表，可为空）。
   - 解析规则：
     - 若 `request.getRemoteAddr()` 不在 `trustedProxies`：直接使用 `remoteAddr`，忽略所有 forwarded headers（安全边界）。
     - 若在 `trustedProxies`：按优先级解析客户端 IP（建议：`Forwarded` → `X-Real-IP` → `X-Forwarded-For`）。
     - 对解析出的 IP 做合法性校验（IPv4/IPv6），非法则降级为 `remoteAddr`。
   - 多层代理链（XFF）处理建议：
     - 将 XFF 拆分为 IP 列表（trim）。
     - 从右向左剔除 `trustedProxies`；取第一个“非 trusted” IP 作为客户端 IP；若全部为 trusted，则降级为 `remoteAddr`。
   - 目的：避免“客户端伪造首段 XFF”污染统计与日志。

2. **/r/** 风控 Filter（应用侧二级防线）**
   - 仅对 `/r/**` 生效（避免影响 API/Actuator）。
   - 在进入 Controller 前完成：
     - 构造 `VisitInfo(ip, userAgent)` 并挂载到 request attribute（Controller 复用，避免重复解析）。
     - IP allowlist/denylist（CIDR 支持）：
       - denylist 命中直接 403；
       - allowlist 非空时，未命中则 403；
     - 限流（Redis）：
       - 至少按 `clientIp` 维度（全局），可选按 `clientIp + code`（热点/枚举）。
       - bot/crawler UA：可配置更严格阈值或直接拒绝。
     - 响应与可观测：
       - 限流返回 429；黑白名单返回 403。
       - 返回头带 `X-Request-Id`（现有 `RequestIdFilter` 已生成）。
       - 日志记录：`reasonCode`、`clientIp`、`code`、`requestId`。

3. **限流实现（Redis + Lua，原子操作）**
   - 建议使用“固定窗口”作为 MVP（简单、低成本）：
     - key 示例：`rl:r:ip:{ip}:{epochMinute}` / `rl:r:ip_code:{ip}:{code}:{epochMinute}`
     - Lua 脚本：`INCR` 后若为 1 则 `EXPIRE`（确保 TTL 原子设置）
     - 达到阈值后拒绝
   - 失败策略（建议默认）：Redis 异常时 fail-open（不阻断跳转主链路），但打点日志便于排障；如需更强防护可切换 fail-close。

4. **网关/反代层加固（第一道防线）**
   - 对当前仓库 `web/nginx.conf`（本地 compose）建议：
     - 覆盖 `X-Forwarded-For` 为 `$remote_addr`（丢弃客户端注入的 XFF）。
   - 生产建议（示例，具体按部署环境调整）：
     - 使用 Nginx `real_ip` 模块，仅信任受控上游（CDN/Ingress/内网网关）的来源网段：
       - `set_real_ip_from <trusted_cidr>;`
       - `real_ip_header X-Forwarded-For;`
       - `real_ip_recursive on;`
     - 然后再转发 `X-Forwarded-For $remote_addr`（此时 remote_addr 已是“清洗后的真实客户端 IP”）。
   - 目标：即使应用侧配置误差，也能大幅降低“伪造 forwarded headers”的成功率。

## Architecture Decision ADR

### ADR-001: Client IP Trust Model（仅信任受控代理链）
**Context:** Redirect Edge 需要使用客户端 IP 进行 UV 指纹与风控；但 forwarded headers 可被公网客户端伪造。  
**Decision:** 仅当 `remoteAddr` 命中配置的 `trustedProxies` 时，才解析并采信 `Forwarded/X-Forwarded-For/X-Real-IP`；否则忽略并使用 `remoteAddr`。  
**Rationale:** 明确 trust boundary，可防止默认“信任任意 forwarded header”带来的安全漏洞；同时在有网关/代理时仍支持正确取 IP。  
**Alternatives:** 无条件取 `X-Forwarded-For` 首段 → Rejection reason: 易伪造，污染统计与日志，无法作为风控依据。  
**Impact:** 需要运维正确配置 trustedProxies；未配置时会退化为 remoteAddr（安全但可能丢失真实客户端 IP）。

## API Design

### [GET] /r/{code}
- **Behavior changes:** 正常跳转语义不变；新增 403/429 拒绝语义（用于 denylist/allowlist/限流触发）。
- **Response:** 302/301（正常）｜403（黑白名单）｜429（限流）

## Data Model

本变更不引入 MySQL 表结构变更；会新增 Redis 限流相关 key（带 TTL），示例：

- `rl:r:ip:{ip}:{epochMinute}`（固定窗口计数）
- `rl:r:ip_code:{ip}:{code}:{epochMinute}`（可选）

## Security and Performance

- **Security**
  - 默认不信任 forwarded headers（必须显式配置 trustedProxies）
  - 风控策略配置化（阈值/名单/UA 规则），支持灰度与快速回滚
  - 拒绝响应不泄露内部信息；日志保留 requestId 便于排障
- **Performance**
  - 风控逻辑在 Controller 前执行，尽量避免无效请求触发 DB 回源
  - Redis 限流使用 Lua 原子操作，降低 RTT 与竞态风险
  - bot 策略优先“降频”而非“强封”，避免误伤造成可用性问题

## Testing and Deployment

- **Testing**
  - 单元测试：IP 解析（trusted/untrusted、XFF 链、非法 IP）、CIDR 匹配
  - 单元/集成测试：限流阈值触发返回 429；allow/deny 返回 403；正常请求不受影响
  - 回归：UV 指纹使用“可信 IP + UA”稳定且可复现
- **Deployment**
  - 增加环境变量/配置项（trustedProxies、阈值、名单、UA 规则）
  - 网关侧按生产建议配置 real_ip 与 forwarded header 清洗

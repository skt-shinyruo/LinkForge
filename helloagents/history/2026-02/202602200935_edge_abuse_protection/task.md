# Task List: edge_abuse_protection

Directory: `helloagents/plan/202602200935_edge_abuse_protection/`

---

## 1. Redirect Edge（server/edge-app）

- [√] 1.1 实现“可信代理链”客户端 IP 解析器（仅信任 `trustedProxies`），并让 `/r/**` 统一使用该 IP 构造 `VisitInfo`，验证 why.md#scenario-spoofed-xff
  - Files (expected): `server/edge-app/src/main/java/com/linkforge/redirect/web/RedirectController.java`, `server/edge-app/src/main/java/com/linkforge/**`(new resolver)
- [√] 1.2 新增 `/r/**` 风控 Filter（OncePerRequestFilter + URL pattern），在进入 Controller 前挂载 `VisitInfo` 并输出可观测日志（reasonCode/requestId），验证 why.md#scenario-burst-request
  - Files (expected): `server/edge-app/src/main/java/com/linkforge/**`(new filter), `server/edge-app/src/main/java/com/linkforge/redirect/web/RedirectController.java`
- [√] 1.3 增加 IP denylist/allowlist（CIDR 支持，deny 优先），命中返回 403，验证 why.md#scenario-ip-allow-deny
  - Depends on task 1.2
- [√] 1.4 增加 Redis 限流（固定窗口 + Lua 原子 INCR/EXPIRE），至少按 IP 维度，命中返回 429，验证 why.md#scenario-burst-request
  - Depends on task 1.2
- [√] 1.5 增加 bot/crawler UA 识别策略（规则配置化；默认更严格阈值），验证 why.md#scenario-known-bot-ua
  - Depends on task 1.4

## 2. Shared 配置与公共能力（server/shared）

- [√] 2.1 扩展 `AppProperties`：新增 `app.edge.*`（trustedProxies、allow/deny、rateLimit、botPolicy 等），并补充启动期配置校验（安全默认值），验证 why.md#requirement-trusted-proxy-chain
  - Files (expected): `server/shared/src/main/java/com/linkforge/platform/config/AppProperties.java`, `server/shared/src/main/java/com/linkforge/platform/config/*`
- [√] 2.2 为 IP 解析与 CIDR 匹配补充单元测试（含 IPv4/IPv6、XFF 链、非法 IP、trusted/untrusted 分支），验证 why.md#scenario-spoofed-xff
  - Files (expected): `server/shared/src/test/java/**` 或 `server/edge-app/src/test/java/**`

## 3. Gateway / Nginx（web）

- [√] 3.1 更新 `web/nginx.conf`：对 `/r/` 覆盖/清洗 `X-Forwarded-For`（默认 compose 直接使用 `$remote_addr`），验证 why.md#scenario-overwrite-forwarded-headers
  - Files (expected): `web/nginx.conf`
- [√] 3.2 在文档中补充生产建议配置（real_ip + set_real_ip_from + recursive），并说明与应用侧 trustedProxies 的配合方式
  - Depends on task 3.1

## 4. Security Check

- [√] 4.1 执行安全检查（G9）：输入校验（IP/UA/headers）、敏感信息处理（日志）、拒绝响应信息最小化、配置默认安全、避免 EHRB 风险

## 5. Documentation Update

- [√] 5.1 更新模块文档：`helloagents/wiki/modules/redirect-edge.md` 补充“可信代理链 + 风控/限流”说明与配置示例
- [√] 5.2 更新变更记录：`helloagents/CHANGELOG.md` 记录本次安全加固

## 6. Testing

- [√] 6.1 增加/更新 Edge 集成测试（MockMvc）：403/429/正常跳转/可信 IP 解析覆盖，确保统计指纹不被伪造 IP 污染
  - Depends on task 1.1, 1.4

# Change Proposal: shortlink_system_mvp（短链系统 MVP）

## Requirement Background

LinkForge 需要实现一个面向公网的自助短链系统，支持多租户、登录与角色权限（管理员/普通用户），并提供管理后台与 OpenAPI。
系统需要支撑 Redirect 链路基线 QPS ~ 1000，日访问量 10w+，并面向长期亿级数据规模具备可演进能力。

## Product Analysis

### Target Users and Scenarios
- **用户群体：**
  - 需要生成短链并管理投放的运营/市场人员
  - 需要通过 API 批量生成短链的开发者/系统集成方
  - 需要查看效果数据的管理者
- **使用场景：**
  - 将长 URL 转换为短链便于传播（短信/社媒/二维码）
  - 对短链进行有效期、启用状态控制（活动结束自动失效）
  - 基于 PV/UV 的基础数据复盘与报表导出
- **核心痛点：**
  - 长 URL 难以传播、可读性差
  - 缺少统一管理、缺少统计汇总
  - 多团队/多客户需要隔离与权限控制

### Value Proposition and Success Metrics
- **价值主张：**
  - 低门槛自助创建短链，提供可控管理与基础统计能力
  - Redirect 链路低延迟，支持缓存与异步统计以适配增长
- **成功指标（MVP）：**
  - Redirect 平均延迟可控（缓存命中场景为主）
  - 创建短链成功率、短码唯一性与可用性
  - 管理后台可用：短链 CRUD、禁用/到期生效、统计报表可用
  - 多租户隔离与 RBAC 生效（越权不可访问）

### Humanistic Care
- 隐私与合规：统计优先做“聚合指标”，避免长期保存可识别个人的明细数据；日志与数据字段应可配置脱敏策略。
- 可访问性：管理后台基础可用性（键盘操作/对比度）在 UI 组件选型时纳入考虑。

## Change Content
1. 建立后端服务（Spring Boot）与前端管理后台（Vue3）工程骨架
2. IAM：多租户、注册/登录、JWT、角色权限（管理员/普通用户）、OpenAPI Key
3. ShortLink：短链创建/列表/编辑/禁用、有效期、备注/标签、批量导入导出
4. Redirect：短码解析缓存、回源、默认 302 跳转、失效处理
5. Analytics：PV/UV 采集、Redis 聚合、定时落库、统计报表 API
6. Docker 与 CI：可本地一键启动（MySQL/Redis/服务/前端），并具备持续集成流水线

## Impact Scope
- **Modules:**
  - server: iam / shortlink / redirect / analytics / platform
  - web: admin-ui
  - deploy: docker-compose / env
- **Files（预估）：**
  - 后端：核心配置、控制器、服务、数据层、迁移脚本、测试等
  - 前端：页面、路由、状态管理、API 客户端
  - 部署：Dockerfile、compose、CI workflow
- **APIs:** 见 `.helloagents/wiki/api.md`（将以此为基准实现）
- **Data:** 见 `.helloagents/wiki/data.md`（将以此为基准落库）

## Core Scenarios

### Requirement: iam-tenant-auth（多租户 + 注册登录 + 权限）
**Module:** iam
提供对外自助创建能力，并确保租户隔离与权限控制可验证。

#### Scenario: register-tenant（注册创建租户）
用户注册时自动创建租户，首个用户成为租户管理员。
- 返回可用于管理后台调用的 JWT

#### Scenario: rbac-admin-user（管理员/普通用户权限）
- 管理员可管理用户与 API Key，可对短链进行批量导入导出
- 普通用户可创建/管理自身短链（具体权限边界在实现中落地）

---

### Requirement: shortlink-crud（短链管理）
**Module:** shortlink
短链可创建、查询、编辑、禁用，支持有效期与标签/备注。

#### Scenario: create-link（创建短链）
- 支持自动生成短码
- 支持设置 expiresAt（可选）
- 返回短 URL（domain + /r/{code}）

#### Scenario: edit-disable-link（编辑/禁用短链）
- 禁用后 Redirect 访问不可用
- 到期后自动不可用

---

### Requirement: shortlink-bulk（批量导入导出）
**Module:** shortlink

#### Scenario: import-export（导入/导出）
- 管理员可通过 CSV 批量导入与导出
- 返回可读的失败原因（格式/字段/冲突）

---

### Requirement: redirect-core（跳转解析）
**Module:** redirect

#### Scenario: redirect-available（可用短链跳转）
- 默认 302 跳转（建议）
- 缓存命中优先

#### Scenario: redirect-unavailable（不可用短链）
- 过期/禁用/不存在：返回统一错误页或 404/410（可配置）

---

### Requirement: analytics-report（统计报表）
**Module:** analytics

#### Scenario: pv-uv-daily（按天 PV/UV）
- 管理后台可查询某短链的按天趋势
- 可查询租户概览（近 7/30 天）

---

### Requirement: openapi（开放 API）
**Module:** iam + shortlink

#### Scenario: api-create-link（API 创建短链）
- 使用 API Key 创建短链并获取短 URL

## Risk Assessment
- **Risk:** 公网开放自助创建且不做风控，存在被滥用（垃圾链接/刷量）与资源消耗风险
  - **Mitigation:** 预留基础防护“开关位”（注册/登录限流、验证码、封禁）与审计日志，不在 MVP 强制启用
- **Risk:** 统计涉及隐私字段（IP/UA）
  - **Mitigation:** 优先聚合统计；IP/UA 只做哈希或不落库；提供数据保留策略

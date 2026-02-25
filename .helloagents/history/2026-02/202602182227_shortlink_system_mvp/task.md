# Task List: shortlink_system_mvp（短链系统 MVP）

Directory: `.helloagents/history/2026-02/202602182227_shortlink_system_mvp/`

---

## 1. Project Bootstrap（platform）
- [√] 1.1 初始化后端 Spring Boot（Java21 + Maven），确定目录结构（建议 `server/`），落地基础配置：`server/pom.xml`、`server/src/main/resources/application.yml`
- [√] 1.2 引入 MySQL/Redis/Flyway/Security 依赖，提供本地开发 profile 与配置示例
- [√] 1.3 定义统一错误码与响应包裹（参考 `.helloagents/project.md`），落地基础中间件（requestId、日志脱敏）

## 2. Database & Migration（data）
- [√] 2.1 建立 Flyway 迁移：`server/src/main/resources/db/migration/V1__init.sql`（tenants/users/user_roles/short_links/tags/link_tags/api_keys/link_stats_daily）
- [√] 2.2 为统计与短链关键查询补齐索引策略，并在本地启动时自动建表

## 3. IAM（iam）
- [√] 3.1 实现注册创建租户：`POST /api/v1/auth/register`（对应 why.md#requirement-iam-tenant-auth）
- [√] 3.2 实现登录与 JWT：`POST /api/v1/auth/login`、`GET /api/v1/me`（对应 why.md#requirement-iam-tenant-auth）
- [√] 3.3 实现 RBAC（tenant_admin/user）与接口权限校验（对应 why.md#requirement-iam-tenant-auth）
- [√] 3.4 实现 OpenAPI Key 管理与校验中间件（对应 why.md#requirement-openapi）

## 4. ShortLink（shortlink）
- [√] 4.1 实现短码生成（Snowflake + Base62）与短链创建：`POST /api/v1/links`（对应 why.md#requirement-shortlink-crud）
- [√] 4.2 实现短链分页列表/详情/编辑/禁用：`GET/PUT /api/v1/links*`（对应 why.md#requirement-shortlink-crud）
- [√] 4.3 实现标签管理与关联（tags/link_tags），并在列表中支持过滤（对应 why.md#requirement-shortlink-crud）
- [√] 4.4 实现 CSV 导入导出：`POST /api/v1/links/import`、`GET /api/v1/links/export`（对应 why.md#requirement-shortlink-bulk）
- [√] 4.5 实现 OpenAPI 创建/查询短链：`/api/v1/open/links*`（对应 why.md#requirement-openapi）

## 5. Redirect（redirect）
- [√] 5.1 实现 `/r/{code}` 跳转（默认 302，可配置 301），处理不存在/禁用/过期（对应 why.md#requirement-redirect-core）
- [√] 5.2 实现 Redis Cache-aside（解析缓存、TTL、失效策略），并在短链更新/禁用时失效缓存

## 6. Analytics（analytics）
- [√] 6.1 Redirect 侧写入统计：PV 计数 + UV 近似去重（Redis），并保证写入足够轻量（对应 why.md#requirement-analytics-report）
- [√] 6.2 定时任务聚合落库到 `link_stats_daily`（幂等/重试）
- [√] 6.3 实现统计 API：`GET /api/v1/stats/links/{id}/daily`、`GET /api/v1/stats/overview`（对应 why.md#requirement-analytics-report）

## 7. Admin UI（admin-ui）
- [√] 7.1 初始化 Vue3 + Vite + TS 工程（建议 `web/`），完成登录页与路由守卫
- [√] 7.2 完成短链列表/创建/禁用交互页面（MVP）
  > Note: 编辑/标签管理 UI 暂未实现（后端 API 已具备基础能力）
- [√] 7.3 完成统计看板（MVP：按链路“今日按天”表格展示）
  > Note: 趋势图与更多报表（7/30 天、Top 链接）可在后续迭代增强
- [√] 7.4 完成导入导出交互（管理员：上传 CSV、下载 CSV）

## 8. Docker & CI（deploy）
- [√] 8.1 编写 Dockerfile：`server/Dockerfile`、`web/Dockerfile`
- [√] 8.2 编写 `deploy/docker-compose.yml`：MySQL + Redis + server + web，一键启动
- [√] 8.3 编写 CI（如 `.github/workflows/ci.yml`）：后端测试 + 前端构建（镜像构建可选）

## 9. Security Check
- [√] 9.1 执行安全检查（G9）：输入校验、敏感信息处理（password/token/api key）、权限控制、SQL 注入与越权检查

## 10. Documentation Update（SSOT）
- [√] 10.1 根据最终实现同步更新 `.helloagents/wiki/*`（API、数据模型、模块说明）
- [√] 10.2 更新 `.helloagents/CHANGELOG.md`，并在执行完成后迁移方案包到 `.helloagents/history/`

## 11. Testing
- [√] 11.1 编写集成测试（Testcontainers）：注册/登录、创建短链、跳转、统计落库
- [-] 11.2 简单压测/验证 Redirect 基线 QPS 1000（记录结果与瓶颈）
  > Note: 可在本地 docker-compose 启动后，使用 wrk/k6 对 `/r/{code}` 压测并记录结果

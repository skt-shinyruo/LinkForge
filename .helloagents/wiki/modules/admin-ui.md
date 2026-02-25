# admin-ui

## Purpose
提供 Vue3 管理后台，用于登录与短链全生命周期管理、统计可视化。

## Module Overview
- **Responsibility:** 登录/注册、短链列表与编辑、批量导入导出、统计看板
- **Status:** 🚧In Development
- **Last Updated:** 2026-02-25

## Specifications

### Requirement: ui-auth（登录与会话）
**Module:** admin-ui
提供登录页与会话保持能力。

#### Scenario: ui-login（登录）
- 输入邮箱/密码登录
- 会话模式：
  - Bearer 模式：保存 JWT（默认建议使用 `sessionStorage`，避免长期驻留）
  - Cookie 模式（可选）：不持久化 token，通过 HttpOnly Cookie 维持会话（前端请求携带 `credentials: include`）

#### Scenario: ui-401-unified（401 统一处理）
- 任意 API 返回 401：清理本地会话并跳转登录页
- 目的：避免局部页面“悄悄失败”导致状态漂移

---

### Requirement: ui-links（短链管理）
**Module:** admin-ui
提供短链列表、创建、编辑、禁用、标签管理、导入导出入口。

#### Scenario: ui-link-crud（CRUD）
- 列表分页 + 筛选
- 创建/编辑表单（自定义短码、有效期、备注、标签）
- 禁用/启用操作
- 生命周期治理（管理员）：
  - 归档/恢复（可恢复下线）
  - 删除（要求先归档，避免误删）
  - 活动/归档列表切换（默认仅展示活动短链）
- 跳转策略配置（可选）：
  - 跳转状态码（301/302/继承全局）
  - 预览确认页开关
  - Query 透传模式（OFF/ALLOWLIST/ALL/继承全局）与 allowlist（如 `utm_*`）
  - 不可用落地页（禁用/过期）

#### Scenario: ui-tags-management（标签管理）
- 独立标签管理入口：标签列表 + 创建
- 为后续“按标签筛选/批量打标”提供基础能力

---

### Requirement: ui-stats（统计看板）
**Module:** admin-ui
提供 PV/UV 趋势图与基础概览。

#### Scenario: ui-daily-chart（趋势图）
- 展示近 7/30 天曲线
- 图表拆分为多卡片（租户趋势/单短链趋势），默认按需展示：点击“显示图表”后才异步加载图表组件（ECharts 模块化引入），降低非统计路径的加载成本

#### Scenario: ui-top-links（Top 链接报表）
- 展示指定时间范围内 Top 链接（支持按 PV/UV 切换排序）

## API Interfaces

前端通过 `.helloagents/wiki/api.md` 中的 `/api/v1/**` 调用后端。

## Data Models

前端主要消费后端 API 的 DTO，不直接访问数据库。

## Dependencies
- backend-api（/api/v1）

## Change History
- [202602182227_shortlink_system_mvp](../../history/2026-02/202602182227_shortlink_system_mvp/) - MVP：登录、短链管理（基础）、统计（基础）
- [202602191324_ui_tags_charts_top_bench](../../history/2026-02/202602191324_ui_tags_charts_top_bench/) - 标签管理 UI、7/30 天趋势图、Top 链接报表、Redirect 压测
- [202602191429_top_uv_sort_chart_lazyload](../../history/2026-02/202602191429_top_uv_sort_chart_lazyload/) - Top 报表支持 UV 排序切换 + 统计图表按需加载优化
- [202602192233_ui_chart_cards_lazyload](../../history/2026-02/202602192233_ui_chart_cards_lazyload/) - 统计趋势图拆分多卡片 + 更细粒度按需展示（懒加载）
- [202602191426_edge_api_split_refactor](../../history/2026-02/202602191426_edge_api_split_refactor/) - 401 统一处理、token 存储策略默认收敛到 session、支持可选 Cookie 会话
- [202602201026_redirect_experience_control](../../history/2026-02/202602201026_redirect_experience_control/) - 短链管理页补齐跳转策略配置（状态码/预览页/query 透传/不可用落地页）
- [202602201407_lifecycle_governance_closure](../../history/2026-02/202602201407_lifecycle_governance_closure/) - Links 管理页补齐生命周期治理能力（归档/恢复/删除）与创建/编辑字段对齐（customCode/expiresAt/tags）

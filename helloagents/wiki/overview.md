# LinkForge（短链系统）

> 本文件包含项目级核心信息；详细模块文档位于 `modules/`。

---

## 1. 项目概览

### 目标与背景
LinkForge 旨在提供一个面向公网的自助短链平台，支持多租户、登录与权限控制，并提供管理后台与 OpenAPI，满足短链生成、管理、跳转与统计报表需求。

### 范围边界
- In scope：
  - 自助注册/登录、多租户隔离、角色权限（管理员/普通用户）
  - 短链创建/编辑/禁用、有效期、备注/标签
  - 批量导入导出（CSV）
  - OpenAPI（API Key）
  - 跳转服务与基础缓存
  - 统计报表（PV/UV 等聚合指标）
- Out of scope（当前明确不做）：
  - 复杂风控体系（反作弊、内容审核、黑白名单联动等）
  - 运营投放系统、A/B 实验等高级能力

### 关键指标（当前假设）
- Redirect 链路：基线 QPS ~ 1000
- 日访问量：10w+
- 长期数据规模：亿级（以统计事件或访问量维度）

---

## 2. 模块索引

| 模块 | 责任 | 状态 | 文档 |
|------|------|------|------|
| api-service | 管理后台/API：`/api/v1/**`（IAM、ShortLink、OpenAPI、统计查询/落库） | 🚧In Development | [modules/api-service.md](modules/api-service.md) |
| redirect-edge | 跳转边缘服务：`/r/**`（解析、缓存治理、轻量统计写入） | 🚧In Development | [modules/redirect-edge.md](modules/redirect-edge.md) |
| iam | 多租户、用户、登录与权限（RBAC） | 🚧In Development | [modules/iam.md](modules/iam.md) |
| shortlink | 短链创建/编辑/禁用、标签、有效期、批量导入导出 | 🚧In Development | [modules/shortlink.md](modules/shortlink.md) |
| analytics | PV/UV 统计采集、聚合落库、报表查询 API | 🚧In Development | [modules/analytics.md](modules/analytics.md) |
| admin-ui | Vue3 管理后台（登录、短链管理、统计看板） | 🚧In Development | [modules/admin-ui.md](modules/admin-ui.md) |

---

## 3. 快速链接

- [技术约定](../project.md)
- [架构设计](arch.md)
- [API 手册](api.md)
- [数据模型](data.md)
- [变更历史](../history/index.md)

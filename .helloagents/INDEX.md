# LinkForge 知识库

> 本文件是知识库的入口点（SSOT 入口）。  
> 说明：历史文档与长篇模块说明目前主要位于 `wiki/` 与 `wiki/modules/`；本次为兼容 HelloAGENTS 标准结构补齐 `INDEX.md/context.md/modules/*` 入口文件，不迁移既有内容。

## 快速导航

| 需要了解 | 读取文件 |
|---------|---------|
| 项目概况、技术栈、开发约定 | [context.md](context.md) |
| 工程与接口统一约定（更细） | [project.md](project.md) |
| 模块索引（标准入口） | [modules/_index.md](modules/_index.md) |
| 模块详细说明（现有长文档） | [wiki/overview.md](wiki/overview.md) |
| 项目变更历史 | [CHANGELOG.md](CHANGELOG.md) |
| 历史方案索引 | [archive/_index.md](archive/_index.md) |
| 历史方案清单（含 why/how/task） | [history/index.md](history/index.md) |
| 当前待执行的方案 | [plan/](plan/) |
| 历史会话记录 | [sessions/](sessions/) |

## 模块关键词索引

> AI 读取此表即可判断哪些模块与当前需求相关，按需深读。

| 模块 | 关键词 | 摘要 |
|------|--------|------|
| api | /api/v1, Spring Boot, tenant guard, ShedLock, OpenAPI | 管理端 API 服务：IAM/ShortLink/OpenAPI/统计查询与落库 |
| edge | /r, redirect, Redis, 风控, trusted proxies | 跳转边缘服务：短码解析跳转、轻量统计写入、缓存与防滥用 |
| iam | JWT, Cookie, RBAC, tenants/users, API Key | 认证鉴权与多租户/角色权限、API Key 管理 |
| shortlink | links, tags, outbox, AFTER_COMMIT, redirect policy | 短链管理域：创建/编辑/归档/恢复/删除 + 缓存一致性治理 |
| analytics | PV/UV, dimensions, events, flush, active-set | 统计采集/聚合落库/报表查询接口（Redis → MySQL） |
| admin-ui | Vue3, Vite, ECharts, dashboard, lazy load | 管理后台前端：登录、短链管理、统计看板 |

## 知识库状态

```yaml
kb_version: 2.2.12
最后更新: 2026-02-26 10:08
模块数量: 6
待执行方案: 2
```

## 读取指引

```yaml
启动任务:
  1. 读取本文件获取导航
  2. 读取 context.md 获取项目上下文
  3. 检查 plan/ 是否有进行中方案包

任务相关:
  - 涉及特定模块: 读取 modules/{模块名}.md（标准入口）
  - 需要更长的模块说明/历史背景: 跳转到 wiki/ 与 history/
  - 需要历史决策: 搜索 CHANGELOG.md → 读取对应 archive/{YYYY-MM}/{方案包}/proposal.md
  - 继续之前任务: 读取 plan/{方案包}/*
```


# 模块索引

> 通过此文件快速定位模块文档（标准入口）。  
> 说明：`wiki/modules/` 下存在更长的模块说明；`modules/` 目录用于对齐 HelloAGENTS 的标准读取路径，并在每个模块页中提供“详细文档”跳转。

## 模块清单

| 模块 | 职责 | 状态 | 文档 |
|------|------|------|------|
| api | 管理后台/API：`/api/v1/**`（IAM、ShortLink、OpenAPI、统计查询/落库） | 🚧 | [api.md](./api.md) |
| edge | 跳转边缘服务：`/r/**`（解析、缓存治理、轻量统计写入） | 🚧 | [edge.md](./edge.md) |
| iam | 多租户、用户、登录与权限（RBAC） | 🚧 | [iam.md](./iam.md) |
| shortlink | 短链创建/编辑/禁用、标签、有效期、批量导入导出 | 🚧 | [shortlink.md](./shortlink.md) |
| analytics | PV/UV 统计采集、聚合落库、报表查询 API | 🚧 | [analytics.md](./analytics.md) |
| admin-ui | Vue3 管理后台（登录、短链管理、统计看板） | 🚧 | [admin-ui.md](./admin-ui.md) |

## 模块依赖关系（高层视角）

```
admin-ui → api

api → iam
api → shortlink
api → analytics

edge → shortlink（解析缓存/回源只读）
edge → analytics（轻量写入 Redis 统计结构）
```

## 状态说明
- ✅ 稳定
- 🚧 开发中
- 📝 规划中


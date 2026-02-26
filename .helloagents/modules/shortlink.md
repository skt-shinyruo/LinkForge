# shortlink

> 标准入口文件：用于对齐 HelloAGENTS 的 `modules/` 读取路径。  
> 详细说明见：[wiki/modules/shortlink.md](../wiki/modules/shortlink.md)

## 职责

- 短链创建/编辑/禁用、有效期、备注、标签、批量导入导出
- 跳转策略配置：301/302、预览页、Query 透传策略（OFF/ALLOWLIST/ALL）等
- 生命周期治理：归档/恢复/删除
- 缓存一致性治理：DB ↔ Redis（事务提交后副作用 + outbox 兜底）

## 行为规范

### 缓存一致性（DB ↔ Redis）
- 避免在事务提交前写/删缓存（防止回滚污染与旧值回填）
- 引入 outbox 兜底缓存刷新/驱逐的最终一致性

### 跳转策略
- 默认行为可通过全局配置与单链接配置组合控制
- Query 透传策略需可控（避免把敏感信息带入目标站点）

## 依赖关系

```yaml
依赖:
  - api
  - edge
  - mysql
  - redis
被依赖:
  - analytics（按链接维度统计/报表）
  - admin-ui
```


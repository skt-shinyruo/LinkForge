# admin-ui

> 标准入口文件：用于对齐 HelloAGENTS 的 `modules/` 读取路径。  
> 详细说明见：[wiki/modules/admin-ui.md](../wiki/modules/admin-ui.md)

## 职责

- Vue 3 管理后台：登录、短链管理、统计看板、标签管理等
- 与 API 服务对接：`/api/v1/**`
- 关注前端性能：图表与报表按需加载，避免进入页面即加载重资源

## 行为规范

### 会话与安全
- Bearer 模式：token 默认收敛到 `sessionStorage`
- Cookie 模式（可选）：前端不持久化 token，请求携带 `credentials: include`，写接口需携带 CSRF token

### 性能
- 图表组件按需挂载/异步加载（ECharts 模块化引入）
- 趋势图拆分多卡片并支持按需展示（点击“显示图表”后再加载 chunk）

## 依赖关系

```yaml
依赖:
  - api
  - Vue 3 / Vite / TypeScript
被依赖:
  - 最终用户（管理后台操作者）
```


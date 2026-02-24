# Technical Design: 管理后台标签管理 + 统计趋势图/Top 报表 + Redirect 压测

## Technical Solution

### Core Technologies
- Frontend: Vue 3 + TypeScript + Vite
- Charts: ECharts（单库引入，用于折线图与 Top 可视化基础能力）
- Backend: Spring Boot（现有）+ JdbcTemplate（现有）
- Benchmark: k6（优先用 Docker 镜像运行，降低本机依赖）

### Implementation Key Points
1. **统计趋势图（7/30 天）**
   - 复用既有 `/api/v1/stats/overview` 返回的按天数据（PV/UV）
   - 前端将 DailyStat 转换为 ECharts series（PV/UV 两条折线）
   - 提供快捷按钮：近 7 天 / 近 30 天（UTC day）
2. **Top 链接报表**
   - 后端新增 `/api/v1/stats/top-links`：
     - 输入：from/to/limit（limit 默认 10，上限 100）
     - 输出：按 PV 降序的 Top 列表（linkId、code、originalUrl、pv、uv、shortUrl 可选）
   - 数据来源：`link_stats_daily` 按范围聚合（SUM pv/uv），JOIN `short_links` 获取 code/original_url
   - 鉴权：沿用 StatsController 规则（禁止 OPENAPI 访问）
3. **标签管理 UI**
   - 前端新增 `/tags` 页面：
     - 拉取 `/api/v1/tags` 列表
     - 通过 `/api/v1/tags` 创建新标签
   - 顶部导航增加“标签/统计/退出”
4. **Redirect 压测**
   - 新增 `bench/redirect/`：
     - k6 脚本支持通过环境变量配置 target、code、vus、duration、rate（可选）
     - README 说明如何准备短码、如何启动服务、如何运行脚本
   - 运行一次基线压测（本地 docker-compose），将结果记录到 `bench/redirect/results.md`（含环境与关键指标）

## API Design

### [GET] /api/v1/stats/top-links
- **Auth:** JWT（`Authorization: Bearer <token>`），并 `@PreAuthorize("!hasRole('OPENAPI')")`
- **Query:**
  - `from` (yyyy-MM-dd) required
  - `to` (yyyy-MM-dd) required
  - `limit` (int) optional, default=10, max=100
- **Response (ApiResponse):**
  - `data`: `TopLinkStat[]`
    - `linkId`: number
    - `code`: string
    - `originalUrl`: string
    - `pv`: number
    - `uv`: number

## Data Model
不新增业务表。可选优化：
- 为 Top 聚合查询补充索引（若压测/评估发现需要）：
  - `link_stats_daily(tenant_id, day, link_id)`（更好地支持按租户+日期范围扫描并分组）

## Security and Performance
- **Security:**
  - Top 链接报表与趋势图接口均禁止 OPENAPI 访问（保持“统计不开放”约束）
  - 所有统计查询必须按 `tenant_id` 过滤，JOIN 同时限定 `short_links.tenant_id = ?`
  - 标签创建复用后端校验：长度/空值/租户唯一约束
- **Performance:**
  - Top 查询增加 limit 上限，避免一次请求拉取过多数据
  - 压测脚本默认目标 localhost，避免误伤；建议在本地/预发环境执行

## Testing and Deployment
- **Testing:**
  - 后端：在 `LinkForgeIntegrationTest` 增加 Top 链接统计接口验证（构造访问→flush→查询 Top）
  - 前端：`npm run build` 确保类型与构建通过；关键页面手测（统计/标签）
  - Benchmark：用 Docker 运行 k6，记录一次基线结果
- **Deployment:**
  - 前端依赖变更后需更新 Docker 构建缓存（重新 build web 镜像）
  - 如新增 Flyway V2（索引优化），compose 启动会自动迁移


# Technical Design: Top 报表支持按 UV 排序 + 图表更细粒度按需加载

## Technical Solution

### Core Technologies
- Backend: Spring Boot + JdbcTemplate（延续现有实现）
- Frontend: Vue3 + Vite
- Chart: ECharts（模块化引入 + 异步组件按需加载）

### Implementation Key Points
1. **Top 报表 API：sortBy 参数**
   - Endpoint: `GET /api/v1/stats/top-links`
   - 新增 Query: `sortBy`（可选，`pv|uv`，默认 `pv`）
   - 参数校验：
     - `from <= to`
     - `limit` 默认 10、最大 100
     - `sortBy` 仅允许 `pv/uv`（忽略大小写与首尾空格）
   - SQL 实现：白名单选择两段固定 SQL（避免动态拼接注入风险）
2. **管理后台 Top 排序切换**
   - StatsView 增加 sort 状态（`pv/uv`），切换后重新请求 Top 接口
   - UI 显示“Top 链接（按 PV/UV）”
3. **图表更细粒度按需加载**
   - StatsView 使用 `defineAsyncComponent` 异步加载 `LineChart.vue`
   - `LineChart.vue` 使用 ECharts 模块化引入（core + line chart + 必要组件 + renderer）
   - 仅在 `overviewStats` 有数据时才渲染图表组件（保持现有逻辑）

## API Design

### [GET] /api/v1/stats/top-links
- **Auth:** JWT（并禁止 OPENAPI：`@PreAuthorize("!hasRole('OPENAPI')")`）
- **Query:**
  - `from` (yyyy-MM-dd) required
  - `to` (yyyy-MM-dd) required
  - `limit` (int) optional, default=10, max=100
  - `sortBy` (string) optional, `pv|uv`, default=pv

## Security and Performance
- **Security:** `sortBy` 白名单校验；Top 查询严格按 tenant_id 过滤；JOIN 同时限定短链 tenant_id
- **Performance:** 复用已存在索引能力；如查询范围较大可通过 `limit` 控制返回；前端将 ECharts 拆到异步 chunk，降低主 bundle

## Testing and Deployment
- **Backend tests:** 扩展 `LinkForgeIntegrationTest`：
  - 构造 PV 更高但 UV 更低的 linkA
  - 构造 PV 更低但 UV 更高的 linkB（通过不同 X-Forwarded-For 模拟不同访客）
  - 断言 `sortBy=pv` 返回 A 第一；`sortBy=uv` 返回 B 第一
- **Frontend build:** `npm run build` 观察 StatsView chunk 体积变化（按需加载后应下降）


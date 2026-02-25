# 变更历史索引

本文件记录所有已完成变更的索引，便于追踪与审计。

---

## Index

| 时间戳 | 功能标识 | 类型 | 状态 | 方案包路径 |
|-------|----------|------|------|-----------|
| 202602182227 | shortlink_system_mvp | Feature | ✅Completed | [Link](2026-02/202602182227_shortlink_system_mvp/) |
| 202602191324 | ui_tags_charts_top_bench | Feature | ✅Completed | [Link](2026-02/202602191324_ui_tags_charts_top_bench/) |
| 202602191426 | edge_api_split_refactor | Refactor | ✅Completed | [Link](2026-02/202602191426_edge_api_split_refactor/) |
| 202602191429 | top_uv_sort_chart_lazyload | Enhancement | ✅Completed | [Link](2026-02/202602191429_top_uv_sort_chart_lazyload/) |
| 202602192233 | ui_chart_cards_lazyload | Enhancement | ✅Completed | [Link](2026-02/202602192233_ui_chart_cards_lazyload/) |
| 202602200935 | edge_abuse_protection | Enhancement | ✅Completed | [Link](2026-02/202602200935_edge_abuse_protection/) |
| 202602201026 | redirect_experience_control | Enhancement | ✅Completed | [Link](2026-02/202602201026_redirect_experience_control/) |
| 202602201217 | analytics_visit_events_dims | Enhancement | ✅Completed | [Link](2026-02/202602201217_analytics_visit_events_dims/) |
| 202602201407 | lifecycle_governance_closure | Enhancement | ✅Completed | [Link](2026-02/202602201407_lifecycle_governance_closure/) |
| 202602241741 | architecture_hardening | Refactor | ✅Completed | [Link](2026-02/202602241741_architecture_hardening/) |
| 202602250305 | m0_id_edge_cache_hardening | Enhancement | ✅Completed | [Link](2026-02/202602250305_m0_id_edge_cache_hardening/) |

---

## 按月份归档

### 2026-02

- [202602182227_shortlink_system_mvp](2026-02/202602182227_shortlink_system_mvp/) - 短链系统 MVP：后端/前端/部署/测试
- [202602191324_ui_tags_charts_top_bench](2026-02/202602191324_ui_tags_charts_top_bench/) - 标签管理 UI、统计趋势图/Top 报表、Redirect 压测
- [202602191426_edge_api_split_refactor](2026-02/202602191426_edge_api_split_refactor/) - 后端拆分 API/Edge、shared SSOT、统计 active-set flush、多租户护栏与统一错误响应
- [202602191429_top_uv_sort_chart_lazyload](2026-02/202602191429_top_uv_sort_chart_lazyload/) - Top 报表支持 UV 排序 + 图表按需加载优化
- [202602192233_ui_chart_cards_lazyload](2026-02/202602192233_ui_chart_cards_lazyload/) - 统计趋势图拆分多卡片 + 更细粒度按需展示（懒加载）
- [202602200935_edge_abuse_protection](2026-02/202602200935_edge_abuse_protection/) - Edge 防滥用/可信代理链/风控加固（安全取 IP、限流、黑白名单、bot 降频）
- [202602201026_redirect_experience_control](2026-02/202602201026_redirect_experience_control/) - 跳转体验与跳转行为可控性增强（404/410 HTML、预览页、按链接 301/302、Query 透传策略）
- [202602201217_analytics_visit_events_dims](2026-02/202602201217_analytics_visit_events_dims/) - 统计增强：维度聚合 + 访问明细事件（短期留存）+ 新增维度/明细查询 API
- [202602201407_lifecycle_governance_closure](2026-02/202602201407_lifecycle_governance_closure/) - 生命周期治理闭环（短链归档/恢复/删除、用户启用/禁用/重置密码、API Key 启用/禁用/轮换、Links UI 对齐）
- [202602241741_architecture_hardening](2026-02/202602241741_architecture_hardening/) - 架构加固：IAM 唯一约束、Edge 最小权限、Cookie+CSRF、统计 flush 优化、契约显式化、API Key 节流、配置校验去重
- [202602250305_m0_id_edge_cache_hardening](2026-02/202602250305_m0_id_edge_cache_hardening/) - M0 护栏：prod/strict 禁止默认 Snowflake 1/1；Edge 增加短码快速拒绝 + NOT_FOUND 负缓存

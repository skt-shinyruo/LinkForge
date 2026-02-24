# Task List: 统计图表拆分多卡片 + 更细粒度按需展示（懒加载）

Directory: `helloagents/history/2026-02/202602192233_ui_chart_cards_lazyload/`

---

## 1. admin-ui（多卡片 + 按需展示）
- [√] 1.1 调整统计页布局：将趋势图拆分为“租户趋势卡片”与“单短链趋势卡片”，并为每个卡片提供显示/收起开关，更新 `web/src/views/StatsView.vue`
- [√] 1.2 确保图表组件仅在用户点击“显示图表”后才挂载（从而触发异步 chunk 加载），避免进入统计页即加载 ECharts chunk，验证构建产物仍保持 `LineChart` 独立 chunk

## 2. Documentation Update（SSOT）
- [√] 2.1 更新 admin-ui 模块文档：补充“图表按需展示/多卡片布局”说明，更新 `helloagents/wiki/modules/admin-ui.md`
- [√] 2.2 更新变更日志（Unreleased）：记录统计看板图表更细粒度按需展示优化，更新 `helloagents/CHANGELOG.md`

## 3. Testing
- [√] 3.1 前端：在 `web/` 执行 `npm run build`，确认构建成功与 chunk 拆分仍生效

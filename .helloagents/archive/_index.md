# 方案归档索引

> 通过此文件快速查找已归档方案包。
> 完整的 why/how/task 等交付物见 `.helloagents/history/`。

## 快速索引（当前年份）

| 时间戳 | 名称 | 类型 | 涉及模块 | 决策 | 结果 |
|--------|------|------|---------|------|------|
| 202602261140 | fix-mermaid-syntax | - | - | - | ✅完成 |
| 202602252307 | rename-server-modules-minimal | - | - | - | ✅完成 |
| 202602252042 | shortlink-core-gap-analysis | - | - | - | ✅完成 |
| 202602242331 | package-ownership-refactor | - | - | package-ownership-refactor#D001 | ✅完成 |
| 202602251453 | shortlink-cache-after-commit | - | - | shortlink-cache-after-commit#D001 | ✅完成 |
| 202602251605 | shortlink-cache-outbox | - | - | shortlink-cache-outbox#D001 | ✅完成 |

## 按月归档

### 2026-02
- [202602242331_package-ownership-refactor](./2026-02/202602242331_package-ownership-refactor/) - 包归属重构：消除 split package + CI 检测（package-ownership-refactor#D001）
- [202602251453_shortlink-cache-after-commit](./2026-02/202602251453_shortlink-cache-after-commit/) - 缓存一致性治理：AFTER_COMMIT 再写/驱逐短链缓存（shortlink-cache-after-commit#D001）
- [202602251605_shortlink-cache-outbox](./2026-02/202602251605_shortlink-cache-outbox/) - 缓存最终一致兜底：持久化 outbox + job 补偿刷新/驱逐（shortlink-cache-outbox#D001）

## 结果状态说明
- ✅ 完成
- ⚠️ 部分完成
- ❌ 失败/中止
- ⏸ 未执行
- 🔄 已回滚
- 📄 概述

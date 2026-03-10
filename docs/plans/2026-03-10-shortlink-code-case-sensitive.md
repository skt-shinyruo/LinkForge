---
title: Shortlink Code Case-Sensitive (Strict)
date: 2026-03-10
status: implemented
---

# Shortlink 短码严格区分大小写

## 决策

- 短码 `code/slug` **严格区分大小写**：`Abcdef` ≠ `abcdef`
- 跳转解析同样严格：访问 `/r/abcdef` 不会命中 `/r/Abcdef`

## 背景与问题

当前系统的自动短码基于 `SnowflakeIdGenerator` + `Base62.encode(id)`，Base62 字母表包含 `A-Z` 与 `a-z`，天然会产生混合大小写短码；自定义短码也允许 `[0-9A-Za-z]`。

但 MySQL 常见默认 collation（例如 `utf8mb4_unicode_ci`）是大小写不敏感的，会导致：

- `short_links.code` 的唯一约束把仅大小写不同的 code 当成重复
- `link_cache_outbox.code` 作为主键 + `ON DUPLICATE KEY UPDATE` 会把两个不同 code 的刷新动作合并成一条 outbox 记录（破坏最终一致性语义）

因此需要在 DB 层把 `code` 的比较语义改为大小写敏感。

## 方案

### DB 层（真正确权）

- 将以下列改为 `ascii_bin`（ASCII + binary collation）以获得确定的大小写敏感比较/唯一性：
  - `short_links.code`
  - `link_cache_outbox.code`

对应 Flyway 迁移：已折叠进初始迁移 `server/app/src/main/resources/db/migration/V1__init.sql`（squash 到最新 schema）

### 应用层（并发/竞态处理）

自定义短码创建存在典型 “check-then-insert” 并发竞态：即使先 `ensureCodeAvailable()`，并发下仍可能撞唯一索引。

策略：

- 保存时使用 `saveAndFlush` 让唯一约束冲突在方法内抛出
- 捕获 `DataIntegrityViolationException`，若为自定义短码则转换为业务错误 `CODE_ALREADY_EXISTS`（HTTP 409）

## 测试

新增集成测试保证行为锁定：

- `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCodeCaseSensitivityIntegrationTest.java`
- 用自定义短码分别创建 `Abcdef` 与 `abcdef`，期望都成功
- 校验：
  - `short_links` 中两条记录都存在
  - `link_cache_outbox` 中两条记录都存在（不会被 case-insensitive 主键合并）

说明：测试容器中的 MySQL 显式使用 `--collation-server=utf8mb4_unicode_ci`，以确保即便服务端默认大小写不敏感，也必须依赖列级别的 `*_bin` 来保证正确性。

## 上线说明

- 本改动是 **breaking change**（严格大小写）：输错大小写即视为不同短码/不存在
- 不需要处理历史数据（本项目 `validateCode` 已限制为 ASCII 字母数字，迁移为 `ascii` 字符集不会引入额外清洗成本）

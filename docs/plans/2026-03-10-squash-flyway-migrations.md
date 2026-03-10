# Flyway Migrations Squash Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 `server/app` 的 Flyway 迁移从多文件（V1..V9）压缩为单文件（仅保留 `V1__init.sql`），适用于“无任何环境/可删库重来”的阶段。

**Architecture:** 把最终 schema（等价于顺序执行 V1..V9 的结果）直接写进 `V1__init.sql`，删除其余迁移文件，并同步更新文档/注释中对旧迁移版本的引用。

**Tech Stack:** Spring Boot + Flyway + MySQL (Testcontainers)

---

### Task 1: 盘点现有迁移内容（确保最终 schema 列表完整）

**Files:**
- Inspect: `server/app/src/main/resources/db/migration/V1__init.sql`
- Inspect: `server/app/src/main/resources/db/migration/V2__add_stats_indexes.sql`
- Inspect: `server/app/src/main/resources/db/migration/V3__add_redirect_policy_fields.sql`
- Inspect: `server/app/src/main/resources/db/migration/V4__add_visit_events_and_dims.sql`
- Inspect: `server/app/src/main/resources/db/migration/V5__add_lifecycle_fields.sql`
- Inspect: `server/app/src/main/resources/db/migration/V6__add_users_email_global_unique.sql`
- Inspect: `server/app/src/main/resources/db/migration/V7__add_link_cache_outbox.sql`
- Inspect: `server/app/src/main/resources/db/migration/V8__add_link_cache_outbox_cleanup_index.sql`
- Inspect: `server/app/src/main/resources/db/migration/V9__shortlink_code_case_sensitive.sql`

**Step 1:** 确认最终 schema 需要包含的变更点清单（表/列/索引/字符集语义）。

### Task 2: 把最终 schema 折叠进 V1

**Files:**
- Modify: `server/app/src/main/resources/db/migration/V1__init.sql`

**Step 1:** 更新 `short_links`：
- `code` 使用 `CHARACTER SET ascii COLLATE ascii_bin`
- 增加 redirect policy 相关列（V3）
- 增加 `archived_at` + 索引（V5）

**Step 2:** 更新 `link_stats_daily` 索引为 `(tenant_id, day, link_id)`（V2）。

**Step 3:** 增加 V4/V7 引入的新表：
- `link_visit_events`
- `link_stats_dim_daily`
- `link_cache_outbox`（含 `idx_lco_status_processed_at`）

**Step 4:** 补上 `users.email` 全局唯一键（V6）（新库无需数据清理 SQL）。

### Task 3: 删除 V2..V9 迁移文件（破坏性变更）

**Files:**
- Delete: `server/app/src/main/resources/db/migration/V2__add_stats_indexes.sql`
- Delete: `server/app/src/main/resources/db/migration/V3__add_redirect_policy_fields.sql`
- Delete: `server/app/src/main/resources/db/migration/V4__add_visit_events_and_dims.sql`
- Delete: `server/app/src/main/resources/db/migration/V5__add_lifecycle_fields.sql`
- Delete: `server/app/src/main/resources/db/migration/V6__add_users_email_global_unique.sql`
- Delete: `server/app/src/main/resources/db/migration/V7__add_link_cache_outbox.sql`
- Delete: `server/app/src/main/resources/db/migration/V8__add_link_cache_outbox_cleanup_index.sql`
- Delete: `server/app/src/main/resources/db/migration/V9__shortlink_code_case_sensitive.sql`

**Step 1:** `ls server/app/src/main/resources/db/migration` 仅保留 `V1__init.sql`。

### Task 4: 更新文档/注释中对旧迁移版本的引用

**Files:**
- Modify: `deploy/docker-compose.yml`
- Modify: `docs/plans/2026-03-10-shortlink-code-case-sensitive.md`

**Step 1:** 将 “see Flyway V9” 之类引用改为 “see Flyway init schema / V1” 或更通用的表述。

### Task 5: 验证（至少跑单测；尽量跑集成测试）

**Step 1: Run unit tests**

Run: `mvn -f server/pom.xml -Dmaven.repo.local=.m2 test`
Expected: `BUILD SUCCESS`

**Step 2: Run integration tests (optional but preferred)**

Run: `mvn -f server/pom.xml -P it -Dmaven.repo.local=.m2 test`
Expected: `BUILD SUCCESS`（会启动 Testcontainers，需要可用 Docker）

### Task 6: Commit

Run:
`git add server/app/src/main/resources/db/migration/V1__init.sql deploy/docker-compose.yml docs/plans/2026-03-10-shortlink-code-case-sensitive.md`

Run:
`git rm server/app/src/main/resources/db/migration/V2__*.sql ... V9__*.sql`

Run:
`git commit -m "chore(db): squash flyway migrations into V1"`

**Notes:** 此改动会让任何已运行过旧 migrations 的数据库无法继续升级；适用于确认“可删库重来”的阶段。


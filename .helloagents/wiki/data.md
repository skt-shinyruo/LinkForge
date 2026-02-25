# 数据模型（MVP 设计草案）

## 1. 概览

- 核心业务数据使用 MySQL 存储（租户/用户/短链/标签/配置等）。
- 跳转解析缓存与统计计数使用 Redis（降低 MySQL 写放大）。
- 统计建议以“聚合表”为主（daily 粒度），避免为每次跳转写 MySQL 明细导致瓶颈。

---

## 2. 数据表（建议）

### 2.1 tenants（租户）

| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| id | bigint | PK | 租户 ID |
| name | varchar(128) | not null | 租户名称 |
| status | varchar(32) | not null | 状态（active/disabled） |
| created_at | datetime | not null | 创建时间 |
| updated_at | datetime | not null | 更新时间 |

索引：
- `uk_tenants_name`（name）

---

### 2.2 users（用户）

| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| id | bigint | PK | 用户 ID |
| tenant_id | bigint | idx | 所属租户 |
| email | varchar(256) | not null | 邮箱（登录名） |
| password_hash | varchar(255) | not null | 密码哈希 |
| status | varchar(32) | not null | active/disabled |
| created_at | datetime | not null | 创建时间 |
| updated_at | datetime | not null | 更新时间 |

索引：
- `uk_users_tenant_email`（tenant_id, email）

---

### 2.3 user_roles（用户角色）

| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| user_id | bigint | PK | 用户 ID |
| role_code | varchar(64) | PK | 角色（tenant_admin/user） |

---

### 2.4 short_links（短链）

| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| id | bigint | PK | 主键（建议 snowflake） |
| tenant_id | bigint | idx | 租户 ID |
| code | varchar(32) | not null | 短码（全局唯一） |
| original_url | text | not null | 原始 URL |
| note | varchar(512) | null | 备注 |
| enabled | bit(1) | not null | 是否启用 |
| expires_at | datetime | null | 过期时间 |
| archived_at | datetime | null | 归档时间（非空表示已归档，可恢复） |
| redirect_status_code | int | null | 跳转状态码（301/302，可空，空表示使用全局默认） |
| preview_enabled | bit(1) | not null | 是否启用预览确认页（默认 0） |
| unavailable_landing_url | text | null | 不可用（禁用/过期）时的落地页 URL（可空） |
| query_forward_mode | varchar(16) | null | Query 透传策略（OFF/ALLOWLIST/ALL，可空表示继承全局默认） |
| query_forward_allowlist | varchar(1024) | null | Query 透传白名单（逗号分隔，支持 `utm_*` 前缀通配） |
| created_by | bigint | idx | 创建人 |
| created_at | datetime | not null | 创建时间 |
| updated_at | datetime | not null | 更新时间 |

索引：
- `uk_short_links_code`（code）
- `idx_short_links_tenant_created_at`（tenant_id, created_at）
- `idx_short_links_tenant_archived_created_at`（tenant_id, archived_at, created_at）

---

### 2.5 tags / link_tags（标签）

tags：
| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| id | bigint | PK | 标签 ID |
| tenant_id | bigint | idx | 租户 ID |
| name | varchar(64) | not null | 标签名 |
| created_at | datetime | not null | 创建时间 |

索引：
- `uk_tags_tenant_name`（tenant_id, name）

link_tags（关联表）：
| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| link_id | bigint | PK | 短链 ID |
| tag_id | bigint | PK | 标签 ID |

---

### 2.6 api_keys（OpenAPI Key）

| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| id | bigint | PK | 主键 |
| tenant_id | bigint | idx | 租户 ID |
| name | varchar(128) | not null | 名称 |
| key_hash | varchar(255) | not null | Key 哈希（不可逆） |
| status | varchar(32) | not null | active/disabled |
| last_used_at | datetime | null | 最近使用时间 |
| created_at | datetime | not null | 创建时间 |

---

### 2.7 link_stats_daily（按天聚合统计）

| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| link_id | bigint | PK | 短链 ID |
| tenant_id | bigint | idx | 租户 ID |
| day | date | PK | 日期 |
| pv | bigint | not null | PV |
| uv | bigint | not null | UV（可近似） |
| updated_at | datetime | not null | 更新时间 |

索引：
- `idx_stats_tenant_day_link`（tenant_id, day, link_id）

---

### 2.8 link_stats_dim_daily（按天维度聚合统计）

> 用于运营常用维度的分布分析（来源/语言/设备/渠道等），避免每次报表都对明细表做全量 group-by。

| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| tenant_id | bigint | PK | 租户 ID |
| link_id | bigint | PK | 短链 ID |
| day | date | PK | 日期 |
| dim_type | varchar(32) | PK | 维度类型（referer_domain/language/ua_family/...） |
| dim_value | varchar(255) | PK | 维度值（归一化后） |
| pv | bigint | not null | PV |
| uv | bigint | not null | UV（MVP 可先为 0，后续扩展） |
| updated_at | datetime | not null | 更新时间 |

索引：
- `idx_dim_tenant_day_type`（tenant_id, day, dim_type）

---

### 2.9 link_visit_events（访问明细事件，短期留存）

> 用于“最近访问明细”排障与异常访问识别，**不建议长期留存**（默认按天清理）。

| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| id | bigint | PK | 主键（Snowflake） |
| tenant_id | bigint | idx | 租户 ID |
| link_id | bigint | idx | 短链 ID |
| occurred_at | datetime | idx | 发生时间（UTC） |
| request_id | varchar(64) | unique(tenant_id,request_id) | 请求标识（便于链路关联） |
| ip_hash | varchar(64) | null | IP 哈希（默认不落明文 IP） |
| ua_raw | varchar(512) | null | UA 原始字符串（截断） |
| ua_family | varchar(64) | null | 浏览器家族（归一化） |
| os_family | varchar(64) | null | OS 家族（归一化） |
| device_type | varchar(32) | null | 设备类型（desktop/mobile/tablet/bot） |
| referer_domain | varchar(255) | null | 来源域名（归一化，不存 path/query） |
| language | varchar(32) | null | 主语言标签（归一化） |
| utm_source | varchar(128) | null | 渠道 source（白名单采集，截断） |
| utm_medium | varchar(128) | null | 渠道 medium（白名单采集，截断） |
| utm_campaign | varchar(128) | null | 渠道 campaign（白名单采集，截断） |
| created_at | datetime | not null | 入库时间 |

索引：
- `idx_visit_tenant_link_time`（tenant_id, link_id, occurred_at）
- `idx_visit_tenant_time`（tenant_id, occurred_at）
- `uk_visit_tenant_request`（tenant_id, request_id）

---

### 2.10 link_cache_outbox（短链缓存刷新 outbox）

> 用于保证“短链管理侧更新 DB 后，对应 Redis 缓存最终一致可追赶”，覆盖 commit 后进程崩溃/短暂缓存不可用等场景。
>
> 语义：对某个 `code` 触发一次 REFRESH（消费时按 DB 当前状态决定写入或驱逐缓存）。

| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| code | varchar(32) | PK | 短码（全局唯一；用作天然去重/合并 key） |
| status | varchar(16) | not null | PENDING / DONE |
| available_at | datetime | not null | 可执行时间（用于退避重试） |
| attempts | int | not null | 重试次数 |
| last_error | varchar(512) | null | 最近一次失败原因（截断） |
| processed_at | datetime | null | 最近一次成功处理时间 |
| created_at | datetime | not null | 创建时间 |
| updated_at | datetime | not null | 更新时间 |

索引：
- `idx_lco_status_available_at`（status, available_at）
- `idx_lco_status_processed_at`（status, processed_at）— 用于 DONE 清理

运维：
- DONE 清理：API Service 定时按“保留 N 天”删除 `status='DONE' AND processed_at < now - N days` 的行，避免 outbox 表无限增长（可通过环境变量配置保留天数与每轮删除上限）。

---

## 3. 统计实现建议（与表配套）

- Redirect 链路只做轻量写入：
  - PV：Redis `INCR` 计数
  - UV：Redis HyperLogLog（`PFADD`）或 bitmap（按需求取舍）
- 定时任务（每分钟/每 5 分钟/每小时）将计数聚合落库到 `link_stats_daily`
- 访问明细事件（排障/近期分析）：可使用 Redis Stream 缓冲后批量落库到 `link_visit_events`，并设置短期留存（例如 7/14 天）
- 长期明细与复杂分析（如果未来需要）：建议走 Kafka/对象存储/OLAP（如 ClickHouse），不建议长期落 MySQL 明细表

# Backend MySQL Access MyBatis Migration Design

**Date:** 2026-03-11

## Goal

将后端所有 MySQL 访问统一迁移到 **MyBatis + XML Mapper**，并彻底移除：

- Spring Data JPA（`JpaRepository`、`@Entity`、`@EntityScan`、`@EnableJpaRepositories`）
- 应用代码与测试代码中对 `JdbcTemplate` 的直接使用
- 业务层对 Spring Data `Page` / `Pageable` 的依赖

迁移完成后，后端对 MySQL 的访问方式只有一套：Spring Boot + MyBatis + Spring 事务。

## Current State

当前仓库的 MySQL 持久层是混合模式：

- `accounts` / `shortlink` 主要使用 Spring Data JPA
- `analytics` 与 `shortlink` outbox 使用 `JdbcTemplate`
- `shortlink` 的列表查询把 `Page` / `Pageable` 暴露到 controller 和 service
- `app` 启动类显式启用了 JPA repository / entity 扫描

受影响的主要代码面：

- `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/*`
- `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/repo/*`
- `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/*`
- `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/repo/*`
- `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/outbox/LinkCacheOutboxRepository.java`
- `server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/AnalyticsQueryRepository.java`
- `server/analytics/src/main/java/com/linkforge/analytics/application/job/*`
- `server/shortlink/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java`
- `server/shortlink/src/main/java/com/linkforge/shortlink/interfaces/web/*`
- `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- `server/app/src/main/resources/application.yml`
- `server/integration-tests/src/test/java/**`

## Constraints

- 不改后端模块边界：`accounts`、`shortlink`、`redirect`、`analytics`、`foundation`、`app` 继续保留
- 不改数据库 schema 与 Flyway 迁移语义，本次只替换访问实现
- 不引入 MyBatis-Plus、代码生成器或自动分页插件
- 继续使用 Spring `@Transactional` 作为事务边界
- Redis 相关逻辑维持现状，本次只统一 MySQL 访问

## Proposed Architecture

### 1) One persistence stack

后端对 MySQL 的访问统一为：

- `mybatis-spring-boot-starter`
- Java mapper interface
- XML SQL mapper
- 普通 Java POJO 作为持久化模型
- Spring `@Transactional`

JPA 与 `JdbcTemplate` 将被完全移除。

### 2) Keep module boundaries, replace persistence implementations

只替换技术实现，不重做业务边界：

- `accounts` 继续负责租户 / 用户 / 角色 / API Key
- `shortlink` 继续负责短链写模型、标签与 outbox
- `analytics` 继续负责统计查询、落库与清理作业
- `redirect` 保持现有 Redis / 服务编排逻辑，仅验证与新 MyBatis 持久层的兼容性

各模块目录仍遵循：

- `interfaces`
- `application`
- `domain`
- `infrastructure`

变化只发生在 `infrastructure.persistence` 与少量引用上层签名的 controller / service。

### 3) Mapper organization

Mapper 按职责分为三类：

- **Command Mapper**：插入、更新、删除
- **Query Mapper**：详情、列表、聚合查询
- **Batch / Job Mapper**：批量写入、清理、汇总、outbox

建议命名：

- `TenantMapper` / `UserMapper` / `UserRoleMapper` / `ApiKeyMapper`
- `ShortLinkCommandMapper` / `ShortLinkQueryMapper` / `TagMapper` / `LinkTagMapper`
- `LinkCacheOutboxMapper`
- `AnalyticsQueryMapper` / `AnalyticsFlushMapper` / `AnalyticsEventMapper` / `AnalyticsDimensionMapper`

复杂 SQL 一律放 XML，不使用注解 SQL。

### 4) Persistence models

现有 JPA `entity` 类改为普通 POJO，优先保留类名以降低业务层改动量：

- `UserEntity`
- `TenantEntity`
- `ApiKeyEntity`
- `UserRoleEntity`
- `UserRoleId`
- `ShortLinkEntity`
- `TagEntity`
- `LinkTagEntity`
- `LinkTagId`

迁移时移除所有 `jakarta.persistence.*` 注解与 import。对象仅承载字段、getter/setter、必要的 `equals/hashCode`。

### 5) Pagination and query DTOs

`shortlink` 当前将 Spring Data `Page` / `Pageable` 暴露到上层，这会阻碍彻底移除 Spring Data 依赖。

迁移后统一改为项目内分页模型：

- `PageQuery`
- `PageResult<T>`

并为复杂查询增加明确的 filter DTO，例如：

- `ShortLinkSearchQuery`

controller 继续接收 `page` / `size` 参数，但会自行构造 `PageQuery`。service 返回 `PageResult<T>`，mapper 分别实现 `count` 与 `list` SQL。

### 6) Spring Boot bootstrapping

启动与配置层需要同步切换：

- `LinkForgeApplication` 去掉 `@EnableJpaRepositories` 与 `@EntityScan`
- `application.yml` 去掉 `spring.jpa.*`
- `pom.xml` 去掉 `spring-boot-starter-data-jpa`
- 使用 MyBatis 配置与 mapper 扫描替代 JPA bootstrap

`spring-boot-starter-jdbc` 若仅作为 `JdbcTemplate` 载体，则在迁移后删除；若保留仅为 DataSource 基础设施，则必须保证代码中不再直接注入 `JdbcTemplate`。

## Migration Strategy

采用 **分模块渐进迁移，最终统一 MyBatis** 的路线，不做一次性 Big Bang：

1. 先引入 MyBatis 基础设施与公共分页对象
2. 迁移 `accounts`
3. 迁移 `shortlink` 的分页接口与 JPA repository
4. 迁移 `shortlink` outbox
5. 迁移 `analytics` 查询与批处理 SQL
6. 清理 `app` 启动配置、依赖与所有遗留 JPA / `JdbcTemplate` 引用
7. 跑完整测试与构建验证

这样做的目标是让每一段迁移都能被已有测试和编译边界验证，而不是在一个大提交里同时失控。

## Module-by-Module Design

### Accounts

`accounts` 是最适合第一阶段迁移的模块：

- 表结构简单
- 主要是单表 CRUD 与轻度关联
- 当前不依赖 `JdbcTemplate`

替换内容：

- `TenantRepository` → `TenantMapper`
- `UserRepository` → `UserMapper`
- `UserRoleRepository` → `UserRoleMapper`
- `ApiKeyRepository` → `ApiKeyMapper`

对应 service：

- `AuthService`
- `ApiKeyService`
- `UserAdminService`

### Shortlink

`shortlink` 是中等复杂度模块，迁移重点有三块：

- 复杂搜索分页查询
- 标签关联表 `link_tags`
- outbox MySQL 读写

替换内容：

- `ShortLinkRepository` 拆为 command / query mapper
- `TagRepository`、`LinkTagRepository` 改为 MyBatis mapper
- `LinkCacheOutboxRepository` 改为 `LinkCacheOutboxMapper`
- `ShortLinkController`、`OpenApiShortLinkController`、`ShortLinkService` 改用 `PageQuery` / `PageResult`

### Analytics

`analytics` 的核心不是 ORM，而是显式 SQL 与批处理：

- `AnalyticsQueryRepository`
- `AnalyticsFlushJob`
- `AnalyticsDimensionFlushJob`
- `AnalyticsEventIngestJob`
- `AnalyticsEventRetentionJob`

迁移目标是把这些 SQL 从 `JdbcTemplate` 改写成 MyBatis XML，并保持：

- 统计聚合语义
- 批量写入 / 清理语义
- 影响行数与幂等性

### Redirect

`redirect` 不是持久层主战场，但需要验证它读取短链读模型时的行为不变，尤其是：

- 失效链路
- 归档链路
- 大小写敏感短码
- 负缓存逻辑

## Testing Strategy

### 1) Keep integration tests as the main regression net

继续以 `server/integration-tests` 的 Testcontainers 测试作为主要回归门槛，因为复杂 SQL 与事务语义不能只靠 mock 推断。

### 2) Replace test-time JdbcTemplate usage

测试代码也要移除对 `JdbcTemplate` 的直接注入和 mock。集成测试优先通过：

- service
- mapper
- HTTP API

来验证数据状态；job 单测则改为 mock mapper interface。

### 3) Add focused mapper tests only where risk is high

优先给以下高风险区域补 mapper 级或集成级验证：

- `shortlink` 搜索分页
- `link_tags` 关联读取
- `link_cache_outbox` 插入 / 拉取 / 清理
- `analytics` 聚合、批量写入、保留策略清理

## Success Criteria

- 代码库中不再出现：
  - `JpaRepository`
  - `@Entity`
  - `@EntityScan`
  - `@EnableJpaRepositories`
  - 生产代码中的 `JdbcTemplate`
  - 测试代码中的 `JdbcTemplate`
- `shortlink` 不再暴露 Spring Data `Page` / `Pageable`
- `cd server && mvn test` 通过
- `cd server && mvn -Pit test` 通过
- `cd server && mvn -pl app -am package` 通过
- 应用启动后 `/api/**`、`/r/**`、统计作业和短链 outbox 行为与迁移前一致

## Risks and Guardrails

- 复合主键表 `user_roles`、`link_tags` 的 mapper 映射容易出错
- `shortlink` 搜索需要保证 `count` 与 `list` SQL 条件完全一致
- `analytics` 批处理 SQL 的幂等性与更新顺序必须保持
- 去掉 JPA 后，`created_at` / `updated_at` 等数据库默认字段的回填行为要通过真实 DB 验证
- 当前工作区已有用户未提交变更：`README.md`、`docs/architecture.md`；本迁移不应覆盖这些文件上的用户修改

## Completion

**Status:** 完成（2026-03-11）

### Verification

- `cd server && mvn test -Dmaven.repo.local=../.m2/repository` ✅
- `cd server && mvn -Pit test -Dmaven.repo.local=../.m2/repository` ✅
- `cd server && mvn -pl app -am package -Dmaven.repo.local=../.m2/repository` ✅
- `rg` 关键字扫描（`JdbcTemplate` / `JpaRepository` / `@Entity` / `@EntityScan` / `@EnableJpaRepositories` / `Pageable` / JPA 相关依赖）无匹配 ✅

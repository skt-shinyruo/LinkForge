# MyBatis Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将后端所有 MySQL 读写从 JPA 和 `JdbcTemplate` 迁移到 MyBatis XML Mapper，并移除 Spring Data 分页依赖。

**Architecture:** 保持现有模块边界与 Spring 事务边界不变，只替换持久层实现。统一引入 MyBatis mapper + XML SQL，使用项目内分页对象替代 `Page` / `Pageable`。

**Tech Stack:** Java 17, Spring Boot 3.2.x, MyBatis, MySQL, Redis, Flyway, Testcontainers

---

### Task 1: 引入 MyBatis 基础设施并切断 JPA 启动钩子

**Files:**
- Modify: `server/pom.xml`
- Modify: `server/app/pom.xml`
- Modify: `server/accounts/pom.xml`
- Modify: `server/shortlink/pom.xml`
- Modify: `server/analytics/pom.xml`
- Modify: `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- Modify: `server/app/src/main/resources/application.yml`
- Create: `server/app/src/main/java/com/linkforge/app/config/MybatisConfig.java`

**Step 1: 写一个启动级失败测试，约束不再依赖 JPA bootstrap**

Test:
- `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`

Action:
- 在测试中保持应用启动断言，准备让后续移除 `@EnableJpaRepositories` / `@EntityScan` 后仍可启动。

**Step 2: 运行基线测试，记录当前通过状态**

Run: `cd server && mvn -pl integration-tests -Dtest=LinkForgeIntegrationTest test`
Expected: `BUILD SUCCESS`

**Step 3: 引入 MyBatis starter 与配置类**

Action:
- 在 `server/pom.xml` 增加 MyBatis 版本属性
- 在 `server/app/pom.xml` 增加 MyBatis starter
- 在需要直接声明持久层依赖的模块中移除 `spring-boot-starter-data-jpa`
- 新增 `MybatisConfig`，开启 mapper 扫描、驼峰映射和 Java Time 支持

**Step 4: 清理启动类与配置中的 JPA 专用项**

Action:
- 从 `LinkForgeApplication` 删除 `@EnableJpaRepositories` 和 `@EntityScan`
- 从 `application.yml` 删除 `spring.jpa.*`

**Step 5: 运行启动测试，确认应用仍能装配**

Run: `cd server && mvn -pl integration-tests -Dtest=LinkForgeIntegrationTest test`
Expected: `BUILD SUCCESS`

**Step 6: Commit**

Run: `git add server/pom.xml server/app/pom.xml server/accounts/pom.xml server/shortlink/pom.xml server/analytics/pom.xml server/app/src/main/java/com/linkforge/LinkForgeApplication.java server/app/src/main/resources/application.yml server/app/src/main/java/com/linkforge/app/config/MybatisConfig.java server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`

Run: `git commit -m "chore(server): add mybatis bootstrap"`

### Task 2: 引入项目内分页抽象，替换 shortlink 的 Spring Data 分页依赖

**Files:**
- Create: `server/foundation/src/main/java/com/linkforge/foundation/persistence/PageQuery.java`
- Create: `server/foundation/src/main/java/com/linkforge/foundation/persistence/PageResult.java`
- Create: `server/shortlink/src/main/java/com/linkforge/shortlink/application/query/ShortLinkSearchQuery.java`
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java`
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java`
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java`

**Step 1: 写一个覆盖短链分页查询返回结构的失败测试**

Test:
- `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCodeCaseSensitivityIntegrationTest.java`
- `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkDeleteRetentionIntegrationTest.java`

Action:
- 固化列表查询与导出路径所需的分页行为，确保后续替换不会改变 API 结果。

**Step 2: 运行相关集成测试，确认基线**

Run: `cd server && mvn -pl integration-tests -Dtest=ShortLinkCodeCaseSensitivityIntegrationTest,ShortLinkDeleteRetentionIntegrationTest test`
Expected: `BUILD SUCCESS`

**Step 3: 新增 `PageQuery` / `PageResult` 并改造 controller / service 签名**

Action:
- controller 继续接收 `page` / `size`
- service 改成接收 `PageQuery`、返回 `PageResult`
- 短链搜索条件收敛为 `ShortLinkSearchQuery`

**Step 4: 运行相关测试，确认分页行为未退化**

Run: `cd server && mvn -pl integration-tests -Dtest=ShortLinkCodeCaseSensitivityIntegrationTest,ShortLinkDeleteRetentionIntegrationTest test`
Expected: `BUILD SUCCESS`

**Step 5: Commit**

Run: `git add server/foundation/src/main/java/com/linkforge/foundation/persistence/PageQuery.java server/foundation/src/main/java/com/linkforge/foundation/persistence/PageResult.java server/shortlink/src/main/java/com/linkforge/shortlink/application/query/ShortLinkSearchQuery.java server/shortlink/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java server/shortlink/src/main/java/com/linkforge/shortlink/interfaces/web/ShortLinkController.java server/shortlink/src/main/java/com/linkforge/shortlink/interfaces/web/OpenApiShortLinkController.java server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCodeCaseSensitivityIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkDeleteRetentionIntegrationTest.java`

Run: `git commit -m "refactor(shortlink): replace spring data paging"`

### Task 3: 将 accounts 模块从 JPA repository 迁移到 MyBatis mapper

**Files:**
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/TenantEntity.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/UserEntity.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/UserRoleEntity.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/UserRoleId.java`
- Modify: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/ApiKeyEntity.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/mapper/TenantMapper.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/mapper/UserMapper.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/mapper/UserRoleMapper.java`
- Create: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/mapper/ApiKeyMapper.java`
- Create: `server/accounts/src/main/resources/com/linkforge/accounts/infrastructure/persistence/mapper/TenantMapper.xml`
- Create: `server/accounts/src/main/resources/com/linkforge/accounts/infrastructure/persistence/mapper/UserMapper.xml`
- Create: `server/accounts/src/main/resources/com/linkforge/accounts/infrastructure/persistence/mapper/UserRoleMapper.xml`
- Create: `server/accounts/src/main/resources/com/linkforge/accounts/infrastructure/persistence/mapper/ApiKeyMapper.xml`
- Delete: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/repo/TenantRepository.java`
- Delete: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/repo/UserRepository.java`
- Delete: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/repo/UserRoleRepository.java`
- Delete: `server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/repo/ApiKeyRepository.java`

**Step 1: 写 accounts 持久层回归测试**

Test:
- Create: `server/integration-tests/src/test/java/com/linkforge/accounts/AuthPersistenceIntegrationTest.java`
- Create: `server/integration-tests/src/test/java/com/linkforge/accounts/ApiKeyPersistenceIntegrationTest.java`

Action:
- 覆盖登录查用户、按租户列 API Key、读取角色等关键读写路径。

**Step 2: 运行新测试，确认当前基线失败点**

Run: `cd server && mvn -pl integration-tests -Dtest=AuthPersistenceIntegrationTest,ApiKeyPersistenceIntegrationTest test`
Expected: FAIL，提示测试尚未实现或持久层尚未切换

**Step 3: 实现 MyBatis mapper 与 XML SQL，改造 service 依赖**

Action:
- 保留现有 entity 类名，移除 JPA 注解
- mapper XML 覆盖 repository 现有查询语义
- 复合主键 `user_roles` 用独立 `UserRoleId` 作为参数对象或返回对象

**Step 4: 运行 accounts 测试**

Run: `cd server && mvn -pl integration-tests -Dtest=AuthPersistenceIntegrationTest,ApiKeyPersistenceIntegrationTest test`
Expected: `BUILD SUCCESS`

**Step 5: Commit**

Run: `git add server/accounts/src/main/java/com/linkforge/accounts/application/AuthService.java server/accounts/src/main/java/com/linkforge/accounts/application/ApiKeyService.java server/accounts/src/main/java/com/linkforge/accounts/application/UserAdminService.java server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/TenantEntity.java server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/UserEntity.java server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/UserRoleEntity.java server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/UserRoleId.java server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/entity/ApiKeyEntity.java server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/mapper/TenantMapper.java server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/mapper/UserMapper.java server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/mapper/UserRoleMapper.java server/accounts/src/main/java/com/linkforge/accounts/infrastructure/persistence/mapper/ApiKeyMapper.java server/accounts/src/main/resources/com/linkforge/accounts/infrastructure/persistence/mapper/TenantMapper.xml server/accounts/src/main/resources/com/linkforge/accounts/infrastructure/persistence/mapper/UserMapper.xml server/accounts/src/main/resources/com/linkforge/accounts/infrastructure/persistence/mapper/UserRoleMapper.xml server/accounts/src/main/resources/com/linkforge/accounts/infrastructure/persistence/mapper/ApiKeyMapper.xml server/integration-tests/src/test/java/com/linkforge/accounts/AuthPersistenceIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/accounts/ApiKeyPersistenceIntegrationTest.java`

Run: `git commit -m "refactor(accounts): migrate persistence to mybatis"`

### Task 4: 将 shortlink JPA repository 迁移到 MyBatis，并保持列表查询语义

**Files:**
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java`
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/ShortLinkEntity.java`
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/TagEntity.java`
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/LinkTagEntity.java`
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/LinkTagId.java`
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/port/ShortLinkMetaQueryAdapter.java`
- Create: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkCommandMapper.java`
- Create: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.java`
- Create: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/TagMapper.java`
- Create: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/LinkTagMapper.java`
- Create: `server/shortlink/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkCommandMapper.xml`
- Create: `server/shortlink/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.xml`
- Create: `server/shortlink/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/TagMapper.xml`
- Create: `server/shortlink/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/LinkTagMapper.xml`
- Delete: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/repo/ShortLinkRepository.java`
- Delete: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/repo/TagRepository.java`
- Delete: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/repo/LinkTagRepository.java`

**Step 1: 写短链 mapper 回归测试**

Test:
- Create: `server/integration-tests/src/test/java/com/linkforge/shortlink/ShortLinkMapperIntegrationTest.java`
- Use existing: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCodeCaseSensitivityIntegrationTest.java`
- Use existing: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkDeleteRetentionIntegrationTest.java`

Action:
- 覆盖按 code 查询、按 tenant 查询、标签 join、分页 count/list 一致性。

**Step 2: 运行相关测试，确认基线**

Run: `cd server && mvn -pl integration-tests -Dtest=ShortLinkMapperIntegrationTest,ShortLinkCodeCaseSensitivityIntegrationTest,ShortLinkDeleteRetentionIntegrationTest test`
Expected: FAIL，提示 mapper 测试或 MyBatis 实现尚未完成

**Step 3: 落地 MyBatis mapper 和 XML**

Action:
- 将现有 JPQL 搜索改写为 `count` + `list` 两条 XML SQL
- 固定排序仍为 `created_at desc`
- 处理 `link_tags` 关联与标签回填

**Step 4: 运行相关测试**

Run: `cd server && mvn -pl integration-tests -Dtest=ShortLinkMapperIntegrationTest,ShortLinkCodeCaseSensitivityIntegrationTest,ShortLinkDeleteRetentionIntegrationTest test`
Expected: `BUILD SUCCESS`

**Step 5: Commit**

Run: `git add server/shortlink/src/main/java/com/linkforge/shortlink/application/ShortLinkService.java server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/ShortLinkEntity.java server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/TagEntity.java server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/LinkTagEntity.java server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/entity/LinkTagId.java server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/port/ShortLinkMetaQueryAdapter.java server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkCommandMapper.java server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.java server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/TagMapper.java server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/persistence/mapper/LinkTagMapper.java server/shortlink/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkCommandMapper.xml server/shortlink/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.xml server/shortlink/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/TagMapper.xml server/shortlink/src/main/resources/com/linkforge/shortlink/infrastructure/persistence/mapper/LinkTagMapper.xml server/integration-tests/src/test/java/com/linkforge/shortlink/ShortLinkMapperIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCodeCaseSensitivityIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkDeleteRetentionIntegrationTest.java`

Run: `git commit -m "refactor(shortlink): migrate repositories to mybatis"`

### Task 5: 将 shortlink outbox 从 JdbcTemplate 迁移到 MyBatis

**Files:**
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/application/job/LinkCacheOutboxJob.java`
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/application/job/LinkCacheOutboxCleanupJob.java`
- Modify: `server/shortlink/src/main/java/com/linkforge/shortlink/application/job/LinkCacheOutboxMonitorJob.java`
- Create: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/outbox/LinkCacheOutboxMapper.java`
- Create: `server/shortlink/src/main/resources/com/linkforge/shortlink/infrastructure/outbox/LinkCacheOutboxMapper.xml`
- Delete: `server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/outbox/LinkCacheOutboxRepository.java`

**Step 1: 写 outbox 行为回归测试**

Test:
- Use existing: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java`
- Create: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/LinkCacheOutboxCleanupIntegrationTest.java`

Action:
- 固化插入、消费、清理和监控所需的关键行为。

**Step 2: 运行相关测试**

Run: `cd server && mvn -pl integration-tests -Dtest=ShortLinkCacheAfterCommitIntegrationTest,LinkCacheOutboxCleanupIntegrationTest test`
Expected: FAIL，提示 cleanup 测试或 MyBatis outbox 尚未完成

**Step 3: 实现 outbox mapper**

Action:
- 将插入、批量拉取、标记已处理、清理旧记录全部迁到 XML
- 保持现有顺序与状态字段语义

**Step 4: 运行相关测试**

Run: `cd server && mvn -pl integration-tests -Dtest=ShortLinkCacheAfterCommitIntegrationTest,LinkCacheOutboxCleanupIntegrationTest test`
Expected: `BUILD SUCCESS`

**Step 5: Commit**

Run: `git add server/shortlink/src/main/java/com/linkforge/shortlink/application/job/LinkCacheOutboxJob.java server/shortlink/src/main/java/com/linkforge/shortlink/application/job/LinkCacheOutboxCleanupJob.java server/shortlink/src/main/java/com/linkforge/shortlink/application/job/LinkCacheOutboxMonitorJob.java server/shortlink/src/main/java/com/linkforge/shortlink/infrastructure/outbox/LinkCacheOutboxMapper.java server/shortlink/src/main/resources/com/linkforge/shortlink/infrastructure/outbox/LinkCacheOutboxMapper.xml server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/shortlink/application/LinkCacheOutboxCleanupIntegrationTest.java`

Run: `git commit -m "refactor(shortlink): migrate outbox to mybatis"`

### Task 6: 将 analytics 查询与批处理 SQL 从 JdbcTemplate 迁移到 MyBatis

**Files:**
- Modify: `server/analytics/src/main/java/com/linkforge/analytics/application/AnalyticsQueryService.java`
- Modify: `server/analytics/src/main/java/com/linkforge/analytics/application/job/AnalyticsFlushJob.java`
- Modify: `server/analytics/src/main/java/com/linkforge/analytics/application/job/AnalyticsDimensionFlushJob.java`
- Modify: `server/analytics/src/main/java/com/linkforge/analytics/application/job/AnalyticsEventIngestJob.java`
- Modify: `server/analytics/src/main/java/com/linkforge/analytics/application/job/AnalyticsEventRetentionJob.java`
- Create: `server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsQueryMapper.java`
- Create: `server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsFlushMapper.java`
- Create: `server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsEventMapper.java`
- Create: `server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsDimensionMapper.java`
- Create: `server/analytics/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsQueryMapper.xml`
- Create: `server/analytics/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsFlushMapper.xml`
- Create: `server/analytics/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsEventMapper.xml`
- Create: `server/analytics/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsDimensionMapper.xml`
- Delete: `server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/AnalyticsQueryRepository.java`

**Step 1: 写 analytics mapper / job 回归测试**

Test:
- Modify: `server/analytics/src/test/java/com/linkforge/analytics/application/job/AnalyticsFlushJobTest.java`
- Create: `server/integration-tests/src/test/java/com/linkforge/analytics/AnalyticsPersistenceIntegrationTest.java`

Action:
- job 单测从 mock `JdbcTemplate` 改为 mock mapper
- 集成测试覆盖聚合查询、批量落库与清理语义

**Step 2: 运行相关测试**

Run: `cd server && mvn -pl analytics,integration-tests -Dtest=AnalyticsFlushJobTest,AnalyticsPersistenceIntegrationTest test`
Expected: FAIL，提示 mapper 实现尚未完成

**Step 3: 实现 analytics MyBatis mappers**

Action:
- 将 `JdbcTemplate` SQL 原样迁移到 XML，再做必要参数对象化
- 批量 SQL 优先保持语义，不顺手重构算法

**Step 4: 运行相关测试**

Run: `cd server && mvn -pl analytics,integration-tests -Dtest=AnalyticsFlushJobTest,AnalyticsPersistenceIntegrationTest test`
Expected: `BUILD SUCCESS`

**Step 5: Commit**

Run: `git add server/analytics/src/main/java/com/linkforge/analytics/application/AnalyticsQueryService.java server/analytics/src/main/java/com/linkforge/analytics/application/job/AnalyticsFlushJob.java server/analytics/src/main/java/com/linkforge/analytics/application/job/AnalyticsDimensionFlushJob.java server/analytics/src/main/java/com/linkforge/analytics/application/job/AnalyticsEventIngestJob.java server/analytics/src/main/java/com/linkforge/analytics/application/job/AnalyticsEventRetentionJob.java server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsQueryMapper.java server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsFlushMapper.java server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsEventMapper.java server/analytics/src/main/java/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsDimensionMapper.java server/analytics/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsQueryMapper.xml server/analytics/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsFlushMapper.xml server/analytics/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsEventMapper.xml server/analytics/src/main/resources/com/linkforge/analytics/infrastructure/persistence/mapper/AnalyticsDimensionMapper.xml server/analytics/src/test/java/com/linkforge/analytics/application/job/AnalyticsFlushJobTest.java server/integration-tests/src/test/java/com/linkforge/analytics/AnalyticsPersistenceIntegrationTest.java`

Run: `git commit -m "refactor(analytics): migrate mysql access to mybatis"`

### Task 7: 移除遗留 JPA / JdbcTemplate / Spring Data 分页引用，并更新测试

**Files:**
- Modify: `server/integration-tests/src/test/java/com/linkforge/ArchivedLinkRedirectIntegrationTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/RedirectNegativeCacheIntegrationTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCodeCaseSensitivityIntegrationTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkDeleteRetentionIntegrationTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`

**Step 1: 写一个仓库级防回退检查**

Test:
- Create: `server/app/src/test/java/com/linkforge/architecture/PersistenceTechnologyArchitectureTest.java`

Action:
- 用 ArchUnit 或简单文本断言禁止新代码再次引入 `JpaRepository`、`jakarta.persistence`、`JdbcTemplate`、`Pageable`

**Step 2: 移除测试中的 `JdbcTemplate` 依赖，改用 mapper 或更上层断言**

Action:
- 集成测试不再直接操纵 `JdbcTemplate`
- 必要时通过 mapper 插桩或 API 验证数据库结果

**Step 3: 运行单测和集成测试**

Run: `cd server && mvn test`
Expected: `BUILD SUCCESS`

Run: `cd server && mvn -Pit test`
Expected: `BUILD SUCCESS`

**Step 4: Commit**

Run: `git add server/app/src/test/java/com/linkforge/architecture/PersistenceTechnologyArchitectureTest.java server/integration-tests/src/test/java/com/linkforge/ArchivedLinkRedirectIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/RedirectNegativeCacheIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCacheAfterCommitIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkCodeCaseSensitivityIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/shortlink/application/ShortLinkDeleteRetentionIntegrationTest.java server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`

Run: `git commit -m "test(server): remove jdbc and jpa test dependencies"`

### Task 8: 做最终清理与交付验证

**Files:**
- Modify: `server/app/pom.xml`
- Modify: `server/accounts/pom.xml`
- Modify: `server/shortlink/pom.xml`
- Modify: `server/analytics/pom.xml`
- Modify: `server/redirect/pom.xml`
- Inspect: `server/app/src/main/resources/application.yml`
- Inspect: `server/app/src/main/java/com/linkforge/LinkForgeApplication.java`
- Inspect: `docs/plans/2026-03-11-mybatis-migration-design.md`

**Step 1: 清理残留依赖和无用 import**

Action:
- 删除不再需要的 `spring-boot-starter-data-jpa`
- 删除不再需要的 Spring Data domain 依赖
- 删除不再使用的 `JdbcTemplate` import 与字段

**Step 2: 跑仓库级文本检查**

Run: `rg -n "JpaRepository|@Entity|@EntityScan|EnableJpaRepositories|JdbcTemplate|Pageable|org.springframework.data.domain.Page" server`
Expected: 无生产代码命中；允许文档文件命中

**Step 3: 跑完整验证**

Run: `cd server && mvn test`
Expected: `BUILD SUCCESS`

Run: `cd server && mvn -Pit test`
Expected: `BUILD SUCCESS`

Run: `cd server && mvn -pl app -am package`
Expected: `BUILD SUCCESS`

**Step 4: Commit**

Run: `git add server/pom.xml server/app/pom.xml server/accounts/pom.xml server/shortlink/pom.xml server/analytics/pom.xml server/redirect/pom.xml server/app/src/main/resources/application.yml server/app/src/main/java/com/linkforge/LinkForgeApplication.java`

Run: `git commit -m "refactor(server): complete mybatis migration"`

**Notes:**
- 当前工作区已有用户未提交变更：`README.md`、`docs/architecture.md`。执行计划时不要把这些文件纳入提交，除非先与用户协调。
- 如果需要更新架构文档，请在确认用户改动处理方式后单独提交。

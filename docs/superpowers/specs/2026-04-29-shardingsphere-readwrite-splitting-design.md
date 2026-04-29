# ShardingSphere Readwrite Splitting Design

## Scope

This design adds MySQL read/write splitting to LinkForge with Apache ShardingSphere-JDBC and adds a local MySQL primary/replica deployment shape.

The scope is intentionally narrow:

- Use ShardingSphere-JDBC readwrite-splitting only.
- Keep LinkForge as one Spring Boot modular monolith.
- Keep MyBatis mapper code and bounded-context repository adapters unchanged unless tests expose a startup or routing issue.
- Keep Flyway migrations bound to the primary database.
- Provide a local Docker Compose primary/replica example for development and acceptance testing.

Out of scope:

- Data sharding.
- ShardingSphere-Proxy.
- Distributed transactions or XA.
- Automatic MySQL failover.
- Production-grade replication orchestration with ProxySQL, MySQL Router, Orchestrator, MHA, or cloud database control planes.

## Current State

The backend currently uses a single Spring Boot `spring.datasource` configuration in `server/app/src/main/resources/application.yml`. MyBatis and `JdbcTemplate` consume the primary Spring `DataSource` through normal auto-configuration. Flyway is enabled from the same app module and currently migrates through the same single MySQL connection settings.

The deployment file `deploy/docker-compose.yml` currently starts one MySQL 8.0.36 service named `mysql`, one Redis service, the backend, and the frontend. The backend receives `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.

Integration tests mostly register `spring.datasource.url`, `spring.datasource.username`, and `spring.datasource.password` against a single Testcontainers MySQL instance. The implementation must preserve this test path or provide a test profile that keeps single-node datasource behavior simple.

## External References

Apache ShardingSphere current documentation states that ShardingSphere-JDBC can be used in Spring Boot through `org.apache.shardingsphere:shardingsphere-jdbc`, `org.apache.shardingsphere.driver.ShardingSphereDriver`, and a `jdbc:shardingsphere:classpath:...` YAML URL:

- https://shardingsphere.apache.org/document/current/en/user-manual/shardingsphere-jdbc/yaml-config/jdbc-driver/spring-boot/

The current readwrite-splitting YAML rule uses `!READWRITE_SPLITTING`, `dataSourceGroups`, `writeDataSourceName`, `readDataSourceNames`, `transactionalReadQueryStrategy`, and a load balancer:

- https://shardingsphere.apache.org/document/current/en/user-manual/shardingsphere-jdbc/yaml-config/rules/readwrite-splitting/

Apache's download index lists ShardingSphere `5.5.3` as the current release line on 2026-02-28:

- https://downloads.apache.org/shardingsphere/

## Design

### Application Datasource Shape

The app will expose one logical Spring `DataSource` backed by ShardingSphere-JDBC:

```yaml
spring:
  datasource:
    driver-class-name: org.apache.shardingsphere.driver.ShardingSphereDriver
    url: jdbc:shardingsphere:classpath:shardingsphere-readwrite.yaml
```

`shardingsphere-readwrite.yaml` will define physical datasources:

- `write_ds`: primary MySQL connection.
- `read_ds_0`: replica MySQL connection.

It will define one logical readwrite-splitting group:

- `readwrite_ds`: ShardingSphere logical datasource consumed by Spring Boot, MyBatis, transaction management, and `JdbcTemplate`.

The default rule will use:

```yaml
rules:
  - !READWRITE_SPLITTING
    dataSourceGroups:
      readwrite_ds:
        writeDataSourceName: write_ds
        readDataSourceNames:
          - read_ds_0
        transactionalReadQueryStrategy: PRIMARY
        loadBalancerName: random
    loadBalancers:
      random:
        type: RANDOM
```

`transactionalReadQueryStrategy: PRIMARY` is required for LinkForge because write-after-read consistency matters in admin, shortlink mutation, redirect cache refresh, analytics projection, approval, and platform-control workflows. Reads inside a Spring transaction must not observe replica lag.

### Environment Variables

The implementation will replace the single app database variables with explicit write/read settings while keeping a compatible fallback for local tests:

- `DB_WRITE_URL`
- `DB_WRITE_USERNAME`
- `DB_WRITE_PASSWORD`
- `DB_READ_URL`
- `DB_READ_USERNAME`
- `DB_READ_PASSWORD`
- `DB_POOL_SIZE`

For local single-node development and most tests, `DB_READ_*` may default to the write connection. That fallback keeps existing Testcontainers tests from needing a real replica unless the test explicitly verifies read/write splitting.

### Flyway

Flyway must run only against the primary database.

Spring Boot supports explicit Flyway connection properties, so `application.yml` will configure:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    url: ${DB_WRITE_URL:${DB_URL:jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC}}
    user: ${DB_WRITE_USERNAME:${DB_USERNAME:linkforge}}
    password: ${DB_WRITE_PASSWORD:${DB_PASSWORD:linkforge}}
```

This keeps schema creation and migration deterministic and prevents any migration SQL from being routed through the ShardingSphere logical readwrite datasource.

### MyBatis and Transaction Management

MyBatis mapper scanning and type-handler customization remain in the current configuration classes. No bounded-context mapper or repository adapter should know about primary or replica datasource names.

The implementation must not add `@Transactional(readOnly = true)` merely to force replica routing. Under `transactionalReadQueryStrategy: PRIMARY`, every transactional read is routed to the primary, including Spring read-only transactions. Replica routing is therefore limited to eligible non-transactional `SELECT` statements.

Existing write commands keep normal `@Transactional` semantics and therefore route reads inside those transactions to the primary. If a read path needs immediate consistency after a write but currently runs without a transaction, the implementation should leave it on the primary by wrapping the use case in a normal transaction or by using ShardingSphere readwrite-splitting hints in a narrowly documented follow-up.

### Docker Compose Deployment

`deploy/docker-compose.yml` will model:

- `mysql-primary`: MySQL 8.0.36 primary.
- `mysql-replica`: MySQL 8.0.36 replica.
- `redis`
- `server`
- `web`

The primary will enable binary logging and a stable server id. The replica will use a different server id and configure replication from `mysql-primary`.

The Compose design should favor deterministic local acceptance over production-grade failover:

- expose primary on host port `3306`
- expose replica on host port `3307`
- keep named volumes separate
- use init scripts or startup commands to create the replication user and start replication
- document that replication scripts only run on new volumes

The backend service will receive:

```yaml
DB_WRITE_URL: jdbc:mysql://mysql-primary:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
DB_WRITE_USERNAME: ${MYSQL_API_USER:-linkforge_api}
DB_WRITE_PASSWORD: ${MYSQL_API_PASSWORD:-linkforge_api}
DB_READ_URL: jdbc:mysql://mysql-replica:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
DB_READ_USERNAME: ${MYSQL_READ_USER:-linkforge_read}
DB_READ_PASSWORD: ${MYSQL_READ_PASSWORD:-linkforge_read}
```

### Accounts and Privileges

The local deployment will use three MySQL accounts:

- `MYSQL_API_USER`: read/write account used by the backend for `write_ds` and Flyway.
- `MYSQL_READ_USER`: read-only account used by the backend for `read_ds_0`.
- `MYSQL_REPLICATION_USER`: replication account used by the replica to connect to the primary.

The primary initialization must grant the API account the privileges required by Flyway and the application. The read-only account must be restricted to select-only application reads. The replication account must have replication privileges only.

### Test Strategy

TDD will drive implementation. The first failing tests should cover configuration behavior before runtime wiring changes:

- `ApplicationContextRunner` test proving the ShardingSphere JDBC driver datasource configuration is active when read/write splitting is enabled.
- Test proving Flyway receives the primary URL/user/password and does not rely on the ShardingSphere logical datasource.
- Unit-level config test proving `DB_READ_*` defaults to `DB_WRITE_*` when no replica is configured.
- Docker or integration slice proving primary and replica containers become healthy and the backend starts against both endpoints. This can be guarded behind the existing integration-test profile because it requires Docker.

Existing single-node integration tests should continue to run by registering the legacy `spring.datasource.*` or `DB_URL` values and relying on the read fallback to the write datasource.

## Migration Plan

1. Add the ShardingSphere dependency version to the Maven parent and the `shardingsphere-jdbc` dependency to the app module.
2. Add `shardingsphere-readwrite.yaml` under the app resources directory.
3. Change `application.yml` to use `ShardingSphereDriver` and the classpath YAML URL while preserving local/test fallback values.
4. Bind Flyway explicitly to `DB_WRITE_*`.
5. Add tests for datasource properties, Flyway primary binding, and fallback behavior.
6. Update Docker Compose to primary/replica MySQL.
7. Add MySQL init scripts for app accounts and replication.
8. Update `deploy/.env.example`, `README.md`, and deployment notes.
9. Run app-module tests, then full backend tests, then Docker-based integration acceptance.

## Risks and Mitigations

### Replica Lag

Risk: Eligible non-transactional reads may observe stale data on the replica.

Mitigation: Keep transaction reads on primary with `transactionalReadQueryStrategy: PRIMARY`; do not use Spring read-only transactions as a replica-routing signal. Preserve primary routing for write workflows and immediate-consistency reads.

### Flyway Routed Through Logical Datasource

Risk: Schema migrations run through ShardingSphere and are routed unexpectedly.

Mitigation: Configure `spring.flyway.url`, `spring.flyway.user`, and `spring.flyway.password` from `DB_WRITE_*`.

### Testcontainer Complexity

Risk: Existing integration tests become slower or brittle if every test needs a real replica.

Mitigation: Keep the read datasource fallback to the write datasource for tests and local single-node runs. Add only one focused Docker/Compose acceptance path for true primary/replica behavior.

### Dependency Compatibility

Risk: ShardingSphere-JDBC dependency versions, transitive YAML parsing dependencies, or Spring Boot 3 behavior may conflict with the current app stack.

Mitigation: Use the current official `shardingsphere-jdbc` artifact, avoid XA distributed transactions, and verify with an app context test before changing deployment files.

### Local Replication Initialization

Risk: MySQL replication setup scripts only run on clean volumes, which can confuse local developers after changing credentials.

Mitigation: Document the clean-volume requirement and keep volume names explicit.

## Acceptance Criteria

- The backend starts with ShardingSphere-JDBC as the primary Spring datasource.
- MyBatis mappers and `JdbcTemplate` continue to work through the logical datasource.
- Flyway migrations always use the primary MySQL connection.
- Write SQL routes to `write_ds`.
- Eligible non-transactional reads can route to `read_ds_0`.
- Reads inside transactions route to the primary.
- Existing single-MySQL integration tests remain viable through read-to-write fallback.
- Docker Compose can start MySQL primary, MySQL replica, Redis, backend, and frontend.
- README and `.env.example` explain write/read/replication accounts and the local primary/replica deployment shape.

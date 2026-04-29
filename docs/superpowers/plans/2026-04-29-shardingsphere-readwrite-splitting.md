# ShardingSphere Readwrite Splitting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Apache ShardingSphere-JDBC read/write splitting for MySQL and provide a local MySQL primary/replica deployment shape.

**Architecture:** Spring Boot keeps one logical application `DataSource` backed by ShardingSphere-JDBC. ShardingSphere maps `readwrite_ds` to `write_ds` and `read_ds_0`, while Flyway is explicitly bound to the primary MySQL URL. Existing Testcontainers integration tests stay on a single MySQL datasource through test-resource overrides.

**Tech Stack:** Java 17, Spring Boot 3.2.5, MyBatis Spring Boot 3.0.4, Flyway, MySQL 8.0.36, Apache ShardingSphere-JDBC 5.5.3, Docker Compose.

---

## File Structure

- Modify: `server/pom.xml`
  - Add `shardingsphere.version` under `<properties>`.
- Modify: `server/app/pom.xml`
  - Add `org.apache.shardingsphere:shardingsphere-jdbc`.
- Modify: `server/app/src/main/resources/application.yml`
  - Switch the default app datasource to `ShardingSphereDriver`.
  - Use `jdbc:shardingsphere:classpath:shardingsphere-readwrite.yaml?placeholder-type=environment`.
  - Bind Flyway to `DB_WRITE_*` with legacy `DB_URL` fallback.
- Modify: `server/app/src/main/resources/application-local.yml`
  - Remove the local profile's direct `spring.datasource.*` override so local mode still exercises ShardingSphere.
- Create: `server/app/src/main/resources/shardingsphere-readwrite.yaml`
  - Define `write_ds`, `read_ds_0`, and the `!READWRITE_SPLITTING` rule.
- Create: `server/app/src/test/java/com/linkforge/config/ShardingSphereDatasourceConfigTest.java`
  - Lock app YAML, local YAML, and ShardingSphere YAML configuration without opening database sockets.
- Create: `server/integration-tests/src/test/resources/application.properties`
  - Preserve current Testcontainers single-MySQL tests by forcing MySQL driver and Flyway datasource placeholders in test scope.
- Modify: `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`
  - Add one assertion that the integration-test datasource override is active.
- Create: `server/app/src/test/java/com/linkforge/config/DeployReadwriteTopologyTest.java`
  - Lock Docker Compose and `.env.example` topology expectations as file-content tests.
- Modify: `deploy/docker-compose.yml`
  - Replace single `mysql` service with `mysql-primary` and `mysql-replica`.
  - Wire backend `DB_WRITE_*` and `DB_READ_*`.
- Create: `deploy/mysql/primary/init/01-create-users.sh`
  - Create app read/write, read-only, and replication accounts.
- Create: `deploy/mysql/replica/init/01-start-replication.sh`
  - Configure GTID replication from `mysql-primary`.
- Modify: `deploy/.env.example`
  - Add read-only and replication account variables.
- Modify: `README.md`
  - Document local primary/replica deployment and clean-volume note.
- Modify: `docs/architecture.md`
  - Update the deployment shape from single MySQL to primary/replica with ShardingSphere-JDBC routing.

## Task 1: Add ShardingSphere App Datasource Configuration

**Files:**
- Modify: `server/pom.xml`
- Modify: `server/app/pom.xml`
- Modify: `server/app/src/main/resources/application.yml`
- Modify: `server/app/src/main/resources/application-local.yml`
- Create: `server/app/src/main/resources/shardingsphere-readwrite.yaml`
- Create: `server/app/src/test/java/com/linkforge/config/ShardingSphereDatasourceConfigTest.java`

- [ ] **Step 1: Write the failing app configuration test**

Create `server/app/src/test/java/com/linkforge/config/ShardingSphereDatasourceConfigTest.java`:

```java
package com.linkforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ShardingSphereDatasourceConfigTest {

    @Test
    void applicationYml_should_use_shardingsphere_driver_and_primary_flyway_connection() {
        Properties application = yaml("application.yml");

        assertThat(application.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("org.apache.shardingsphere.driver.ShardingSphereDriver");
        assertThat(application.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:shardingsphere:classpath:shardingsphere-readwrite.yaml?placeholder-type=environment");
        assertThat(application.getProperty("spring.flyway.url"))
                .isEqualTo("${DB_WRITE_URL:${DB_URL:jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC}}");
        assertThat(application.getProperty("spring.flyway.user"))
                .isEqualTo("${DB_WRITE_USERNAME:${DB_USERNAME:linkforge}}");
        assertThat(application.getProperty("spring.flyway.password"))
                .isEqualTo("${DB_WRITE_PASSWORD:${DB_PASSWORD:linkforge}}");
    }

    @Test
    void applicationLocalYml_should_not_bypass_shardingsphere_datasource() {
        Properties local = yaml("application-local.yml");

        assertThat(local).doesNotContainKey("spring.datasource.url");
        assertThat(local).doesNotContainKey("spring.datasource.username");
        assertThat(local).doesNotContainKey("spring.datasource.password");
        assertThat(local).doesNotContainKey("spring.datasource.driver-class-name");
    }

    @Test
    void shardingsphereYaml_should_define_readwrite_splitting_with_environment_placeholders() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/shardingsphere-readwrite.yaml"));

        assertThat(yaml)
                .contains("databaseName: linkforge")
                .contains("write_ds:")
                .contains("read_ds_0:")
                .contains("dataSourceClassName: com.zaxxer.hikari.HikariDataSource")
                .contains("driverClassName: $${DB_DRIVER_CLASS_NAME::com.mysql.cj.jdbc.Driver}")
                .contains("jdbcUrl: $${DB_WRITE_URL::jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC}")
                .contains("jdbcUrl: $${DB_READ_URL::jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC}")
                .contains("username: $${DB_WRITE_USERNAME::linkforge}")
                .contains("username: $${DB_READ_USERNAME::linkforge}")
                .contains("!READWRITE_SPLITTING")
                .contains("writeDataSourceName: write_ds")
                .contains("readDataSourceNames:")
                .contains("- read_ds_0")
                .contains("transactionalReadQueryStrategy: PRIMARY")
                .contains("type: RANDOM")
                .contains("sql-show: $${SHARDINGSPHERE_SQL_SHOW::false}");
    }

    private static Properties yaml(String classpathResource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(classpathResource));
        return Objects.requireNonNull(factory.getObject());
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=ShardingSphereDatasourceConfigTest test
```

Expected: FAIL because `application.yml` still uses the single MySQL datasource and `shardingsphere-readwrite.yaml` does not exist.

- [ ] **Step 3: Add the ShardingSphere dependency version**

Modify `server/pom.xml` properties:

```xml
<shardingsphere.version>5.5.3</shardingsphere.version>
```

Place it near the existing third-party version properties:

```xml
<mybatis-spring-boot.version>3.0.4</mybatis-spring-boot.version>
<shardingsphere.version>5.5.3</shardingsphere.version>
```

- [ ] **Step 4: Add the app ShardingSphere dependency**

Modify `server/app/pom.xml` inside the `<!-- data -->` dependency block:

```xml
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>shardingsphere-jdbc</artifactId>
    <version>${shardingsphere.version}</version>
</dependency>
```

Keep the existing MySQL driver dependency because the physical ShardingSphere datasource still connects to MySQL.

- [ ] **Step 5: Update `application.yml` datasource and Flyway settings**

Replace the current `spring.datasource` and `spring.flyway` blocks with:

```yaml
  datasource:
    driver-class-name: org.apache.shardingsphere.driver.ShardingSphereDriver
    url: jdbc:shardingsphere:classpath:shardingsphere-readwrite.yaml?placeholder-type=environment

  flyway:
    enabled: true
    locations: classpath:db/migration
    url: ${DB_WRITE_URL:${DB_URL:jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC}}
    user: ${DB_WRITE_USERNAME:${DB_USERNAME:linkforge}}
    password: ${DB_WRITE_PASSWORD:${DB_PASSWORD:linkforge}}
```

Do not keep `spring.datasource.username`, `spring.datasource.password`, or `spring.datasource.hikari.maximum-pool-size` in `application.yml`; physical pool settings move into ShardingSphere YAML.

- [ ] **Step 6: Update `application-local.yml` so it does not bypass ShardingSphere**

Change `server/app/src/main/resources/application-local.yml` from:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
    username: linkforge
    password: linkforge
  data:
    redis:
      host: localhost
      port: 6379
```

to:

```yaml
spring:
  flyway:
    url: jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
    user: linkforge
    password: linkforge
  data:
    redis:
      host: localhost
      port: 6379
```

Keep the existing `app:` block unchanged.

- [ ] **Step 7: Add the ShardingSphere readwrite YAML**

Create `server/app/src/main/resources/shardingsphere-readwrite.yaml`:

```yaml
databaseName: linkforge

dataSources:
  write_ds:
    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
    driverClassName: $${DB_DRIVER_CLASS_NAME::com.mysql.cj.jdbc.Driver}
    jdbcUrl: $${DB_WRITE_URL::jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC}
    username: $${DB_WRITE_USERNAME::linkforge}
    password: $${DB_WRITE_PASSWORD::linkforge}
    maximumPoolSize: $${DB_WRITE_POOL_SIZE::20}
  read_ds_0:
    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
    driverClassName: $${DB_DRIVER_CLASS_NAME::com.mysql.cj.jdbc.Driver}
    jdbcUrl: $${DB_READ_URL::jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC}
    username: $${DB_READ_USERNAME::linkforge}
    password: $${DB_READ_PASSWORD::linkforge}
    maximumPoolSize: $${DB_READ_POOL_SIZE::20}

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

props:
  sql-show: $${SHARDINGSPHERE_SQL_SHOW::false}
```

Use ShardingSphere's `$${ENV_NAME::default}` placeholder syntax because the JDBC URL uses `placeholder-type=environment`.

- [ ] **Step 8: Run the focused test and verify it passes**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=ShardingSphereDatasourceConfigTest test
```

Expected: PASS.

- [ ] **Step 9: Compile the app module with dependencies resolved**

Run:

```bash
cd server && mvn -q -pl app -am -DskipTests compile
```

Expected: PASS. If Maven cannot resolve `org.apache.shardingsphere:shardingsphere-jdbc:5.5.3`, stop and verify Maven Central availability before changing the version.

- [ ] **Step 10: Commit**

```bash
git add server/pom.xml server/app/pom.xml server/app/src/main/resources/application.yml server/app/src/main/resources/application-local.yml server/app/src/main/resources/shardingsphere-readwrite.yaml server/app/src/test/java/com/linkforge/config/ShardingSphereDatasourceConfigTest.java
git commit -m "feat: configure ShardingSphere readwrite datasource"
```

## Task 2: Preserve Existing Single-MySQL Integration Tests

**Files:**
- Create: `server/integration-tests/src/test/resources/application.properties`
- Modify: `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`

- [ ] **Step 1: Write the failing integration datasource assertion**

In `server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java`, add this test method inside `LinkForgeIntegrationTest`:

```java
    @Test
    void integrationTests_use_plain_mysql_datasource_and_primary_flyway_override() {
        var env = applicationContext.getEnvironment();

        assertThat(env.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(env.getProperty("spring.datasource.url"))
                .startsWith("jdbc:mysql:");
        assertThat(env.getProperty("spring.flyway.url"))
                .startsWith("jdbc:mysql:");
        assertThat(env.getProperty("spring.flyway.user"))
                .isEqualTo(MYSQL.getUsername());
        assertThat(env.getProperty("spring.flyway.password"))
                .isEqualTo(MYSQL.getPassword());
    }
```

- [ ] **Step 2: Run the focused integration test and verify it fails**

Run:

```bash
cd server && mvn -q -P it -pl integration-tests -am -Dtest=LinkForgeIntegrationTest#integrationTests_use_plain_mysql_datasource_and_primary_flyway_override test
```

Expected: FAIL because the integration-test module does not yet override the ShardingSphere driver and explicit Flyway connection.

- [ ] **Step 3: Add integration-test resource overrides**

Create `server/integration-tests/src/test/resources/application.properties`:

```properties
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.flyway.url=${spring.datasource.url}
spring.flyway.user=${spring.datasource.username}
spring.flyway.password=${spring.datasource.password}
```

These properties preserve the existing `DynamicPropertySource` pattern that registers `spring.datasource.url`, `spring.datasource.username`, and `spring.datasource.password` against one Testcontainers MySQL instance.

- [ ] **Step 4: Re-run the focused integration test and verify it passes**

Run:

```bash
cd server && mvn -q -P it -pl integration-tests -am -Dtest=LinkForgeIntegrationTest#integrationTests_use_plain_mysql_datasource_and_primary_flyway_override test
```

Expected: PASS.

- [ ] **Step 5: Run the integration smoke test class**

Run:

```bash
cd server && mvn -q -P it -pl integration-tests -am -Dtest=LinkForgeIntegrationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/integration-tests/src/test/resources/application.properties server/integration-tests/src/test/java/com/linkforge/LinkForgeIntegrationTest.java
git commit -m "test: preserve single-node datasource integration tests"
```

## Task 3: Add Docker Compose MySQL Primary/Replica Topology

**Files:**
- Create: `server/app/src/test/java/com/linkforge/config/DeployReadwriteTopologyTest.java`
- Modify: `deploy/docker-compose.yml`
- Create: `deploy/mysql/primary/init/01-create-users.sh`
- Create: `deploy/mysql/replica/init/01-start-replication.sh`
- Modify: `deploy/.env.example`

- [ ] **Step 1: Write the failing deployment topology test**

Create `server/app/src/test/java/com/linkforge/config/DeployReadwriteTopologyTest.java`:

```java
package com.linkforge.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeployReadwriteTopologyTest {

    @Test
    void dockerCompose_should_define_mysql_primary_replica_and_server_readwrite_env() throws Exception {
        String compose = Files.readString(Path.of("../../deploy/docker-compose.yml"));

        assertThat(compose)
                .contains("mysql-primary:")
                .contains("mysql-replica:")
                .contains("mysql_primary_data:")
                .contains("mysql_replica_data:")
                .contains("\"3306:3306\"")
                .contains("\"3307:3306\"")
                .contains("--server-id=1")
                .contains("--server-id=2")
                .contains("--log-bin=mysql-bin")
                .contains("--gtid-mode=ON")
                .contains("DB_WRITE_URL: jdbc:mysql://mysql-primary:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC")
                .contains("DB_READ_URL: jdbc:mysql://mysql-replica:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC")
                .contains("DB_WRITE_USERNAME: ${MYSQL_API_USER:-linkforge_api}")
                .contains("DB_READ_USERNAME: ${MYSQL_READ_USER:-linkforge_read}")
                .contains("mysql-primary:")
                .contains("condition: service_healthy")
                .contains("mysql-replica:")
                .contains("condition: service_healthy");

        assertThat(compose).doesNotContain("DB_URL: jdbc:mysql://mysql:3306/linkforge");
    }

    @Test
    void envExample_should_document_mysql_read_and_replication_accounts() throws Exception {
        String env = Files.readString(Path.of("../../deploy/.env.example"));

        assertThat(env)
                .contains("MYSQL_ROOT_PASSWORD=root")
                .contains("MYSQL_API_USER=linkforge_api")
                .contains("MYSQL_API_PASSWORD=linkforge_api")
                .contains("MYSQL_READ_USER=linkforge_read")
                .contains("MYSQL_READ_PASSWORD=linkforge_read")
                .contains("MYSQL_REPLICATION_USER=linkforge_repl")
                .contains("MYSQL_REPLICATION_PASSWORD=linkforge_repl");
    }

    @Test
    void mysql_init_scripts_should_create_accounts_and_start_replication() throws Exception {
        String primary = Files.readString(Path.of("../../deploy/mysql/primary/init/01-create-users.sh"));
        String replica = Files.readString(Path.of("../../deploy/mysql/replica/init/01-start-replication.sh"));

        assertThat(primary)
                .contains("CREATE USER IF NOT EXISTS '${MYSQL_API_USER}'@'%'")
                .contains("GRANT ALL PRIVILEGES ON linkforge.* TO '${MYSQL_API_USER}'@'%'")
                .contains("CREATE USER IF NOT EXISTS '${MYSQL_READ_USER}'@'%'")
                .contains("GRANT SELECT ON linkforge.* TO '${MYSQL_READ_USER}'@'%'")
                .contains("CREATE USER IF NOT EXISTS '${MYSQL_REPLICATION_USER}'@'%'")
                .contains("GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO '${MYSQL_REPLICATION_USER}'@'%'");

        assertThat(replica)
                .contains("CHANGE REPLICATION SOURCE TO")
                .contains("SOURCE_HOST='mysql-primary'")
                .contains("SOURCE_AUTO_POSITION=1")
                .contains("GET_SOURCE_PUBLIC_KEY=1")
                .contains("START REPLICA");
    }
}
```

- [ ] **Step 2: Run the focused deployment test and verify it fails**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=DeployReadwriteTopologyTest test
```

Expected: FAIL because Compose still defines the single `mysql` service and the init scripts do not exist.

- [ ] **Step 3: Replace the MySQL service in `deploy/docker-compose.yml`**

Replace the current `mysql:` service with:

```yaml
  mysql-primary:
    image: mysql:8.0.36
    environment:
      MYSQL_DATABASE: linkforge
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}
      MYSQL_API_USER: ${MYSQL_API_USER:-linkforge_api}
      MYSQL_API_PASSWORD: ${MYSQL_API_PASSWORD:-linkforge_api}
      MYSQL_READ_USER: ${MYSQL_READ_USER:-linkforge_read}
      MYSQL_READ_PASSWORD: ${MYSQL_READ_PASSWORD:-linkforge_read}
      MYSQL_REPLICATION_USER: ${MYSQL_REPLICATION_USER:-linkforge_repl}
      MYSQL_REPLICATION_PASSWORD: ${MYSQL_REPLICATION_PASSWORD:-linkforge_repl}
    ports:
      - "3306:3306"
    command:
      - "--server-id=1"
      - "--log-bin=mysql-bin"
      - "--binlog-format=ROW"
      - "--gtid-mode=ON"
      - "--enforce-gtid-consistency=ON"
      - "--character-set-server=utf8mb4"
      - "--collation-server=utf8mb4_unicode_ci"
    volumes:
      - mysql_primary_data:/var/lib/mysql
      - ./mysql/primary/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p${MYSQL_ROOT_PASSWORD:-root} --silent"]
      interval: 5s
      timeout: 3s
      retries: 20

  mysql-replica:
    image: mysql:8.0.36
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}
      MYSQL_REPLICATION_USER: ${MYSQL_REPLICATION_USER:-linkforge_repl}
      MYSQL_REPLICATION_PASSWORD: ${MYSQL_REPLICATION_PASSWORD:-linkforge_repl}
    ports:
      - "3307:3306"
    command:
      - "--server-id=2"
      - "--relay-log=mysql-relay-bin"
      - "--read-only=ON"
      - "--gtid-mode=ON"
      - "--enforce-gtid-consistency=ON"
      - "--character-set-server=utf8mb4"
      - "--collation-server=utf8mb4_unicode_ci"
    volumes:
      - mysql_replica_data:/var/lib/mysql
      - ./mysql/replica/init:/docker-entrypoint-initdb.d:ro
    depends_on:
      mysql-primary:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "mysql -uroot -p${MYSQL_ROOT_PASSWORD:-root} -e \"SHOW REPLICA STATUS\\\\G\" | grep -q \"Replica_IO_Running: Yes\" && mysql -uroot -p${MYSQL_ROOT_PASSWORD:-root} -e \"SHOW REPLICA STATUS\\\\G\" | grep -q \"Replica_SQL_Running: Yes\""]
      interval: 5s
      timeout: 5s
      retries: 30
```

Update the `server.environment` database variables:

```yaml
      DB_WRITE_URL: jdbc:mysql://mysql-primary:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
      DB_WRITE_USERNAME: ${MYSQL_API_USER:-linkforge_api}
      DB_WRITE_PASSWORD: ${MYSQL_API_PASSWORD:-linkforge_api}
      DB_READ_URL: jdbc:mysql://mysql-replica:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
      DB_READ_USERNAME: ${MYSQL_READ_USER:-linkforge_read}
      DB_READ_PASSWORD: ${MYSQL_READ_PASSWORD:-linkforge_read}
```

Update `server.depends_on`:

```yaml
    depends_on:
      mysql-primary:
        condition: service_healthy
      mysql-replica:
        condition: service_healthy
      redis:
        condition: service_healthy
```

Update volumes:

```yaml
volumes:
  mysql_primary_data:
  mysql_replica_data:
```

- [ ] **Step 4: Add the primary init script**

Create `deploy/mysql/primary/init/01-create-users.sh`:

```sh
#!/bin/sh
set -eu

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS linkforge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS '${MYSQL_API_USER}'@'%' IDENTIFIED BY '${MYSQL_API_PASSWORD}';
GRANT ALL PRIVILEGES ON linkforge.* TO '${MYSQL_API_USER}'@'%';

CREATE USER IF NOT EXISTS '${MYSQL_READ_USER}'@'%' IDENTIFIED BY '${MYSQL_READ_PASSWORD}';
GRANT SELECT ON linkforge.* TO '${MYSQL_READ_USER}'@'%';

CREATE USER IF NOT EXISTS '${MYSQL_REPLICATION_USER}'@'%' IDENTIFIED BY '${MYSQL_REPLICATION_PASSWORD}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO '${MYSQL_REPLICATION_USER}'@'%';

FLUSH PRIVILEGES;
SQL
```

- [ ] **Step 5: Add the replica init script**

Create `deploy/mysql/replica/init/01-start-replication.sh`:

```sh
#!/bin/sh
set -eu

until mysqladmin ping -h mysql-primary -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent; do
  sleep 2
done

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='mysql-primary',
  SOURCE_PORT=3306,
  SOURCE_USER='${MYSQL_REPLICATION_USER}',
  SOURCE_PASSWORD='${MYSQL_REPLICATION_PASSWORD}',
  SOURCE_AUTO_POSITION=1,
  GET_SOURCE_PUBLIC_KEY=1;
START REPLICA;
SQL
```

- [ ] **Step 6: Make init scripts executable**

Run:

```bash
chmod +x deploy/mysql/primary/init/01-create-users.sh deploy/mysql/replica/init/01-start-replication.sh
```

Expected: command exits 0.

- [ ] **Step 7: Update `deploy/.env.example`**

Change the MySQL section to:

```properties
# MySQL（docker compose 初始化用；注意 init 脚本仅在“全新数据卷”时执行）
MYSQL_ROOT_PASSWORD=root
MYSQL_API_USER=linkforge_api
MYSQL_API_PASSWORD=linkforge_api
MYSQL_READ_USER=linkforge_read
MYSQL_READ_PASSWORD=linkforge_read
MYSQL_REPLICATION_USER=linkforge_repl
MYSQL_REPLICATION_PASSWORD=linkforge_repl
```

- [ ] **Step 8: Run the focused deployment test and verify it passes**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=DeployReadwriteTopologyTest test
```

Expected: PASS.

- [ ] **Step 9: Validate Compose syntax**

Run:

```bash
cd deploy && docker compose --env-file .env.example config >/tmp/linkforge-compose.yml
```

Expected: command exits 0 and `/tmp/linkforge-compose.yml` includes `mysql-primary`, `mysql-replica`, `DB_WRITE_URL`, and `DB_READ_URL`.

- [ ] **Step 10: Commit**

```bash
git add deploy/docker-compose.yml deploy/.env.example deploy/mysql/primary/init/01-create-users.sh deploy/mysql/replica/init/01-start-replication.sh server/app/src/test/java/com/linkforge/config/DeployReadwriteTopologyTest.java
git commit -m "feat: add mysql primary replica compose topology"
```

## Task 4: Update Runtime Documentation and Architecture SSOT

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `server/app/src/test/java/com/linkforge/config/DeployReadwriteTopologyTest.java`

- [ ] **Step 1: Extend the deployment documentation assertion**

Append this test method to `DeployReadwriteTopologyTest`:

```java
    @Test
    void docs_should_describe_shardingsphere_and_primary_replica_deployment() throws Exception {
        String readme = Files.readString(Path.of("../../README.md"));
        String architecture = Files.readString(Path.of("../../docs/architecture.md"));

        assertThat(readme)
                .contains("ShardingSphere-JDBC")
                .contains("mysql-primary")
                .contains("mysql-replica")
                .contains("MYSQL_READ_USER")
                .contains("MYSQL_REPLICATION_USER")
                .contains("docker compose down -v");

        assertThat(architecture)
                .contains("ShardingSphere-JDBC")
                .contains("readwrite_ds")
                .contains("write_ds")
                .contains("read_ds_0")
                .contains("Flyway")
                .contains("primary");
    }
```

- [ ] **Step 2: Run the documentation assertion and verify it fails**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=DeployReadwriteTopologyTest#docs_should_describe_shardingsphere_and_primary_replica_deployment test
```

Expected: FAIL because README and architecture docs still describe a single MySQL service.

- [ ] **Step 3: Update `README.md` local startup notes**

In the MySQL account bullet under section `## 1. 本地一键启动（推荐）`, replace:

```markdown
- （可选）MySQL 账号（默认值可直接使用）：
  - `MYSQL_API_USER` / `MYSQL_API_PASSWORD`：后端服务读写账号（Flyway 迁移与业务写入）
```

with:

```markdown
- （可选）MySQL 账号（默认值可直接使用）：
  - `MYSQL_API_USER` / `MYSQL_API_PASSWORD`：主库读写账号（Flyway 迁移与业务写入）
  - `MYSQL_READ_USER` / `MYSQL_READ_PASSWORD`：从库只读账号（ShardingSphere-JDBC 读流量）
  - `MYSQL_REPLICATION_USER` / `MYSQL_REPLICATION_PASSWORD`：MySQL 主从复制账号
```

Add this paragraph after the startup command:

````markdown
本地 compose 使用 `mysql-primary` + `mysql-replica` 模拟 MySQL 主从部署，后端通过 ShardingSphere-JDBC 暴露一个逻辑数据源。Flyway 固定连接主库，业务写入走 `write_ds`，符合条件的非事务查询可走 `read_ds_0`；事务内读保持走主库，降低复制延迟导致的写后读不一致风险。

如果修改 MySQL 初始化账号、复制参数或需要重新初始化主从数据卷，请先停止并删除旧卷：

```bash
cd deploy
docker compose --env-file .env down -v
```
````

- [ ] **Step 4: Update `docs/architecture.md` deployment section**

Replace the current Deployment Shape section:

```markdown
The repository currently ships as:

- one backend runtime (`server/app`)
- one frontend app (`web`)
- supporting infrastructure such as MySQL and Redis

This is not a microservice deployment. Module boundaries remain for ownership and tests, but day-to-day correctness is designed for a single deployed monolith rather than future service extraction.
```

with:

```markdown
The repository currently ships as:

- one backend runtime (`server/app`)
- one frontend app (`web`)
- supporting infrastructure such as MySQL primary/replica and Redis

The backend uses Apache ShardingSphere-JDBC as the logical application datasource. `readwrite_ds` routes writes to `write_ds` and eligible non-transactional reads to `read_ds_0`; transactional reads stay on the primary through `transactionalReadQueryStrategy: PRIMARY`. Flyway is explicitly bound to the primary MySQL connection and does not migrate through the logical read/write splitting datasource.

This is not a microservice deployment. Module boundaries remain for ownership and tests, but day-to-day correctness is designed for a single deployed monolith rather than future service extraction.
```

- [ ] **Step 5: Run the documentation assertion and verify it passes**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=DeployReadwriteTopologyTest#docs_should_describe_shardingsphere_and_primary_replica_deployment test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add README.md docs/architecture.md server/app/src/test/java/com/linkforge/config/DeployReadwriteTopologyTest.java
git commit -m "docs: document ShardingSphere mysql topology"
```

## Task 5: Final Verification

**Files:**
- No planned source edits.

- [ ] **Step 1: Run focused app tests**

Run:

```bash
cd server && mvn -q -pl app -am -Dtest=ShardingSphereDatasourceConfigTest,DeployReadwriteTopologyTest test
```

Expected: PASS.

- [ ] **Step 2: Run app module regression**

Run:

```bash
cd server && mvn -q -pl app -am test
```

Expected: PASS.

- [ ] **Step 3: Run integration smoke test**

Run:

```bash
cd server && mvn -q -P it -pl integration-tests -am -Dtest=LinkForgeIntegrationTest test
```

Expected: PASS.

- [ ] **Step 4: Run full backend tests**

Run:

```bash
cd server && mvn -q test
```

Expected: PASS.

- [ ] **Step 5: Validate Docker Compose syntax**

Run:

```bash
cd deploy && docker compose --env-file .env.example config >/tmp/linkforge-compose.yml
```

Expected: PASS and rendered config includes `mysql-primary`, `mysql-replica`, `DB_WRITE_URL`, and `DB_READ_URL`.

- [ ] **Step 6: Run local primary/replica acceptance**

Run:

```bash
cd deploy
docker compose --env-file .env.example up --build -d mysql-primary mysql-replica redis server
docker compose --env-file .env.example ps
curl -fsS http://localhost:8080/actuator/health
```

Expected:

- `mysql-primary`, `mysql-replica`, `redis`, and `server` are healthy or running.
- `curl` returns a health response with overall status `UP`.

- [ ] **Step 7: Clean up local acceptance containers**

Run:

```bash
cd deploy && docker compose --env-file .env.example down
```

Expected: command exits 0. Do not use `down -v` unless the user explicitly wants to delete local MySQL data.

- [ ] **Step 8: Final status check**

Run:

```bash
git status --short
```

Expected: no uncommitted changes.

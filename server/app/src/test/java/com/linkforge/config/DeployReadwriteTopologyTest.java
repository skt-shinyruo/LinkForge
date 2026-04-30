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
                .contains("image: redis:8.6.2-alpine")
                .contains("--server-id=1")
                .contains("--server-id=2")
                .contains("--log-bin=mysql-bin")
                .contains("--gtid-mode=ON")
                .contains("DB_WRITE_URL: jdbc:mysql://mysql-primary:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true")
                .contains("DB_READ_URL: jdbc:mysql://mysql-replica:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true")
                .contains("DB_WRITE_USERNAME: ${MYSQL_API_USER:-linkforge_api}")
                .contains("DB_READ_USERNAME: ${MYSQL_READ_USER:-linkforge_read}")
                .contains("mysql-primary:")
                .contains("condition: service_healthy")
                .contains("mysql-replica:")
                .contains("condition: service_healthy");

        assertThat(compose).doesNotContain("DB_URL: jdbc:mysql://mysql:3306/linkforge");
        assertThat(compose)
                .contains("\"${LINKFORGE_HTTP_BIND:-127.0.0.1}:${LINKFORGE_HTTP_PORT:-18080}:80\"")
                .doesNotContain("\"3306:3306\"")
                .doesNotContain("\"3307:3306\"")
                .doesNotContain("\"6380:6379\"")
                .doesNotContain("\"8080:8080\"")
                .doesNotContain("\"80:80\"");
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
                .contains("MYSQL_REPLICATION_PASSWORD=linkforge_repl")
                .contains("LINKFORGE_HTTP_BIND=127.0.0.1")
                .contains("LINKFORGE_HTTP_PORT=18080")
                .contains("APP_BASE_URL=http://localhost:18080");
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

    @Test
    void serverDockerfile_should_copy_all_app_reactor_modules() throws Exception {
        String dockerfile = Files.readString(Path.of("../Dockerfile"));

        assertThat(dockerfile)
                .contains("COPY foundation /app/foundation")
                .contains("COPY contracts /app/contracts")
                .contains("COPY accounts /app/accounts")
                .contains("COPY shortlink /app/shortlink")
                .contains("COPY redirect /app/redirect")
                .contains("COPY analytics /app/analytics")
                .contains("COPY platform /app/platform")
                .contains("COPY governance /app/governance")
                .contains("COPY app /app/app");
    }

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
                .contains("http://localhost:18080/")
                .contains("docker compose down -v");

        assertThat(architecture)
                .contains("ShardingSphere-JDBC")
                .contains("readwrite_ds")
                .contains("write_ds")
                .contains("read_ds_0")
                .contains("Flyway")
                .contains("primary");
    }
}

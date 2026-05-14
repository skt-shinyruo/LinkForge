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
                .isEqualTo("${DB_WRITE_URL:${DB_URL:jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true}}");
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
                .contains("jdbcUrl: $${DB_WRITE_URL::jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true}")
                .contains("jdbcUrl: $${DB_READ_URL::jdbc:mysql://localhost:3306/linkforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true}")
                .contains("username: $${DB_WRITE_USERNAME::linkforge}")
                .contains("username: $${DB_READ_USERNAME::linkforge}")
                .contains("!READWRITE_SPLITTING")
                .contains("writeDataSourceName: write_ds")
                .contains("readDataSourceNames:")
                .contains("- read_ds_0")
                .contains("transactionalReadQueryStrategy: PRIMARY")
                .contains("!SINGLE")
                .contains("tables:")
                .contains("- \"*.*\"")
                .contains("defaultDataSource: readwrite_ds")
                .contains("type: RANDOM")
                .contains("sql-show: $${SHARDINGSPHERE_SQL_SHOW::false}");
    }

    @Test
    void appPom_should_include_shardingsphere_runtime_plugins_required_by_yaml_driver() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom)
                .contains("<artifactId>shardingsphere-jdbc</artifactId>")
                .contains("<artifactId>shardingsphere-jdbc-dialect-mysql</artifactId>")
                .contains("<artifactId>shardingsphere-parser-sql-engine-mysql</artifactId>")
                .contains("<artifactId>shardingsphere-infra-url-classpath</artifactId>")
                .contains("<artifactId>shardingsphere-infra-data-source-pool-hikari</artifactId>")
                .contains("<artifactId>shardingsphere-standalone-mode-repository-memory</artifactId>")
                .contains("<artifactId>shardingsphere-authority-simple</artifactId>")
                .contains("<artifactId>shardingsphere-readwrite-splitting-core</artifactId>")
                .contains("<artifactId>shardingsphere-single-core</artifactId>")
                .contains("<artifactId>commons-lang3</artifactId>")
                .contains("<version>${commons-lang3.version}</version>");
    }

    @Test
    void parentPom_should_use_commonsLang3_version_required_by_shardingsphere() throws Exception {
        String pom = Files.readString(Path.of("../pom.xml"));

        assertThat(pom).contains("<commons-lang3.version>3.18.0</commons-lang3.version>");
    }

    private static Properties yaml(String classpathResource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(classpathResource));
        return Objects.requireNonNull(factory.getObject());
    }
}

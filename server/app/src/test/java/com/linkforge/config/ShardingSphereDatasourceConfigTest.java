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

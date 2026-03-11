package com.linkforge.app.config;

import org.apache.ibatis.type.InstantTypeHandler;
import org.apache.ibatis.type.JapaneseDateTypeHandler;
import org.apache.ibatis.type.LocalDateTimeTypeHandler;
import org.apache.ibatis.type.LocalDateTypeHandler;
import org.apache.ibatis.type.LocalTimeTypeHandler;
import org.apache.ibatis.type.MonthTypeHandler;
import org.apache.ibatis.type.OffsetDateTimeTypeHandler;
import org.apache.ibatis.type.OffsetTimeTypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.YearMonthTypeHandler;
import org.apache.ibatis.type.YearTypeHandler;
import org.apache.ibatis.type.ZonedDateTimeTypeHandler;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.JapaneseDate;

@Configuration
public class MybatisConfig {

    @Bean
    static MapperScannerConfigurer mybatisMapperScannerConfigurer() {
        // Real mapper scanning is introduced in later migration tasks. For Task 1,
        // register a no-op scanner so MyBatis starter does not warn on empty packages.
        return new NoOpMapperScannerConfigurer();
    }

    @Bean
    ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            configuration.setMapUnderscoreToCamelCase(true);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(Instant.class, InstantTypeHandler.class);
            registry.register(JapaneseDate.class, JapaneseDateTypeHandler.class);
            registry.register(LocalDate.class, LocalDateTypeHandler.class);
            registry.register(LocalDateTime.class, LocalDateTimeTypeHandler.class);
            registry.register(LocalTime.class, LocalTimeTypeHandler.class);
            registry.register(Month.class, MonthTypeHandler.class);
            registry.register(OffsetDateTime.class, OffsetDateTimeTypeHandler.class);
            registry.register(OffsetTime.class, OffsetTimeTypeHandler.class);
            registry.register(Year.class, YearTypeHandler.class);
            registry.register(YearMonth.class, YearMonthTypeHandler.class);
            registry.register(ZonedDateTime.class, ZonedDateTimeTypeHandler.class);
        };
    }

    private static final class NoOpMapperScannerConfigurer extends MapperScannerConfigurer {

        @Override
        public void afterPropertiesSet() {
            // Intentionally no-op until real mapper interfaces exist.
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            // Intentionally no-op until real mapper interfaces exist.
        }
    }
}

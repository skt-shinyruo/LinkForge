package com.linkforge.platform.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartupValidationTest {

    @Test
    void strict_mode_should_reject_default_snowflake_id_config() {
        AppProperties p = new AppProperties();
        List<String> errors = new ArrayList<>();

        StartupValidation.validateIdBasics(p, true, null, errors);

        assertThat(errors).anyMatch(s -> s.contains("生产/strict 模式禁止使用默认"));
    }

    @Test
    void non_strict_mode_should_allow_default_snowflake_id_config() {
        AppProperties p = new AppProperties();
        List<String> errors = new ArrayList<>();

        StartupValidation.validateIdBasics(p, false, null, errors);

        assertThat(errors).isEmpty();
    }

    @Test
    void id_values_should_be_within_expected_range() {
        AppProperties p = new AppProperties();
        p.getId().setWorkerId(32);
        p.getId().setDatacenterId(-1);

        List<String> errors = new ArrayList<>();
        StartupValidation.validateIdBasics(p, false, null, errors);

        assertThat(errors).contains("app.id.worker-id 仅支持 0~31");
        assertThat(errors).contains("app.id.datacenter-id 仅支持 0~31");
    }
}


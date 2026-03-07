package com.linkforge.edge.net;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CidrBlockTest {

    @Test
    void parse_ipv4_cidr_should_match() {
        CidrBlock b = CidrBlock.parse("10.0.0.0/8");
        assertThat(b.contains("10.1.2.3")).isTrue();
        assertThat(b.contains("11.1.2.3")).isFalse();
    }

    @Test
    void parse_single_ip_should_be_exact_match() {
        CidrBlock b = CidrBlock.parse("1.2.3.4");
        assertThat(b.contains("1.2.3.4")).isTrue();
        assertThat(b.contains("1.2.3.5")).isFalse();
    }

    @Test
    void parse_ipv6_cidr_should_match() {
        CidrBlock b = CidrBlock.parse("2001:db8::/32");
        assertThat(b.contains("2001:db8::1")).isTrue();
        assertThat(b.contains("2001:db9::1")).isFalse();
    }

    @Test
    void parse_invalid_should_throw() {
        assertThatThrownBy(() -> CidrBlock.parse("not-an-ip"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CidrBlock.parse("1.2.3.4/33"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}


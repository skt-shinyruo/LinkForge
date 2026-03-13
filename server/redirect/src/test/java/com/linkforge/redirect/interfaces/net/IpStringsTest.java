package com.linkforge.redirect.interfaces.net;

import com.linkforge.redirect.domain.net.IpStrings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpStringsTest {

    @Test
    void isValidIp_should_accept_ip_literals() {
        assertThat(IpStrings.isValidIp("1.2.3.4")).isTrue();
        assertThat(IpStrings.isValidIp("2001:db8::1")).isTrue();
    }

    @Test
    void isValidIp_should_reject_hostnames() {
        assertThat(IpStrings.isValidIp("localhost")).isFalse();
    }
}

package com.linkforge.redirect.interfaces.net;

import com.linkforge.redirect.domain.net.CidrBlock;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

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

    @Test
    void parse_hostname_should_throw() {
        assertThatThrownBy(() -> CidrBlock.parse("localhost/8"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contains_should_not_resolve_hostnames() throws Exception {
        InetAddress byName = InetAddress.getByName("localhost");
        InetAddress[] all = InetAddress.getAllByName("localhost");

        Set<String> resolvedIps = new HashSet<>();
        resolvedIps.add(byName.getHostAddress());
        for (InetAddress a : all) {
            resolvedIps.add(a.getHostAddress());
        }

        boolean matched = false;
        for (String ip : resolvedIps) {
            CidrBlock b = CidrBlock.parse(ip);
            if (b.contains("localhost")) {
                matched = true;
                break;
            }
        }

        assertThat(matched).isFalse();
    }
}

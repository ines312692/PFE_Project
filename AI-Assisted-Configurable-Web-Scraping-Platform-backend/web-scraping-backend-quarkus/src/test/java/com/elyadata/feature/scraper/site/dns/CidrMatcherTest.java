package com.elyadata.feature.scraper.site.dns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CidrMatcherTest {

    private static InetAddress ip(String s) {
        try {
            return InetAddress.getByName(s);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "192.168.1.5,   192.168.1.0/24, true",
            "192.168.1.255, 192.168.1.0/24, true",
            "192.168.2.1,   192.168.1.0/24, false",
            "10.0.0.1,      10.0.0.0/8,     true",
            "10.255.255.255,10.0.0.0/8,     true",
            "11.0.0.1,      10.0.0.0/8,     false",
            "172.16.5.4,    172.16.0.0/12,  true",
            "172.32.0.1,    172.16.0.0/12,  false"
    })
    void isInRange_ipv4(String ip, String cidr, boolean expected) {
        assertThat(CidrMatcher.isInRange(ip(ip), cidr)).isEqualTo(expected);
    }

    @Test
    void isInRange_prefix32MatchesExactIpOnly() {
        assertThat(CidrMatcher.isInRange(ip("8.8.8.8"), "8.8.8.8/32")).isTrue();
        assertThat(CidrMatcher.isInRange(ip("8.8.8.9"), "8.8.8.8/32")).isFalse();
    }

    @Test
    void isInRange_prefix0MatchesEverything() {
        assertThat(CidrMatcher.isInRange(ip("1.2.3.4"), "0.0.0.0/0")).isTrue();
        assertThat(CidrMatcher.isInRange(ip("255.255.255.255"), "0.0.0.0/0")).isTrue();
    }


    @Test
    void isInRange_ipv6InRange() {
        assertThat(CidrMatcher.isInRange(ip("2606:4700::1"), "2606:4700::/32")).isTrue();
    }

    @Test
    void isInRange_ipv6OutOfRange() {
        assertThat(CidrMatcher.isInRange(ip("2607:f8b0::1"), "2606:4700::/32")).isFalse();
    }

    // ---------------------------------------------------------------------
    // Family mismatch: v4 IP against v6 CIDR and vice-versa
    // ---------------------------------------------------------------------

    @Test
    void isInRange_returnsFalseForFamilyMismatch() {
        assertThat(CidrMatcher.isInRange(ip("192.168.1.1"), "2606:4700::/32")).isFalse();
        assertThat(CidrMatcher.isInRange(ip("2606:4700::1"), "192.168.1.0/24")).isFalse();
    }

    // ---------------------------------------------------------------------
    // Malformed input -> false, never throws
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "192.168.1.0",
            "192.168.1.0/abc",
            "not-an-ip/24",
            "192.168.1.0/-1",
            "192.168.1.0/33"
    })
    void isInRange_returnsFalseForMalformedCidr(String cidr) {
        assertThat(CidrMatcher.isInRange(ip("192.168.1.1"), cidr)).isFalse();
    }

    @Test
    void isInAnyRange_trueWhenAnyMatches() {
        List<String> ranges = List.of("10.0.0.0/8", "192.168.1.0/24", "172.16.0.0/12");

        assertThat(CidrMatcher.isInAnyRange(ip("192.168.1.50"), ranges)).isTrue();
    }

    @Test
    void isInAnyRange_falseWhenNoneMatch() {
        List<String> ranges = List.of("10.0.0.0/8", "172.16.0.0/12");

        assertThat(CidrMatcher.isInAnyRange(ip("8.8.8.8"), ranges)).isFalse();
    }

    @Test
    void isInAnyRange_falseForEmptyList() {
        assertThat(CidrMatcher.isInAnyRange(ip("8.8.8.8"), List.of())).isFalse();
    }
}

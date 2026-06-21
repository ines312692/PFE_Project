package com.elyadata.feature.scraper.site.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;


public final class CidrMatcher {

    private CidrMatcher() {
    }

    public static boolean isInRange(InetAddress ip, String cidr) {
        try {
            int slash = cidr.indexOf('/');
            if (slash < 0) return false;

            String baseStr = cidr.substring(0, slash);
            int prefix = Integer.parseInt(cidr.substring(slash + 1));

            byte[] ipBytes = ip.getAddress();
            byte[] baseBytes = InetAddress.getByName(baseStr).getAddress();

            if (ipBytes.length != baseBytes.length) return false;
            if (prefix < 0 || prefix > ipBytes.length * 8) return false;

            int fullBytes = prefix / 8;
            int remainderBits = prefix % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != baseBytes[i]) return false;
            }
            if (remainderBits == 0) return true;

            int mask = (0xFF << (8 - remainderBits)) & 0xFF;
            return (ipBytes[fullBytes] & mask) == (baseBytes[fullBytes] & mask);

        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }

    public static boolean isInAnyRange(InetAddress ip, List<String> cidrs) {
        for (String cidr : cidrs) {
            if (isInRange(ip, cidr)) return true;
        }
        return false;
    }
}
package com.linkforge.redirect.interfaces.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * CIDR 网段匹配工具（IPv4 / IPv6）。
 *
 * <p>用于可信代理链（trusted proxies）与风控黑白名单的快速判断。</p>
 */
public final class CidrBlock {

    private final byte[] network;
    private final int prefixLength;

    private CidrBlock(byte[] network, int prefixLength) {
        this.network = network;
        this.prefixLength = prefixLength;
    }

    public static CidrBlock parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("CIDR 不能为空");
        }
        String t = raw.trim();
        if (t.isBlank()) {
            throw new IllegalArgumentException("CIDR 不能为空");
        }

        String ipPart = t;
        Integer prefix = null;
        int slash = t.indexOf('/');
        if (slash >= 0) {
            ipPart = t.substring(0, slash).trim();
            String p = t.substring(slash + 1).trim();
            if (p.isBlank()) {
                throw new IllegalArgumentException("CIDR 前缀长度不能为空: " + raw);
            }
            try {
                prefix = Integer.parseInt(p);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("CIDR 前缀长度不合法: " + raw);
            }
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByName(ipPart);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("CIDR IP 不合法: " + raw);
        }

        int maxBits = addr.getAddress().length * 8;
        int prefixLen = prefix == null ? maxBits : prefix;
        if (prefixLen < 0 || prefixLen > maxBits) {
            throw new IllegalArgumentException("CIDR 前缀长度超范围: " + raw);
        }

        byte[] network = masked(addr.getAddress(), prefixLen);
        return new CidrBlock(network, prefixLen);
    }

    public boolean contains(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        InetAddress addr;
        try {
            addr = InetAddress.getByName(ip.trim());
        } catch (UnknownHostException e) {
            return false;
        }
        return contains(addr);
    }

    public boolean contains(InetAddress addr) {
        if (addr == null) {
            return false;
        }
        byte[] bytes = addr.getAddress();
        if (bytes.length != network.length) {
            return false;
        }
        return Arrays.equals(masked(bytes, prefixLength), network);
    }

    private static byte[] masked(byte[] in, int prefixLen) {
        byte[] out = Arrays.copyOf(in, in.length);
        int fullBytes = prefixLen / 8;
        int remainBits = prefixLen % 8;

        // 清零 prefix 之后的整字节
        for (int i = fullBytes + (remainBits > 0 ? 1 : 0); i < out.length; i++) {
            out[i] = 0;
        }
        if (remainBits == 0) {
            return out;
        }

        // 对 prefix 所在字节做 bit mask
        int mask = 0xFF << (8 - remainBits);
        out[fullBytes] = (byte) (out[fullBytes] & mask);
        return out;
    }
}

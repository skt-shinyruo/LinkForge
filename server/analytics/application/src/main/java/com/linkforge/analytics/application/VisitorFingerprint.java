package com.linkforge.analytics.application;

import com.linkforge.contract.analytics.VisitContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * 访问者假名化标识生成器。
 *
 * <p>日 UV 指纹包含 UTC 日期，因而同一客户端跨日会产生不同值。摘要不是匿名化数据，salt 必须作为敏感配置管理；
 * 更换 salt 会使新旧标识无法连续比较。</p>
 *
 * <p>输入长度受限，避免不可信 IP 或 User-Agent 在 Redirect 热路径中消耗无界 CPU/内存。</p>
 */
public final class VisitorFingerprint {

    private VisitorFingerprint() {
    }

    // 防御性长度上限：避免 Redirect 热路径对不可信请求头做无界摘要计算。
    private static final int MAX_FP_IP_LEN = 64;
    private static final int MAX_FP_UA_LEN = 512;
    private static final int MAX_FP_SALT_LEN = 128;

    /**
     * 计算用于单个 UTC 自然日 UV 去重的指纹。
     *
     * <p>缺失 IP 或 User-Agent 仍参与固定字符串拼接，因此不同缺失组合可能共享同一日指纹；这是当前低成本近似
     * 统计的既定限制，不能作为登录、风控或唯一访客身份。</p>
     *
     * @param day 统计所属 UTC 日期
     * @param visitContext 原始访问上下文，可为空
     * @param salt 部署侧 salt，可为空但生产环境应配置
     * @return 小写十六进制 SHA-256 摘要
     */
    public static String fingerprint(LocalDate day, VisitContext visitContext, String salt) {
        String ip = firstNonBlankCapped(visitContext == null ? null : visitContext.ip(), MAX_FP_IP_LEN);
        String ua = firstNonBlankCapped(visitContext == null ? null : visitContext.userAgent(), MAX_FP_UA_LEN);
        String safeSalt = salt == null ? "" : capLen(salt, MAX_FP_SALT_LEN);
        String raw = day + "|" + ip + "|" + ua + "|" + safeSalt;
        return sha256Hex(raw);
    }

    private static String firstNonBlankCapped(String v, int maxLen) {
        if (v == null) {
            return null;
        }
        String t = capLen(v, maxLen).trim();
        return t.isBlank() ? null : t;
    }

    private static String capLen(String v, int maxLen) {
        if (v == null) {
            return null;
        }
        if (maxLen <= 0) {
            return v;
        }
        return v.length() <= maxLen ? v : v.substring(0, maxLen);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}

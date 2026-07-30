package com.linkforge.shortlink.domain;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_CODE;

/**
 * 大小写敏感的短码值对象。
 *
 * <p>构造时先去除首尾空白，再要求长度为 6 至 32，且每个字符都属于 ASCII {@code [0-9A-Za-z]}。
 * 不执行大小写折叠或其他规范化，因此 {@code abc123} 与 {@code ABC123} 是不同短码。唯一性不属于单值约束，
 * 由仓储按域名范围检查。</p>
 */
public record ShortCode(String value) {

    public ShortCode {
        if (value == null) {
            throw new ShortLinkDomainException(INVALID_CODE, "短码不能为空");
        }
        value = value.trim();
        if (value.isBlank()) {
            throw new ShortLinkDomainException(INVALID_CODE, "短码不能为空");
        }
        if (value.length() < 6 || value.length() > 32) {
            throw new ShortLinkDomainException(INVALID_CODE, "短码长度需为 6-32");
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z');
            if (!ok) {
                throw new ShortLinkDomainException(INVALID_CODE, "短码仅允许 [0-9A-Za-z]");
            }
        }
    }

    /**
     * 从外部字符串创建并校验短码。
     *
     * @param raw 可包含首尾空白的原始值
     * @return 已去除首尾空白的短码
     */
    public static ShortCode of(String raw) {
        return new ShortCode(raw);
    }
}

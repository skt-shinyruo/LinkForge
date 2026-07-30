package com.linkforge.redirect.interfaces.web;

import com.linkforge.redirect.domain.net.CidrBlock;
import com.linkforge.redirect.domain.net.CidrBlocks;
import com.linkforge.redirect.domain.net.IpStrings;
import com.linkforge.foundation.config.EdgeProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redirect Edge 客户端 IP 解析器（带可信代理链约束）。
 *
 * <p>原则：只有当 remoteAddr 命中 trustedProxies 时，才采信 forwarded headers；否则忽略所有
 * forwarded headers，直接使用 remoteAddr。受信任时优先使用网关清洗后的 {@code X-Real-IP}；其缺失或
 * 非法时才从 {@code X-Forwarded-For} 右向左剥离可信代理。</p>
 */
@Component
public class RedirectClientIpResolver {

    /**
     * 防御性限制：X-Forwarded-For 头部若异常过长，直接忽略（返回 remoteAddr）。
     *
     * <p>原因：XFF 可被客户端伪造，超长值会导致 split/substring 产生大量对象，形成 DoS 面。</p>
     */
    private static final int MAX_XFF_HEADER_LEN = 1024;

    /**
     * 防御性限制：最多解析 XFF 中的 N 个 token（从右往左），避免异常长链路导致 CPU/内存放大。
     */
    private static final int MAX_XFF_TOKENS = 20;

    private final List<CidrBlock> trustedProxies;

    public RedirectClientIpResolver(EdgeProperties properties) {
        List<String> raw = properties == null ? null : properties.getTrustedProxies();
        this.trustedProxies = CidrBlocks.parseList(raw, "app.edge.trusted-proxies");
    }

    /**
     * 按可信代理链规则解析客户端 IP。
     *
     * <p>返回值可能为 {@code null} 或无法识别的 remote 地址；调用方必须把它视作风控维度中的未知值，
     * 而不是回退信任任意请求头。超长 XFF 与非法 token 均回退到 remoteAddr。</p>
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String remote = IpStrings.cleanIpToken(request.getRemoteAddr());
        if (remote == null || !IpStrings.isValidIp(remote)) {
            return remote;
        }

        // 安全默认：未配置 trustedProxies 时，永远不信任 forwarded headers。
        if (trustedProxies.isEmpty() || !CidrBlocks.containsAny(trustedProxies, remote)) {
            return remote;
        }

        // 优先信任受控网关覆盖后的 X-Real-IP（通常是清洗后的真实客户端 IP）。
        String xReal = IpStrings.cleanIpToken(request.getHeader("X-Real-IP"));
        if (IpStrings.isValidIp(xReal)) {
            return xReal;
        }

        return resolveFromXff(remote, request.getHeader("X-Forwarded-For"));
    }

    private String resolveFromXff(String remote, String xffRaw) {
        if (xffRaw == null || xffRaw.isBlank()) {
            return remote;
        }
        if (xffRaw.length() > MAX_XFF_HEADER_LEN) {
            return remote;
        }

        // 从右往左解析，避免 split 造成大量临时对象
        int tokens = 0;
        int endExclusive = xffRaw.length();
        for (int i = xffRaw.length() - 1; i >= -1; i--) {
            boolean isSeparator = i < 0 || xffRaw.charAt(i) == ',';
            if (!isSeparator) {
                continue;
            }

            int startInclusive = i + 1;
            if (startInclusive < endExclusive) {
                String token = xffRaw.substring(startInclusive, endExclusive);
                String ip = IpStrings.cleanIpToken(token);
                if (IpStrings.isValidIp(ip)) {
                    // 从右到左剔除可信代理，取第一个“非可信代理”作为客户端 IP
                    if (!CidrBlocks.containsAny(trustedProxies, ip)) {
                        return ip;
                    }
                }

                tokens++;
                if (tokens >= MAX_XFF_TOKENS) {
                    break;
                }
            }
            endExclusive = i;
        }
        return remote;
    }
}

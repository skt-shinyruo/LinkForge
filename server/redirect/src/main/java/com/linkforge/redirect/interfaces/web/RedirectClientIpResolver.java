package com.linkforge.redirect.interfaces.web;

import com.linkforge.redirect.domain.net.CidrBlock;
import com.linkforge.redirect.domain.net.CidrBlocks;
import com.linkforge.redirect.domain.net.IpStrings;
import com.linkforge.foundation.config.EdgeProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Redirect Edge 客户端 IP 解析器（带可信代理链约束）。
 *
 * <p>原则：只有当 remoteAddr 命中 trustedProxies 时，才采信 forwarded headers；
 * 否则忽略所有 forwarded headers，直接使用 remoteAddr。</p>
 */
@Component
public class RedirectClientIpResolver {

    private final List<CidrBlock> trustedProxies;

    public RedirectClientIpResolver(EdgeProperties properties) {
        List<String> raw = properties == null ? null : properties.getTrustedProxies();
        this.trustedProxies = CidrBlocks.parseList(raw, "app.edge.trusted-proxies");
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String remote = IpStrings.cleanIpToken(request.getRemoteAddr());
        if (remote == null || !IpStrings.isValidIp(remote)) {
            return remote;
        }

        // 安全默认值：未配置 trustedProxies 时，永远不信任 forwarded headers
        if (trustedProxies.isEmpty() || !CidrBlocks.containsAny(trustedProxies, remote)) {
            return remote;
        }

        // 优先信任受控网关覆盖后的 X-Real-IP（通常是“清洗后的真实客户端 IP”）
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

        String[] parts = xffRaw.split(",");
        List<String> ips = new ArrayList<>(parts.length);
        for (String p : parts) {
            String ip = IpStrings.cleanIpToken(p);
            if (IpStrings.isValidIp(ip)) {
                ips.add(ip);
            }
        }
        if (ips.isEmpty()) {
            return remote;
        }

        // 从右到左剔除可信代理，取第一个“非可信代理”作为客户端 IP
        for (int i = ips.size() - 1; i >= 0; i--) {
            String ip = ips.get(i);
            if (!CidrBlocks.containsAny(trustedProxies, ip)) {
                return ip;
            }
        }
        return remote;
    }
}

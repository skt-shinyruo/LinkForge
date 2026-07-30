package com.linkforge.redirect.interfaces.startup;

import com.linkforge.foundation.config.EdgeProperties;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.foundation.config.StartupValidation;
import com.linkforge.foundation.runtime.startup.StartupCheck;
import com.linkforge.redirect.domain.net.CidrBlocks;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * Redirect 与 Edge 配置的启动期门禁。
 *
 * <p>它只校验静态配置组合：缓存 TTL、状态码、landing URL、query 参数模式、可信代理 CIDR 与风控阈值。
 * 通过校验不代表 Redis、Shortlink 或反向代理链已经可用；这些运行时问题仍须由监控和降级路径处理。</p>
 */
@Component
public class RedirectStartupCheck implements StartupCheck {

    private final RedirectProperties redirectProperties;
    private final EdgeProperties edgeProperties;

    public RedirectStartupCheck(RedirectProperties redirectProperties, EdgeProperties edgeProperties) {
        this.redirectProperties = redirectProperties;
        this.edgeProperties = edgeProperties;
    }

    /**
     * 将可恢复的配置错误追加到共享列表，由统一启动策略决定是否在 strict 模式阻止启动。
     */
    @Override
    public void validate(boolean strict, List<String> errors) {
        validateRedirect(errors);
        validateEdgeRiskControl(errors);
    }

    private void validateRedirect(List<String> errors) {
        try {
            StartupValidation.validateRedirectBasics(redirectProperties, errors);
        } catch (Exception e) {
            errors.add("redirect 配置读取失败: " + e.getMessage());
            return;
        }

        try {
            String notFoundLandingUrl = trimToNull(redirectProperties == null ? null : redirectProperties.getNotFoundLandingUrl());
            if (notFoundLandingUrl != null && !isHttpUrl(notFoundLandingUrl)) {
                errors.add("app.redirect.not-found-landing-url 必须为 http/https URL");
            }
            String goneLandingUrl = trimToNull(redirectProperties == null ? null : redirectProperties.getGoneLandingUrl());
            if (goneLandingUrl != null && !isHttpUrl(goneLandingUrl)) {
                errors.add("app.redirect.gone-landing-url 必须为 http/https URL");
            }

            String mode = trimToNull(redirectProperties == null ? null : redirectProperties.getQueryForwardMode());
            if (mode != null) {
                String upper = mode.trim().toUpperCase();
                if (!("OFF".equals(upper) || "ALLOWLIST".equals(upper) || "ALL".equals(upper))) {
                    errors.add("app.redirect.query-forward-mode 仅支持 OFF/ALLOWLIST/ALL");
                }
            }

            var allowlist = redirectProperties == null ? null : redirectProperties.getQueryForwardAllowlist();
            if (allowlist != null) {
                for (String param : allowlist) {
                    String value = trimToNull(param);
                    if (value != null && !StartupValidation.isValidParamPattern(value)) {
                        errors.add("app.redirect.query-forward-allowlist 包含不合法项: " + value);
                        break;
                    }
                }
            }

            var reserved = redirectProperties == null ? null : redirectProperties.getQueryForwardReservedParams();
            if (reserved != null) {
                for (String param : reserved) {
                    String value = trimToNull(param);
                    if (value != null && !StartupValidation.isValidParamPattern(value)) {
                        errors.add("app.redirect.query-forward-reserved-params 包含不合法项: " + value);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            errors.add("redirect 配置校验异常: " + e.getMessage());
        }
    }

    private void validateEdgeRiskControl(List<String> errors) {
        try {
            if (edgeProperties != null) {
                CidrBlocks.parseList(edgeProperties.getTrustedProxies(), "app.edge.trusted-proxies");

                var rc = edgeProperties.getRiskControl();
                if (rc != null) {
                    CidrBlocks.parseList(rc.getIpAllowlist(), "app.edge.risk-control.ip-allowlist");
                    CidrBlocks.parseList(rc.getIpDenylist(), "app.edge.risk-control.ip-denylist");

                    var rl = rc.getRateLimit();
                    if (rl != null && rl.isEnabled()) {
                        if (rl.getWindowSeconds() <= 0) {
                            errors.add("app.edge.risk-control.rate-limit.window-seconds 必须 > 0");
                        }
                        if (rl.getIpMaxRequests() < 0) {
                            errors.add("app.edge.risk-control.rate-limit.ip-max-requests 必须 >= 0");
                        }
                        if (rl.getIpCodeMaxRequests() < 0) {
                            errors.add("app.edge.risk-control.rate-limit.ip-code-max-requests 必须 >= 0");
                        }
                    }

                    var bot = rc.getBot();
                    if (bot != null && bot.isEnabled() && bot.getIpMaxRequests() < 0) {
                        errors.add("app.edge.risk-control.bot.ip-max-requests 必须 >= 0");
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        } catch (Exception e) {
            errors.add("edge 配置校验异常: " + e.getMessage());
        }
    }

    private static String trimToNull(String value) {
        return StartupValidation.trimToNull(value);
    }

    private static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            return scheme != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (Exception e) {
            return false;
        }
    }
}

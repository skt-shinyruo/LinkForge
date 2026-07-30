package com.linkforge.foundation.security;

/**
 * 挂在 Spring Security Authentication details 上的 API Key 认证补充信息。
 *
 * <p>用户主体本身不承载 API Key scope，因此适配层只能在同一已认证 Authentication 上读取该值。该记录
 * 不含 API Key 原文或 secret；{@code applicationId} 可为空仅用于兼容历史载荷，实际授权必须谨慎处理。</p>
 */
public record ApiKeyAuthenticationDetails(long apiKeyId, Long applicationId) {
}

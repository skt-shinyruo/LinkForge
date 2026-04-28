package com.linkforge.foundation.security;

public record ApiKeyAuthenticationResult(long tenantId, Long applicationId, long apiKeyId) {
}

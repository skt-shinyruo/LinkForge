package com.linkforge.accounts.application;

public record ApiKeyAuthResult(long tenantId, Long applicationId, long apiKeyId) {
}

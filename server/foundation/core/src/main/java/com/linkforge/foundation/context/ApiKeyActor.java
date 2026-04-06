package com.linkforge.foundation.context;

public record ApiKeyActor(long tenantId, long apiKeyId, Long applicationId) implements ApplicationActor {
}

package com.linkforge.accounts.application;

import java.time.LocalDateTime;

public record ApiKeyInfoResult(long id, Long applicationId, String name, String status, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
}

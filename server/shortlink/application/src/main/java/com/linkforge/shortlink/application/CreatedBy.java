package com.linkforge.shortlink.application;

import com.linkforge.shortlink.domain.CreatedByType;

public record CreatedBy(long id, CreatedByType type) {

    public static CreatedBy user(long userId) {
        return new CreatedBy(userId, CreatedByType.USER);
    }

    public static CreatedBy apiKey(long apiKeyId) {
        return new CreatedBy(apiKeyId, CreatedByType.API_KEY);
    }
}

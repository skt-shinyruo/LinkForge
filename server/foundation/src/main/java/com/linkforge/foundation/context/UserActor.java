package com.linkforge.foundation.context;

import java.util.Set;

public record UserActor(long tenantId, long userId, String email, Set<String> roles) implements ApplicationActor {
}

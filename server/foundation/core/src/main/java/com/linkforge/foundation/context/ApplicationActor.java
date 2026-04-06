package com.linkforge.foundation.context;

public sealed interface ApplicationActor permits UserActor, ApiKeyActor {

    long tenantId();
}

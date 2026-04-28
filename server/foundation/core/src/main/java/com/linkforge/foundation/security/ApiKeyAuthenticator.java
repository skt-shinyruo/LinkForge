package com.linkforge.foundation.security;

public interface ApiKeyAuthenticator {

    ApiKeyAuthenticationResult authenticateApiKey(String apiKey);
}

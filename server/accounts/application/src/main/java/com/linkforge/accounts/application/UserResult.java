package com.linkforge.accounts.application;

import java.util.Set;

public record UserResult(long id, long tenantId, String email, String status, Set<String> roles) {
}

package com.linkforge.accounts.interfaces.web;

import java.util.Set;

public record UserHttpResponse(long id, long tenantId, String email, String status, Set<String> roles) {
}

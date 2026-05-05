package com.linkforge.accounts.application;

import java.util.Set;

public record CreateUserCommand(String email, String password, Set<String> roles) {
}

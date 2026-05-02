package com.linkforge.shortlink.application;

import java.util.List;

public record ImportResult(int success, int failed, List<String> errors) {
}

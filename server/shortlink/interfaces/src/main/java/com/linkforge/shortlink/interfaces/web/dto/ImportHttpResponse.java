package com.linkforge.shortlink.interfaces.web.dto;

import java.util.List;

public record ImportHttpResponse(int success, int failed, List<String> errors) {
}

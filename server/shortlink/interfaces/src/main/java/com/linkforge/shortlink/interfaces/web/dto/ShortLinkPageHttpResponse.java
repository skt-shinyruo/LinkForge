package com.linkforge.shortlink.interfaces.web.dto;

import java.util.List;

public record ShortLinkPageHttpResponse<T>(List<T> items, long total, int page, int size) {
}

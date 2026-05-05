package com.linkforge.analytics.interfaces.web;

import java.time.LocalDate;

public record DailyStatHttpResponse(LocalDate day, long pv, long uv) {
}

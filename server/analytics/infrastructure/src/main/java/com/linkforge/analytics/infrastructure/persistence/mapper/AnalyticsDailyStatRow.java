package com.linkforge.analytics.infrastructure.persistence.mapper;

import java.time.LocalDate;

public class AnalyticsDailyStatRow {

    private LocalDate day;
    private Long pv;
    private Long uv;

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public Long getPv() {
        return pv;
    }

    public void setPv(Long pv) {
        this.pv = pv;
    }

    public Long getUv() {
        return uv;
    }

    public void setUv(Long uv) {
        this.uv = uv;
    }
}


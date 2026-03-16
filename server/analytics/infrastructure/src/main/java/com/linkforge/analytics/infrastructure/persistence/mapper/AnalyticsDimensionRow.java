package com.linkforge.analytics.infrastructure.persistence.mapper;

public class AnalyticsDimensionRow {

    private String value;
    private Long pv;
    private Long uv;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
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


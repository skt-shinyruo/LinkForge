package com.linkforge.shortlink.infrastructure.persistence.mapper;

public class LinkCacheOutboxPendingRow {

    private String code;
    private Integer attempts;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }
}


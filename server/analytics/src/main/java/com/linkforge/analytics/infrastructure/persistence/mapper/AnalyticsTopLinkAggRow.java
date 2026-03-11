package com.linkforge.analytics.infrastructure.persistence.mapper;

public class AnalyticsTopLinkAggRow {

    private Long linkId;
    private Long pv;
    private Long uv;

    public Long getLinkId() {
        return linkId;
    }

    public void setLinkId(Long linkId) {
        this.linkId = linkId;
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


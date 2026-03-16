package com.linkforge.shortlink.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class LinkTagId implements Serializable {

    private Long linkId;

    private Long tagId;

    public LinkTagId() {
    }

    public LinkTagId(Long linkId, Long tagId) {
        this.linkId = linkId;
        this.tagId = tagId;
    }

    public Long getLinkId() {
        return linkId;
    }

    public void setLinkId(Long linkId) {
        this.linkId = linkId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LinkTagId that)) {
            return false;
        }
        return Objects.equals(linkId, that.linkId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linkId, tagId);
    }
}

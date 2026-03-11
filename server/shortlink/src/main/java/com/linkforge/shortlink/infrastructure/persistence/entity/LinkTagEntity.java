package com.linkforge.shortlink.infrastructure.persistence.entity;

public class LinkTagEntity {

    private LinkTagId id;

    public LinkTagEntity() {
    }

    public LinkTagEntity(LinkTagId id) {
        this.id = id;
    }

    public LinkTagId getId() {
        return id;
    }

    public void setId(LinkTagId id) {
        this.id = id;
    }
}

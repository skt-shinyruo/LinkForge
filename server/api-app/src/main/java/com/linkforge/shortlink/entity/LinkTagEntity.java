package com.linkforge.shortlink.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "link_tags")
public class LinkTagEntity {

    @EmbeddedId
    private LinkTagId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("linkId")
    @JoinColumn(name = "link_id")
    private ShortLinkEntity link;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id")
    private TagEntity tag;

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

    public ShortLinkEntity getLink() {
        return link;
    }

    public void setLink(ShortLinkEntity link) {
        this.link = link;
    }

    public TagEntity getTag() {
        return tag;
    }

    public void setTag(TagEntity tag) {
        this.tag = tag;
    }
}


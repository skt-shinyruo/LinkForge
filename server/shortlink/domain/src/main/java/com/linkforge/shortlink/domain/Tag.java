package com.linkforge.shortlink.domain;

public record Tag(String name) {

    public Tag {
        name = new LinkTagPolicy().normalizeName(name);
    }

    public static Tag of(String raw) {
        return new Tag(raw);
    }
}

package com.linkforge.shortlink.application;

public record ScopedCreateLinkRequest(
        CreateLinkRequest createRequest,
        Long pathApplicationId
) {
}

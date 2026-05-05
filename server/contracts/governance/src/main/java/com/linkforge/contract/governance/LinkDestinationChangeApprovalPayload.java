package com.linkforge.contract.governance;

public record LinkDestinationChangeApprovalPayload(
        String type,
        int version,
        long linkId,
        String originalUrl
) {

    public static LinkDestinationChangeApprovalPayload v1(long linkId, String originalUrl) {
        return new LinkDestinationChangeApprovalPayload(
                ApprovalPayloadTypes.LINK_DESTINATION_CHANGE,
                ApprovalPayloadTypes.VERSION_1,
                linkId,
                originalUrl
        );
    }
}

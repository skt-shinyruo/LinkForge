package com.linkforge.foundation.web;

public final class RequestId {

    private static final ThreadLocal<String> TL = new ThreadLocal<>();

    private RequestId() {
    }

    public static String get() {
        return TL.get();
    }

    static void set(String requestId) {
        TL.set(requestId);
    }

    static void clear() {
        TL.remove();
    }
}

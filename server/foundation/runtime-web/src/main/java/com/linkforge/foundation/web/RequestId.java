package com.linkforge.foundation.web;

public final class RequestId {

    private static final ThreadLocal<String> TL = new ThreadLocal<>();

    private RequestId() {
    }

    public static String get() {
        return TL.get();
    }

    public static void set(String requestId) {
        TL.set(requestId);
    }

    public static void clear() {
        TL.remove();
    }
}

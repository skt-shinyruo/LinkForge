package com.linkforge.shortlink.application.port;

public interface RedirectCacheSyncPort {

    void evict(String code);
}

package com.linkforge.platform.application.port;

import com.linkforge.platform.domain.Application;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository {

    void insert(Application application);

    Optional<Application> findByTenantIdAndId(long tenantId, long applicationId);

    Optional<Application> findByTenantIdAndApplicationKey(long tenantId, String applicationKey);

    List<Application> listByTenantId(long tenantId);

    List<Application> listAll();
}

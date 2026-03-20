package com.linkforge.platform.application.port;

import com.linkforge.platform.domain.ApplicationQuota;

import java.util.Optional;

public interface ApplicationQuotaRepository {

    void insert(ApplicationQuota quota);

    Optional<ApplicationQuota> findByApplicationId(long applicationId);
}

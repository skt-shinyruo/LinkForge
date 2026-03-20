package com.linkforge.platform.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.platform.application.ApplicationProvisioningService;
import com.linkforge.platform.application.PlatformControlPlaneService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformAdminController {

    private final PlatformControlPlaneService platformControlPlaneService;

    public PlatformAdminController(PlatformControlPlaneService platformControlPlaneService) {
        this.platformControlPlaneService = platformControlPlaneService;
    }

    @GetMapping("/applications")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<ApplicationProvisioningService.ApplicationDto>> listApplications() {
        return ApiResponse.ok(platformControlPlaneService.listAllApplications(), RequestId.get());
    }

    @GetMapping("/domains")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<ApplicationProvisioningService.DomainDto>> listDomains() {
        return ApiResponse.ok(platformControlPlaneService.listAllDomains(), RequestId.get());
    }
}

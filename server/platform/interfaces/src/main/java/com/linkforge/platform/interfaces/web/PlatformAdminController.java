package com.linkforge.platform.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.platform.application.PlatformControlPlaneService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台管理员的全局只读控制面入口。
 *
 * <p>这里允许跨租户查看应用和域名，因此每个端点都显式要求
 * {@code PLATFORM_ADMIN}。接口只做响应映射，不在 Web 层重新解释领域状态。</p>
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformAdminController {

    private final PlatformControlPlaneService platformControlPlaneService;

    public PlatformAdminController(PlatformControlPlaneService platformControlPlaneService) {
        this.platformControlPlaneService = platformControlPlaneService;
    }

    /** 返回所有租户的应用快照，不执行租户过滤。 */
    @GetMapping("/applications")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<ApplicationHttpResponse>> listApplications() {
        return ApiResponse.ok(
                platformControlPlaneService.listAllApplications().stream()
                        .map(PlatformHttpMapper::toApplicationResponse)
                        .toList(),
                RequestId.get()
        );
    }

    /** 返回所有租户的域名授权快照，不执行租户过滤。 */
    @GetMapping("/domains")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<DomainHttpResponse>> listDomains() {
        return ApiResponse.ok(
                platformControlPlaneService.listAllDomains().stream()
                        .map(PlatformHttpMapper::toDomainResponse)
                        .toList(),
                RequestId.get()
        );
    }
}

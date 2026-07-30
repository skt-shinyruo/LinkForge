package com.linkforge.accounts.interfaces.web;

import com.linkforge.accounts.application.ApiKeyInfoResult;
import com.linkforge.accounts.application.ApiKeyService;
import com.linkforge.accounts.application.CreatedApiKeyResult;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.web.RequestId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户管理员管理 OpenAPI API Key 的 HTTP 边界。
 *
 * <p>所有操作同时要求已认证 JWT 主体和 {@code TENANT_ADMIN} 角色。tenantId 只取自
 * {@link AuthContext}，绝不接受 path、query 或 body 指定；请求中的 {@code applicationId} 只是目标资源，
 * 应用层仍须校验其归属当前租户。</p>
 *
 * <p>API Key 明文只在创建和轮换成功的本次响应中返回，列表、启用和停用响应均不包含密钥。
 * 启用/停用使用 PUT 表达目标状态，调用方可以按状态操作语义重试。</p>
 */
@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;

    public ApiKeyAdminController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * 创建绑定到当前租户应用的 API Key；返回的 {@code apiKey} 是唯一一次明文交付。
     */
    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<CreateApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest req) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        CreatedApiKeyResult created = apiKeyService.create(tenantId, req.applicationId(), req.name());
        return ApiResponse.ok(new CreateApiKeyResponse(created.id(), created.name(), created.apiKey()), RequestId.get());
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<ApiKeyDto>> list(@RequestParam(required = false) Long applicationId) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        List<ApiKeyDto> dto = apiKeyService.list(tenantId, applicationId).stream()
                .map(e -> new ApiKeyDto(e.id(), e.applicationId(), e.name(), e.status(), e.lastUsedAt(), e.createdAt()))
                .toList();
        return ApiResponse.ok(dto, RequestId.get());
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ApiKeyDto> disable(@PathVariable("id") long id) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        ApiKeyInfoResult e = apiKeyService.disable(tenantId, id);
        return ApiResponse.ok(new ApiKeyDto(e.id(), e.applicationId(), e.name(), e.status(), e.lastUsedAt(), e.createdAt()), RequestId.get());
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ApiKeyDto> enable(@PathVariable("id") long id) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        ApiKeyInfoResult e = apiKeyService.enable(tenantId, id);
        return ApiResponse.ok(new ApiKeyDto(e.id(), e.applicationId(), e.name(), e.status(), e.lastUsedAt(), e.createdAt()), RequestId.get());
    }

    /**
     * 轮换当前租户的 API Key；旧密钥失效，新密钥仅在本次响应中明文返回。
     */
    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<CreateApiKeyResponse> rotate(@PathVariable("id") long id) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        CreatedApiKeyResult created = apiKeyService.rotate(tenantId, id);
        return ApiResponse.ok(new CreateApiKeyResponse(created.id(), created.name(), created.apiKey()), RequestId.get());
    }

    public record CreateApiKeyRequest(
            @jakarta.validation.constraints.NotNull(message = "applicationId 不能为空")
            Long applicationId,
            @NotBlank(message = "名称不能为空")
            @Size(max = 128, message = "名称过长")
            String name
    ) {
    }

    public record CreateApiKeyResponse(long id, String name, String apiKey) {
    }

    /**
     * API Key 的管理视图。
     *
     * <p>{@code createdAt} 与 {@code lastUsedAt} 使用 UTC 语义，但 {@link LocalDateTime} JSON 不携带偏移量；
     * 客户端应按 UTC 解释。为抑制高 QPS 写放大，{@code lastUsedAt} 按配置节流更新，是最近使用时间的
     * 近似值而非每次请求的精确审计时间，且从未使用时可为 {@code null}。</p>
     */
    public record ApiKeyDto(long id, Long applicationId, String name, String status, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
    }
}

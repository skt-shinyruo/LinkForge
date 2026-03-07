package com.linkforge.accounts.interfaces.web;

import com.linkforge.accounts.application.ApiKeyService;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.security.AuthContext;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;

    public ApiKeyAdminController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<CreateApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest req) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        ApiKeyService.CreatedApiKey created = apiKeyService.create(tenantId, req.name());
        return ApiResponse.ok(new CreateApiKeyResponse(created.id(), created.name(), created.apiKey()), RequestId.get());
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<ApiKeyDto>> list() {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        List<ApiKeyDto> dto = apiKeyService.list(tenantId).stream()
                .map(e -> new ApiKeyDto(e.id(), e.name(), e.status(), e.lastUsedAt(), e.createdAt()))
                .toList();
        return ApiResponse.ok(dto, RequestId.get());
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ApiKeyDto> disable(@PathVariable("id") long id) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        ApiKeyService.ApiKeyInfo e = apiKeyService.disable(tenantId, id);
        return ApiResponse.ok(new ApiKeyDto(e.id(), e.name(), e.status(), e.lastUsedAt(), e.createdAt()), RequestId.get());
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ApiKeyDto> enable(@PathVariable("id") long id) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        ApiKeyService.ApiKeyInfo e = apiKeyService.enable(tenantId, id);
        return ApiResponse.ok(new ApiKeyDto(e.id(), e.name(), e.status(), e.lastUsedAt(), e.createdAt()), RequestId.get());
    }

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<CreateApiKeyResponse> rotate(@PathVariable("id") long id) {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        ApiKeyService.CreatedApiKey created = apiKeyService.rotate(tenantId, id);
        return ApiResponse.ok(new CreateApiKeyResponse(created.id(), created.name(), created.apiKey()), RequestId.get());
    }

    public record CreateApiKeyRequest(
            @NotBlank(message = "名称不能为空")
            @Size(max = 128, message = "名称过长")
            String name
    ) {
    }

    public record CreateApiKeyResponse(long id, String name, String apiKey) {
    }

    public record ApiKeyDto(long id, String name, String status, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
    }
}

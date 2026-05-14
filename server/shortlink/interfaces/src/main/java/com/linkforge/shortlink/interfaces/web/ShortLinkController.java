package com.linkforge.shortlink.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.runtime.security.PrincipalActorMapper;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.shortlink.application.BrowseLinksRequest;
import com.linkforge.shortlink.application.ImportResult;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.ScopedCreateLinkRequest;
import com.linkforge.shortlink.application.ScopedImportCsvRequest;
import com.linkforge.shortlink.application.ShortLinkCreationUseCase;
import com.linkforge.shortlink.application.ShortLinkCsvUseCase;
import com.linkforge.shortlink.application.ShortLinkLifecycleUseCase;
import com.linkforge.shortlink.application.ShortLinkQueryUseCase;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExport;
import com.linkforge.shortlink.interfaces.web.dto.ImportHttpResponse;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkCreateHttpRequest;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkHttpResponse;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkPageHttpResponse;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkUpdateHttpRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1")
public class ShortLinkController {

    private final ShortLinkCreationUseCase shortLinkCreationUseCase;
    private final ShortLinkQueryUseCase shortLinkQueryUseCase;
    private final ShortLinkLifecycleUseCase shortLinkLifecycleUseCase;
    private final ShortLinkCsvUseCase shortLinkCsvUseCase;
    private final ShortLinkWriteGuard writeGuard;
    private final PrincipalActorMapper principalActorMapper;
    private final ShortLinkCsvHttpMapper shortLinkCsvHttpMapper;

    public ShortLinkController(
            ShortLinkCreationUseCase shortLinkCreationUseCase,
            ShortLinkQueryUseCase shortLinkQueryUseCase,
            ShortLinkLifecycleUseCase shortLinkLifecycleUseCase,
            ShortLinkCsvUseCase shortLinkCsvUseCase,
            ShortLinkWriteGuard writeGuard,
            PrincipalActorMapper principalActorMapper,
            ShortLinkCsvHttpMapper shortLinkCsvHttpMapper
    ) {
        this.shortLinkCreationUseCase = shortLinkCreationUseCase;
        this.shortLinkQueryUseCase = shortLinkQueryUseCase;
        this.shortLinkLifecycleUseCase = shortLinkLifecycleUseCase;
        this.shortLinkCsvUseCase = shortLinkCsvUseCase;
        this.writeGuard = writeGuard;
        this.principalActorMapper = principalActorMapper;
        this.shortLinkCsvHttpMapper = shortLinkCsvHttpMapper;
    }

    @PostMapping("/links")
    public ApiResponse<ShortLinkHttpResponse> create(@Valid @RequestBody ShortLinkCreateHttpRequest req) {
        writeGuard.requireWriteEnabled();
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        LinkDto dto = shortLinkCreationUseCase.createForUser(
                actor,
                new ScopedCreateLinkRequest(ShortLinkHttpMapper.toCreateRequest(req), null)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toLinkResponse(dto), RequestId.get());
    }

    @PostMapping("/applications/{applicationId}/links")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ShortLinkHttpResponse> createForApplication(
            @PathVariable("applicationId") long applicationId,
            @Valid @RequestBody ShortLinkCreateHttpRequest req
    ) {
        writeGuard.requireWriteEnabled();
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        LinkDto dto = shortLinkCreationUseCase.createForUser(
                actor,
                new ScopedCreateLinkRequest(ShortLinkHttpMapper.toCreateRequest(req), applicationId)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toLinkResponse(dto), RequestId.get());
    }

    @GetMapping("/links")
    public ApiResponse<ShortLinkPageHttpResponse<ShortLinkHttpResponse>> list(
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        PageResult<LinkDto> result = shortLinkQueryUseCase.browseForUser(
                actor,
                new BrowseLinksRequest(archived, enabled, keyword, tag, applicationId, null, page, size, 100)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toPageResponse(result), RequestId.get());
    }

    @GetMapping("/applications/{applicationId}/links")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ShortLinkPageHttpResponse<ShortLinkHttpResponse>> listByApplication(
            @PathVariable("applicationId") long applicationId,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        PageResult<LinkDto> result = shortLinkQueryUseCase.browseForUser(
                actor,
                new BrowseLinksRequest(archived, enabled, keyword, tag, null, applicationId, page, size, 100)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toPageResponse(result), RequestId.get());
    }

    @GetMapping("/links/{id}")
    public ApiResponse<ShortLinkHttpResponse> detail(@PathVariable("id") long id) {
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        return ApiResponse.ok(
                ShortLinkHttpMapper.toLinkResponse(shortLinkQueryUseCase.detailForUser(actor, id)),
                RequestId.get()
        );
    }

    @PutMapping("/links/{id}")
    public ApiResponse<ShortLinkHttpResponse> update(@PathVariable("id") long id, @Valid @RequestBody ShortLinkUpdateHttpRequest req) {
        writeGuard.requireWriteEnabled();
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        return ApiResponse.ok(
                ShortLinkHttpMapper.toLinkResponse(
                        shortLinkLifecycleUseCase.update(
                                actor.tenantId(),
                                id,
                                ShortLinkHttpMapper.toUpdateRequest(req),
                                actor,
                                LocalDateTime.now(ZoneOffset.UTC)
                        )
                ),
                RequestId.get()
        );
    }

    @PostMapping("/links/{id}/archive")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ShortLinkHttpResponse> archive(@PathVariable("id") long id) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(
                ShortLinkHttpMapper.toLinkResponse(shortLinkLifecycleUseCase.archive(p.getTenantId(), id)),
                RequestId.get()
        );
    }

    @PostMapping("/links/{id}/restore")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ShortLinkHttpResponse> restore(@PathVariable("id") long id) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(
                ShortLinkHttpMapper.toLinkResponse(shortLinkLifecycleUseCase.restore(p.getTenantId(), id)),
                RequestId.get()
        );
    }

    @DeleteMapping("/links/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        shortLinkLifecycleUseCase.delete(p.getTenantId(), id);
        return ApiResponse.ok(null, RequestId.get());
    }

    @PostMapping(value = "/links/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ImportHttpResponse> importCsv(@RequestParam("file") MultipartFile file) {
        writeGuard.requireWriteEnabled();
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        ImportResult result = shortLinkCsvUseCase.importCsv(actor, shortLinkCsvHttpMapper.parse(file));
        return ApiResponse.ok(ShortLinkHttpMapper.toImportResponse(result), RequestId.get());
    }

    @PostMapping(value = "/applications/{applicationId}/links/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ImportHttpResponse> importCsvByApplication(
            @PathVariable("applicationId") long applicationId,
            @RequestParam("domainId") long domainId,
            @RequestParam("file") MultipartFile file
    ) {
        writeGuard.requireWriteEnabled();
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        ImportResult result = shortLinkCsvUseCase.importCsv(
                actor,
                new ScopedImportCsvRequest(shortLinkCsvHttpMapper.parse(file), applicationId, domainId)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toImportResponse(result), RequestId.get());
    }

    @GetMapping("/links/export")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public void exportCsv(
            jakarta.servlet.http.HttpServletResponse response,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) {
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        ShortLinkCsvExport export = shortLinkCsvUseCase.exportCsvForUser(
                actor,
                new BrowseLinksRequest(archived, enabled, keyword, tag, applicationId, null, page, size, 1000)
        );
        shortLinkCsvHttpMapper.write(export, response);
    }

    @GetMapping("/applications/{applicationId}/links/export")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public void exportCsvByApplication(
            @PathVariable("applicationId") long applicationId,
            jakarta.servlet.http.HttpServletResponse response,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) {
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        ShortLinkCsvExport export = shortLinkCsvUseCase.exportCsvForUser(
                actor,
                new BrowseLinksRequest(archived, enabled, keyword, tag, null, applicationId, page, size, 1000)
        );
        shortLinkCsvHttpMapper.write(export, response);
    }
}

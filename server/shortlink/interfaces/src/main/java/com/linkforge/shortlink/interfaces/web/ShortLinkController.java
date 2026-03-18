package com.linkforge.shortlink.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.foundation.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.shortlink.application.ShortLinkService;
import com.linkforge.shortlink.application.ShortLinkService.CreatedBy;
import com.linkforge.shortlink.application.ShortLinkService.ImportResult;
import com.linkforge.shortlink.application.ShortLinkService.LinkDto;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkCreateHttpRequest;
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

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/links")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;
    private final ShortLinkWriteGuard writeGuard;

    public ShortLinkController(ShortLinkService shortLinkService, ShortLinkWriteGuard writeGuard) {
        this.shortLinkService = shortLinkService;
        this.writeGuard = writeGuard;
    }

    @PostMapping
    public ApiResponse<LinkDto> create(@Valid @RequestBody ShortLinkCreateHttpRequest req) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        CreatedBy createdBy = CreatedBy.user(p.getUserId());
        LinkDto dto = shortLinkService.create(
                p.getTenantId(),
                createdBy,
                ShortLinkHttpMapper.toCreateRequest(req)
        );
        return ApiResponse.ok(dto, RequestId.get());
    }

    @GetMapping
    public ApiResponse<ShortLinkPageHttpResponse<LinkDto>> list(
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        boolean archivedFlag = archived != null && archived;
        PageResult<LinkDto> result = shortLinkService.search(
                p.getTenantId(),
                new ShortLinkSearchQuery(archivedFlag, enabled, keyword, tag),
                PageQuery.of(page, size, 100)
        );
        return ApiResponse.ok(ShortLinkHttpMapper.toPageResponse(result), RequestId.get());
    }

    @GetMapping("/{id}")
    public ApiResponse<LinkDto> detail(@PathVariable("id") long id) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(shortLinkService.detail(p.getTenantId(), id), RequestId.get());
    }

    @PutMapping("/{id}")
    public ApiResponse<LinkDto> update(@PathVariable("id") long id, @Valid @RequestBody ShortLinkUpdateHttpRequest req) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(
                shortLinkService.update(
                        p.getTenantId(),
                        id,
                        ShortLinkHttpMapper.toUpdateRequest(req)
                ),
                RequestId.get()
        );
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<LinkDto> archive(@PathVariable("id") long id) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(shortLinkService.archive(p.getTenantId(), id), RequestId.get());
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<LinkDto> restore(@PathVariable("id") long id) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(shortLinkService.restore(p.getTenantId(), id), RequestId.get());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        shortLinkService.delete(p.getTenantId(), id);
        return ApiResponse.ok(null, RequestId.get());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ImportResult> importCsv(@RequestParam("file") MultipartFile file) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        ImportResult r;
        try {
            r = shortLinkService.importCsv(p.getTenantId(), CreatedBy.user(p.getUserId()), file.getInputStream());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件读取失败");
        }
        return ApiResponse.ok(r, RequestId.get());
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public void exportCsv(
            jakarta.servlet.http.HttpServletResponse response,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) throws IOException {
        AuthPrincipal p = AuthContext.requirePrincipal();

        response.setHeader(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"links.csv\"");
        shortLinkService.exportCsv(p.getTenantId(), PageQuery.of(page, size, 1000), response.getOutputStream());
    }

}

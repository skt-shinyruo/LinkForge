package com.linkforge.shortlink.web;

import com.linkforge.platform.api.ApiResponse;
import com.linkforge.platform.api.BusinessException;
import com.linkforge.platform.api.ErrorCode;
import com.linkforge.platform.security.AuthContext;
import com.linkforge.platform.security.AuthPrincipal;
import com.linkforge.platform.web.RequestId;
import com.linkforge.shortlink.service.ShortLinkService;
import com.linkforge.shortlink.service.ShortLinkService.ImportResult;
import com.linkforge.shortlink.service.ShortLinkService.LinkDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    public ShortLinkController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @PostMapping
    public ApiResponse<LinkDto> create(@Valid @RequestBody CreateLinkRequest req) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        LinkDto dto = shortLinkService.create(
                p.getTenantId(),
                p.getUserId(),
                new ShortLinkService.CreateLinkRequest(
                        req.originalUrl(),
                        req.note(),
                        req.expiresAt(),
                        req.enabled(),
                        req.customCode(),
                        req.tags(),
                        req.redirectStatusCode(),
                        req.previewEnabled(),
                        req.unavailableLandingUrl(),
                        req.queryForwardMode(),
                        req.queryForwardAllowlist()
                )
        );
        return ApiResponse.ok(dto, RequestId.get());
    }

    @GetMapping
    public ApiResponse<PageResponse<LinkDto>> list(
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        boolean archivedFlag = archived != null && archived;
        Page<LinkDto> r = shortLinkService.search(p.getTenantId(), archivedFlag, enabled, keyword, tag, pageable);
        return ApiResponse.ok(new PageResponse<>(r.getContent(), r.getTotalElements(), r.getNumber(), r.getSize()), RequestId.get());
    }

    @GetMapping("/{id}")
    public ApiResponse<LinkDto> detail(@PathVariable("id") long id) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(shortLinkService.detail(p.getTenantId(), id), RequestId.get());
    }

    @PutMapping("/{id}")
    public ApiResponse<LinkDto> update(@PathVariable("id") long id, @Valid @RequestBody UpdateLinkRequest req) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(
                shortLinkService.update(
                        p.getTenantId(),
                        id,
                        new ShortLinkService.UpdateLinkRequest(
                                req.originalUrl(),
                                req.note(),
                                req.expiresAt(),
                                req.clearExpiresAt(),
                                req.enabled(),
                                req.tags(),
                                req.redirectStatusCode(),
                                req.clearRedirectStatusCode(),
                                req.previewEnabled(),
                                req.unavailableLandingUrl(),
                                req.queryForwardMode(),
                                req.clearQueryForwardMode(),
                                req.queryForwardAllowlist()
                        )
                ),
                RequestId.get()
        );
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<LinkDto> archive(@PathVariable("id") long id) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(shortLinkService.archive(p.getTenantId(), id), RequestId.get());
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<LinkDto> restore(@PathVariable("id") long id) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(shortLinkService.restore(p.getTenantId(), id), RequestId.get());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        shortLinkService.delete(p.getTenantId(), id);
        return ApiResponse.ok(null, RequestId.get());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<ImportResult> importCsv(@RequestParam("file") MultipartFile file) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        ImportResult r;
        try {
            r = shortLinkService.importCsv(p.getTenantId(), p.getUserId(), file.getInputStream());
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
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 1000), Sort.by(Sort.Direction.DESC, "createdAt"));

        response.setHeader(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"links.csv\"");
        shortLinkService.exportCsv(p.getTenantId(), pageable, response.getOutputStream());
    }

    public record PageResponse<T>(java.util.List<T> items, long total, int page, int size) {
    }

    public record CreateLinkRequest(
            @NotBlank(message = "originalUrl 不能为空")
            @Size(max = 2048, message = "URL 过长")
            String originalUrl,
            @Size(max = 512, message = "备注过长")
            String note,
            java.time.LocalDateTime expiresAt,
            Boolean enabled,
            @Size(max = 32, message = "自定义短码过长")
            String customCode,
            java.util.Set<String> tags,
            Integer redirectStatusCode,
            Boolean previewEnabled,
            @Size(max = 2048, message = "落地页 URL 过长")
            String unavailableLandingUrl,
            @Size(max = 16, message = "queryForwardMode 过长")
            String queryForwardMode,
            java.util.List<@Size(max = 64, message = "queryForwardAllowlist 项过长") String> queryForwardAllowlist
    ) {
    }

    public record UpdateLinkRequest(
            @Size(max = 2048, message = "URL 过长")
            String originalUrl,
            @Size(max = 512, message = "备注过长")
            String note,
            java.time.LocalDateTime expiresAt,
            Boolean clearExpiresAt,
            Boolean enabled,
            java.util.Set<String> tags,
            Integer redirectStatusCode,
            Boolean clearRedirectStatusCode,
            Boolean previewEnabled,
            @Size(max = 2048, message = "落地页 URL 过长")
            String unavailableLandingUrl,
            @Size(max = 16, message = "queryForwardMode 过长")
            String queryForwardMode,
            Boolean clearQueryForwardMode,
            java.util.List<@Size(max = 64, message = "queryForwardAllowlist 项过长") String> queryForwardAllowlist
    ) {
    }
}

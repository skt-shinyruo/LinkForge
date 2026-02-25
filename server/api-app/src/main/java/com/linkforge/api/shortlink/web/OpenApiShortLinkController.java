package com.linkforge.api.shortlink.web;

import com.linkforge.platform.api.ApiResponse;
import com.linkforge.api.security.AuthContext;
import com.linkforge.api.security.AuthPrincipal;
import com.linkforge.platform.web.RequestId;
import com.linkforge.api.shortlink.service.ShortLinkService;
import com.linkforge.api.shortlink.service.ShortLinkService.LinkDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/open/links")
public class OpenApiShortLinkController {

    private final ShortLinkService shortLinkService;

    public OpenApiShortLinkController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @PostMapping
    public ApiResponse<LinkDto> create(@Valid @RequestBody CreateLinkRequest req) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        LinkDto dto = shortLinkService.create(
                p.getTenantId(),
                0L,
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
    public ApiResponse<ShortLinkController.PageResponse<LinkDto>> list(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LinkDto> r = shortLinkService.search(p.getTenantId(), false, enabled, keyword, null, pageable);
        return ApiResponse.ok(new ShortLinkController.PageResponse<>(r.getContent(), r.getTotalElements(), r.getNumber(), r.getSize()), RequestId.get());
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
}

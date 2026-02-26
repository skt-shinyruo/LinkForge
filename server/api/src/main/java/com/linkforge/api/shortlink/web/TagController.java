package com.linkforge.api.shortlink.web;

import com.linkforge.platform.api.ApiResponse;
import com.linkforge.api.security.AuthContext;
import com.linkforge.api.security.AuthPrincipal;
import com.linkforge.platform.web.RequestId;
import com.linkforge.api.shortlink.service.ShortLinkService;
import com.linkforge.api.shortlink.service.ShortLinkService.TagDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final ShortLinkService shortLinkService;

    public TagController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<TagDto>> list() {
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(shortLinkService.listTags(p.getTenantId()), RequestId.get());
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<TagDto> create(@Valid @RequestBody CreateTagRequest req) {
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(shortLinkService.createTag(p.getTenantId(), req.name()), RequestId.get());
    }

    public record CreateTagRequest(
            @NotBlank(message = "标签名不能为空")
            @Size(max = 64, message = "标签名过长")
            String name
    ) {
    }
}

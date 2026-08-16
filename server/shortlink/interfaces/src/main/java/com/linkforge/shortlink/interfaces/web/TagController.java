package com.linkforge.shortlink.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.shortlink.application.ShortLinkApplicationService;
import com.linkforge.shortlink.application.TagDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final ShortLinkApplicationService shortLinkService;
    private final ShortLinkWriteGuard writeGuard;

    public TagController(ShortLinkApplicationService shortLinkService, ShortLinkWriteGuard writeGuard) {
        this.shortLinkService = shortLinkService;
        this.writeGuard = writeGuard;
    }

    @GetMapping
    public ApiResponse<List<TagDto>> list() {
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(shortLinkService.listTags(p.getTenantId()), RequestId.get());
    }

    @PostMapping
    public ApiResponse<TagDto> create(@Valid @RequestBody CreateTagRequest req) {
        writeGuard.requireWriteEnabled();
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(
                shortLinkService.createTag(p.getTenantId(), req.name()),
                RequestId.get()
        );
    }

    public record CreateTagRequest(
            @NotBlank(message = "标签名不能为空")
            @Size(max = 64, message = "标签名过长")
            String name
    ) {
    }
}

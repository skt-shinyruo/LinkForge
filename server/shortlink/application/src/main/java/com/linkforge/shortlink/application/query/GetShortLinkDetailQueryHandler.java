package com.linkforge.shortlink.application.query;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.ShortLinkUserAccess;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询短链详情并在授权完成后补充标签。
 *
 * <p>两种入口都先以 {@code tenantId + linkId} 查找聚合，因此不存在跨租户 ID 回退。接收
 * {@link UserActor} 的入口还会执行用户可见性策略：租户管理员可查看租户内短链，普通用户只能查看自己创建的
 * 无应用 scope 短链；无权访问与不存在统一表现为 {@code LINK_NOT_FOUND}，避免泄露资源存在性。标签关联表没有
 * tenant 列，所以标签读取严格位于聚合租户校验和用户授权之后。</p>
 *
 * <p>只接收 {@code tenantId} 的入口不执行角色校验，供已经在更外层建立可信租户边界的内部用例使用，不能直接
 * 作为面向用户的授权入口。</p>
 */
@Component
public class GetShortLinkDetailQueryHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkDtoMapper dtoMapper;

    public GetShortLinkDetailQueryHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository,
            ShortLinkDtoMapper dtoMapper
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
        this.dtoMapper = dtoMapper;
    }

    /**
     * 在可信调用方提供的租户范围内查询详情；本重载不验证用户角色或资源所有者。
     */
    public LinkDto handle(long tenantId, long linkId) {
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));
        List<String> tags = linkTagRepository.findTagNamesByLinkId(linkId);
        return dtoMapper.toDto(link, tags);
    }

    /**
     * 按 actor 租户查询并应用普通用户/租户管理员可见性规则，再读取标签和映射 DTO。
     *
     * @throws BusinessException 短链不存在或 actor 无权访问时均抛出 {@code LINK_NOT_FOUND}
     */
    public LinkDto handle(UserActor actor, long linkId) {
        ShortLink link = shortLinkRepository.findByTenantIdAndId(actor.tenantId(), linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));
        ShortLinkUserAccess.requireCanAccess(actor, link);
        List<String> tags = linkTagRepository.findTagNamesByLinkId(linkId);
        return dtoMapper.toDto(link, tags);
    }
}

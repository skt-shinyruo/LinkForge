package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.shortlink.application.TagDto;
import com.linkforge.shortlink.application.port.TagRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在租户内创建或取得同名标签。
 *
 * <p>标签名经过去空白和长度校验后，以租户内唯一约束作为最终并发仲裁。先查到已有标签时直接返回；
 * 两个事务并发插入时，失败方在捕获唯一约束异常后重新读取，因此同一租户、同一规范化名称的重复请求
 * 通常收敛到同一个标签。该处理器只接收租户 ID，不判断调用用户权限，上游必须先完成租户成员授权。</p>
 */
@Component
public class CreateTagCommandHandler {

    private final SnowflakeIdGenerator idGenerator;
    private final TagRepository tagRepository;

    public CreateTagCommandHandler(
            SnowflakeIdGenerator idGenerator,
            TagRepository tagRepository
    ) {
        this.idGenerator = idGenerator;
        this.tagRepository = tagRepository;
    }

    /**
     * 在当前事务中创建标签，或幂等地返回已经存在的同名标签。
     *
     * @param tenantId 标签所属租户，也是名称唯一性的作用域
     * @param name 标签名；首尾空白会被移除，空值和超过 64 个字符的值会被拒绝
     * @return 新建或并发/历史已存在的标签
     * @throws BusinessException 标签名不符合约束时抛出
     */
    @Transactional
    public TagDto handle(long tenantId, String name) {
        String n = normalizeNullable(name);
        if (n == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名不能为空");
        }
        if (n.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名过长");
        }

        TagRepository.Tag existing = tagRepository.findByTenantIdAndName(tenantId, n);
        if (existing != null) {
            return new TagDto(existing.id(), existing.name());
        }

        long id = idGenerator.nextId();
        TagRepository.Tag tag = new TagRepository.Tag(id, tenantId, n, null);
        try {
            tagRepository.insert(tag);
            return new TagDto(tag.id(), tag.name());
        } catch (DataIntegrityViolationException ex) {
            TagRepository.Tag raced = tagRepository.findByTenantIdAndName(tenantId, n);
            if (raced != null) {
                return new TagDto(raced.id(), raced.name());
            }
            throw ex;
        }
    }

    private static String normalizeNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}

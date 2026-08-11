package com.linkforge.shortlink.application.command;

import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.TagRepository;
import com.linkforge.shortlink.application.support.LinkTagSetNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 以全量替换语义维护短链与标签的关联。
 *
 * <p>关联删除、缺失标签创建和新关联写入位于同一事务；任一步骤失败都会回滚先前删除。
 * 单条短链最多保留 20 个规范化后的不同标签。并发创建同名标签由租户内唯一约束仲裁，
 * 但关联集合本身不做乐观锁，并发替换采用最后提交者生效的语义。该处理器不验证 {@code linkId}
 * 是否属于 {@code tenantId}，调用方必须先完成短链归属和权限校验。</p>
 */
@Component
public class SetLinkTagsCommandHandler {

    private final SnowflakeIdGenerator idGenerator;
    private final TagRepository tagRepository;
    private final LinkTagRepository linkTagRepository;

    public SetLinkTagsCommandHandler(
            SnowflakeIdGenerator idGenerator,
            TagRepository tagRepository,
            LinkTagRepository linkTagRepository
    ) {
        this.idGenerator = idGenerator;
        this.tagRepository = tagRepository;
        this.linkTagRepository = linkTagRepository;
    }

    /**
     * 将短链当前标签集合替换为请求集合。
     *
     * <p>{@code tags} 为 {@code null} 或空集合时会清空全部关联。重复执行相同集合时最终状态一致；
     * 标签创建发生竞争时会重新读取胜出的记录，只有无法读到竞争结果时才继续抛出数据库异常。</p>
     *
     * @param tenantId 新标签的租户作用域
     * @param linkId 要替换标签的短链 ID，归属必须由调用方保证
     * @param tags 目标标签名集合；空值表示清空，规范化后最多采用 20 个
     * @throws BusinessException 任一采用的标签名超过 64 个字符时抛出
     */
    @Transactional
    public void handle(long tenantId, long linkId, Set<String> tags) {
        linkTagRepository.deleteAllByLinkId(linkId);
        if (tags == null || tags.isEmpty()) {
            return;
        }

        Set<String> normalized = LinkTagSetNormalizer.normalize(tags);

        Map<String, TagRepository.Tag> existing = new HashMap<>();
        for (String name : normalized) {
            TagRepository.Tag t = tagRepository.findByTenantIdAndName(tenantId, name);
            if (t != null) {
                existing.put(name, t);
            }
        }

        for (String name : normalized) {
            TagRepository.Tag t = existing.get(name);
            if (t == null) {
                long id = idGenerator.nextId();
                TagRepository.Tag created = new TagRepository.Tag(id, tenantId, name, null);
                try {
                    tagRepository.insert(created);
                    t = created;
                } catch (DataIntegrityViolationException ex) {
                    // 并发创建由唯一约束仲裁，失败方复用胜出的标签。
                    TagRepository.Tag raced = tagRepository.findByTenantIdAndName(tenantId, name);
                    if (raced != null) {
                        t = raced;
                    } else {
                        throw ex;
                    }
                }
            }
            linkTagRepository.insert(linkId, t.id());
        }
    }

}

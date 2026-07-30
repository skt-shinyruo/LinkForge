package com.linkforge.shortlink.infrastructure.persistence.repository;

import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.infrastructure.persistence.mapper.LinkTagMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.LinkTagNameRow;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 短链与标签关联仓储的 MyBatis 适配器。
 *
 * <p>关联的增删通常由应用层在短链写事务中组合调用；本适配器只返回实际影响行数，
 * 不自行开启事务，也不把唯一键或外键冲突转换为幂等成功。</p>
 */
@Repository
public class MybatisLinkTagRepository implements LinkTagRepository {

    private final LinkTagMapper mapper;

    public MybatisLinkTagRepository(LinkTagMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(long linkId, long tagId) {
        return mapper.insert(linkId, tagId);
    }

    /**
     * 物理删除短链的全部标签关联，通常作为“整体替换标签”事务的第一步。
     *
     * <p>删除与随后逐项插入之间没有本地事务保护，调用方必须在同一事务内完成组合操作，
     * 否则中途失败可能留下空标签集合。</p>
     */
    @Override
    public int deleteAllByLinkId(long linkId) {
        return mapper.deleteAllByLinkId(linkId);
    }

    @Override
    public List<String> findTagNamesByLinkId(long linkId) {
        List<String> rows = mapper.findTagNamesByLinkId(linkId);
        return rows == null ? List.of() : rows;
    }

    /**
     * 批量读取短链的标签名称。
     *
     * <p>调用前会丢弃 {@code null} 和非正数 ID，避免生成无效的 {@code IN} 条件；返回结果按
     * mapper 的“短链 ID 升序、标签创建时间倒序”顺序保留，同一短链可对应多行。数据库返回的
     * 不完整行会被忽略，且空输入或空结果统一返回不可变空列表。关联表本身没有 tenant 列，因此
     * 调用方必须只传入已完成租户隔离和授权校验的 linkId。</p>
     */
    @Override
    public List<LinkTagName> findTagNamesByLinkIds(Collection<Long> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Long id : linkIds) {
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<LinkTagNameRow> rows = mapper.findTagNamesByLinkIds(ids);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<LinkTagName> out = new ArrayList<>(rows.size());
        for (LinkTagNameRow r : rows) {
            if (r == null || r.getLinkId() == null || r.getTagName() == null) {
                continue;
            }
            out.add(new LinkTagName(r.getLinkId(), r.getTagName()));
        }
        return out;
    }
}

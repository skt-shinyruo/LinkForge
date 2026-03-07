package com.linkforge.shortlink.infrastructure.persistence.repo;

import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLinkEntity, Long> {

    Optional<ShortLinkEntity> findByTenantIdAndId(Long tenantId, Long id);

    Optional<ShortLinkEntity> findByCode(String code);

    Optional<ShortLinkEntity> findByCodeAndArchivedAtIsNull(String code);

    @Query(
            value = """
                    select distinct l from ShortLinkEntity l
                    left join LinkTagEntity lt on lt.id.linkId = l.id
                    left join TagEntity t on t.id = lt.id.tagId
                    where l.tenantId = :tenantId
                      and ((:archived = false and l.archivedAt is null) or (:archived = true and l.archivedAt is not null))
                      and (:enabled is null or l.enabled = :enabled)
                      and (:keyword is null
                          or lower(l.code) like lower(concat('%', :keyword, '%'))
                          or lower(l.originalUrl) like lower(concat('%', :keyword, '%'))
                          or lower(l.note) like lower(concat('%', :keyword, '%')))
                      and (:tag is null or t.name = :tag)
                    order by l.createdAt desc
                    """,
            countQuery = """
                    select count(distinct l.id) from ShortLinkEntity l
                    left join LinkTagEntity lt on lt.id.linkId = l.id
                    left join TagEntity t on t.id = lt.id.tagId
                    where l.tenantId = :tenantId
                      and ((:archived = false and l.archivedAt is null) or (:archived = true and l.archivedAt is not null))
                      and (:enabled is null or l.enabled = :enabled)
                      and (:keyword is null
                          or lower(l.code) like lower(concat('%', :keyword, '%'))
                          or lower(l.originalUrl) like lower(concat('%', :keyword, '%'))
                          or lower(l.note) like lower(concat('%', :keyword, '%')))
                      and (:tag is null or t.name = :tag)
                    """
    )
    Page<ShortLinkEntity> search(
            @Param("tenantId") Long tenantId,
            @Param("archived") boolean archived,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("tag") String tag,
            Pageable pageable
    );
}

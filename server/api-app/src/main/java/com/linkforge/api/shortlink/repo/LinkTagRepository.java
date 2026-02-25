package com.linkforge.api.shortlink.repo;

import com.linkforge.api.shortlink.entity.LinkTagEntity;
import com.linkforge.api.shortlink.entity.LinkTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LinkTagRepository extends JpaRepository<LinkTagEntity, LinkTagId> {

    void deleteAllByIdLinkId(Long linkId);

    @Query("select lt from LinkTagEntity lt join fetch lt.tag where lt.id.linkId in :linkIds")
    List<LinkTagEntity> findAllByLinkIdsFetchTag(@Param("linkIds") Collection<Long> linkIds);

    @Query("select lt from LinkTagEntity lt join fetch lt.tag where lt.id.linkId = :linkId")
    List<LinkTagEntity> findAllByLinkIdFetchTag(@Param("linkId") Long linkId);
}


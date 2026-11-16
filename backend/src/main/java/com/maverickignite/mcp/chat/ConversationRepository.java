package com.maverickignite.mcp.chat;

import java.util.List;
import java.util.UUID;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    List<ConversationEntity> findAllByOrderByPinnedDescUpdatedAtDesc();

    List<ConversationEntity> findByWorkspaceIdOrderByPinnedDescUpdatedAtDesc(UUID workspaceId);

    List<ConversationEntity> findByTitleContainingIgnoreCaseOrderByPinnedDescUpdatedAtDesc(String q);

    /** Only replaces the placeholder title, so a user rename is never overwritten. */
    @Modifying
    @Transactional
    @Query("update ConversationEntity c set c.title = :title where c.id = :id and c.title = :placeholder")
    int replacePlaceholderTitle(@Param("id") UUID id, @Param("title") String title,
            @Param("placeholder") String placeholder);

    @Modifying
    @Transactional
    @Query("update ConversationEntity c set c.workspaceId = :workspaceId where c.workspaceId is null")
    int assignOrphansTo(@Param("workspaceId") UUID workspaceId);

    @Modifying
    @Transactional
    @Query("update ConversationEntity c set c.updatedAt = :now where c.id = :id")
    void touch(@Param("id") UUID id, @Param("now") Instant now);
}

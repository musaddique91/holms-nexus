package com.maverickignite.mcp.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    long countByConversationId(UUID conversationId);

    @Query("select distinct m.conversationId from ChatMessageEntity m where lower(m.content) like lower(concat('%', ?1, '%'))")
    List<UUID> findConversationIdsByContent(String q);
}

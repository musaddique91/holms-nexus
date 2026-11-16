package com.maverickignite.mcp.llm;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmProviderRepository extends JpaRepository<LlmProviderEntity, UUID> {

    List<LlmProviderEntity> findAllByOrderByCreatedAtAsc();
}

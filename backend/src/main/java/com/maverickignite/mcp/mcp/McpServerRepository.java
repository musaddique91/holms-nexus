package com.maverickignite.mcp.mcp;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface McpServerRepository extends JpaRepository<McpServerEntity, UUID> {

    List<McpServerEntity> findAllByOrderByCreatedAtAsc();

    List<McpServerEntity> findAllByEnabledTrue();

    boolean existsByNameIgnoreCase(String name);
}

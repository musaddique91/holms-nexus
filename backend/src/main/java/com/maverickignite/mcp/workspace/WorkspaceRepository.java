package com.maverickignite.mcp.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {

    List<WorkspaceEntity> findAllByOrderByDefaultWorkspaceDescCreatedAtAsc();

    Optional<WorkspaceEntity> findFirstByDefaultWorkspaceTrue();

    boolean existsByNameIgnoreCase(String name);
}

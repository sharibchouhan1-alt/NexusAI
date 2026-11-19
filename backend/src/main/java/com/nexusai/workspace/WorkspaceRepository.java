package com.nexusai.workspace;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the workspace module; other modules use {@link WorkspaceService}. */
interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
}

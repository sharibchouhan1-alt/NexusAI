package com.nexusai.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Internal to the document module. Every query takes the workspace id: there is
 * deliberately no unscoped lookup by document id alone.
 */
interface DocumentRepository extends Repository<Document, UUID> {

    Document saveAndFlush(Document document);

    Optional<Document> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<Document> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    void delete(Document document);
}

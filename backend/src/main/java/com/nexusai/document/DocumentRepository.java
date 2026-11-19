package com.nexusai.document;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Internal to the document module. Every user-facing query takes the workspace id: there
 * is deliberately no unscoped lookup by document id alone.
 */
interface DocumentRepository extends Repository<Document, UUID> {

    Document saveAndFlush(Document document);

    Optional<Document> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<Document> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    /** System-level: unfinished ingestion work across all workspaces (startup recovery). */
    List<Document> findByStatusIn(Collection<DocumentStatus> statuses);

    void delete(Document document);

    /** PENDING → PROCESSING as a single conditional update; returns 1 if this caller won. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Document d
               set d.status = com.nexusai.document.DocumentStatus.PROCESSING,
                   d.statusDetail = null,
                   d.updatedAt = :now
             where d.id = :id and d.workspaceId = :workspaceId
               and d.status = com.nexusai.document.DocumentStatus.PENDING
            """)
    int claimForIngestion(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
                          @Param("now") Instant now);
}

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

    /** READY + index PENDING → INDEXING as a single conditional update; 1 if this caller won. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Document d
               set d.indexStatus = com.nexusai.document.SearchIndexStatus.INDEXING,
                   d.updatedAt = :now
             where d.id = :id and d.workspaceId = :workspaceId
               and d.status = com.nexusai.document.DocumentStatus.READY
               and d.indexStatus = com.nexusai.document.SearchIndexStatus.PENDING
            """)
    int claimForIndexing(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
                         @Param("now") Instant now);

    /** System-level: documents in the given index state, oldest first. */
    List<Document> findByIndexStatusOrderByUpdatedAtAsc(SearchIndexStatus status, Pageable pageable);

    List<Document> findByIndexStatus(SearchIndexStatus status);

    List<Document> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    /** Rows of (indexStatus, count) for READY documents of a workspace. */
    @Query("""
            select d.indexStatus, count(d) from Document d
             where d.workspaceId = :workspaceId and d.status = com.nexusai.document.DocumentStatus.READY
             group by d.indexStatus
            """)
    List<Object[]> countIndexStatuses(@Param("workspaceId") UUID workspaceId);

    @Query("""
            select count(d) from Document d
             where d.workspaceId = :workspaceId
               and d.status in (com.nexusai.document.DocumentStatus.PENDING, com.nexusai.document.DocumentStatus.PROCESSING)
            """)
    long countIngestingInWorkspace(@Param("workspaceId") UUID workspaceId);
}

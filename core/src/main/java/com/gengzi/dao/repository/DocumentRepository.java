package com.gengzi.dao.repository;

import com.gengzi.dao.Document;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String>, JpaSpecificationExecutor<Document> {

    @Query("update Document d set d.chunkNum= :chunkNum, d.status = :status where d.id = :id")
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    int updateChunkNumAndStatusById(@Size(max = 64) String id, @NotNull Integer chunkNum, String status);

    @Query("update Document d set d.status = :status where d.id = :documentId")
    @Modifying
    int updateStatusById(String documentId, String status);


    List<Document> findDocumentByChunkNumGreaterThan(@NotNull Integer chunkNumIsGreaterThan);

    List<Document> findDocumentByKbId(@Size(max = 256) @NotNull String kbId);

    List<Document> findDocumentByIdIn(Collection<String> ids);

    @Query("select d from Document d where d.kbId = :kbId and d.chunkNum > 0")
    List<Document> findChunkDocumentByKbId(String kbId);
}
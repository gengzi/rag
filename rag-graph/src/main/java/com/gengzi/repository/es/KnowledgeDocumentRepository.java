package com.gengzi.repository.es;

import com.gengzi.model.es.KnowledgeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface KnowledgeDocumentRepository extends ElasticsearchRepository<KnowledgeDocument, String> {

    // 根据知识库ID查询
    Page<KnowledgeDocument> findByKbId(String kbId, Pageable pageable);

    // 根据文档ID查询
    List<KnowledgeDocument> findByDocId(String docId);

    // Metadata 字段查询示例 (Spring Data 支持级联命名)
    List<KnowledgeDocument> findByMetadata_DocumentName(String documentName);
    
    // 范围查询示例
    List<KnowledgeDocument> findByCreateTimestampFltBetween(Float min, Float max);
}

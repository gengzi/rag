package com.gengzi.service.es;

import com.gengzi.model.es.KnowledgeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 知识文档服务接口。
 */
public interface KnowledgeDocumentService {

    /**
     * 保存或更新文档。
     */
    KnowledgeDocument save(KnowledgeDocument document);

    /**
     * 根据 ID 获取文档。
     */
    KnowledgeDocument findById(String id);

    /**
     * 根据知识库 ID 分页查询。
     */
    Page<KnowledgeDocument> findByKbId(String kbId, Pageable pageable);

    /**
     * 根据文档 ID 查询详情。
     */
    List<KnowledgeDocument> findByDocId(String docId);

    /**
     * 向量相似度检索。
     */
    List<KnowledgeDocument> searchByVector(float[] queryVector, int k, float minScore);
}

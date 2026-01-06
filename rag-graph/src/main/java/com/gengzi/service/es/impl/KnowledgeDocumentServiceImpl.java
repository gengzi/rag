package com.gengzi.service.es.impl;

import co.elastic.clients.elasticsearch._types.KnnQuery;
import com.gengzi.model.es.KnowledgeDocument;
import com.gengzi.repository.es.KnowledgeDocumentRepository;
import com.gengzi.service.es.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final int VECTOR_DIMS = 1024;

    private final KnowledgeDocumentRepository repository;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 保存或更新文档。
     */
    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        return repository.save(document);
    }

    /**
     * 根据 ID 获取文档。
     */
    @Override
    public KnowledgeDocument findById(String id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * 获取某知识库下的文档分页。
     */
    @Override
    public Page<KnowledgeDocument> findByKbId(String kbId, Pageable pageable) {
        return repository.findByKbId(kbId, pageable);
    }

    /**
     * 根据文档 ID 查询详情。
     */
    @Override
    public List<KnowledgeDocument> findByDocId(String docId) {
        return repository.findByDocId(docId);
    }

    /**
     * 向量相似度检索（语义检索）。
     * 使用 ES 8.x 的 kNN 查询。
     */
    @Override
    public List<KnowledgeDocument> searchByVector(float[] queryVector, int k, float minScore) {
        if (queryVector == null || queryVector.length != VECTOR_DIMS) {
            throw new IllegalArgumentException("Vector dimension must be " + VECTOR_DIMS);
        }

        NativeQuery query = NativeQuery.builder()
            .withKnnQuery(KnnQuery.of(kq -> kq
                .field("q_1024_vec")
                .queryVector(toList(queryVector))
                .k(k)
                .numCandidates(k * 10)
            ))
            .build();

        SearchHits<KnowledgeDocument> searchHits = elasticsearchOperations.search(
            query,
            KnowledgeDocument.class,
            IndexCoordinates.of("knowledge_docs")
        );

        return searchHits.stream()
            .filter(hit -> hit.getScore() >= minScore)
            .map(org.springframework.data.elasticsearch.core.SearchHit::getContent)
            .collect(Collectors.toList());
    }

    // float[] 转 List<Float>，供 kNN 查询使用。
    private List<Float> toList(float[] floats) {
        List<Float> list = new ArrayList<>(floats.length);
        for (float value : floats) {
            list.add(value);
        }
        return list;
    }
}

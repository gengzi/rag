package com.gengzi.rag.vector.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.RRFRetriever;
import co.elastic.clients.elasticsearch._types.Retriever;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.Version;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gengzi.rag.vector.es.document.ExtendedDocument;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchAiSearchFilterExpressionConverter;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ExtendedElasticsearchVectorStore extends AbstractObservationVectorStore implements InitializingBean {

    private static final Map<SimilarityFunction, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
            SimilarityFunction.cosine, VectorStoreSimilarityMetric.COSINE, SimilarityFunction.l2_norm,
            VectorStoreSimilarityMetric.EUCLIDEAN, SimilarityFunction.dot_product, VectorStoreSimilarityMetric.DOT);

    private final ElasticsearchClient elasticsearchClient;

    private final ElasticsearchVectorStoreOptions options;

    private final FilterExpressionConverter filterExpressionConverter;

    private final boolean initializeSchema;

    protected ExtendedElasticsearchVectorStore(Builder builder) {
        super(builder);

        Assert.notNull(builder.restClient, "RestClient must not be null");

        this.initializeSchema = builder.initializeSchema;
        this.options = builder.options;
        this.filterExpressionConverter = builder.filterExpressionConverter;

        String version = Version.VERSION == null ? "Unknown" : Version.VERSION.toString();
        this.elasticsearchClient = new ElasticsearchClient(new RestClientTransport(builder.restClient,
                new JacksonJsonpMapper(
                        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))))
                .withTransportOptions(t -> t.addHeader("user-agent", "spring-ai elastic-java/" + version));
    }

    /**
     * Creates a new builder instance for ElasticsearchVectorStore.
     *
     * @return a new ElasticsearchBuilder instance
     */
    public static Builder builder(RestClient restClient, EmbeddingModel embeddingModel) {
        return new Builder(restClient, embeddingModel);
    }

    /**
     * 添加内容
     * @param documents
     */
    @Override
    public void doAdd(List<Document> documents) {
        documents.forEach(document -> {
            if (!(document instanceof ExtendedDocument)) {
                throw new IllegalArgumentException("document type error!!! not equals ExtendedDocument");
            }
        });


        // For the index to be present, either it must be pre-created or set the
        // initializeSchema to true.
        if (!indexExists()) {
            throw new IllegalArgumentException("Index not found");
        }
        BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();

        List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
                this.batchingStrategy);

        for (int i = 0; i < embeddings.size(); i++) {
            Document document = documents.get(i);

            float[] embedding = embeddings.get(i);
            bulkRequestBuilder.operations(op -> op.index(idx -> idx.index(this.options.getIndexName())
                    .id(document.getId())
                    .document(getDocument(document, embedding, this.options.getEmbeddingFieldName()))));
        }
        BulkResponse bulkRequest = bulkRequest(bulkRequestBuilder.build());
        if (bulkRequest.errors()) {
            List<BulkResponseItem> bulkResponseItems = bulkRequest.items();
            for (BulkResponseItem bulkResponseItem : bulkResponseItems) {
                if (bulkResponseItem.error() != null) {
                    throw new IllegalStateException(bulkResponseItem.error().reason());
                }
            }
        }
    }

    private Object getDocument(Document document, float[] embedding, String embeddingFieldName) {

        Assert.notNull(document.getText(), "document's text must not be null");
        ExtendedDocument extendedDocument = (ExtendedDocument) document;
        return new HashMap<String, Object>() {
            {
                put("id", document.getId());
                put("content", document.getText());
                put("metadata", document.getMetadata());
                put(embeddingFieldName, embedding);
                put("doc_id", extendedDocument.getEsVectorDocument().getDocId());
                put("title_tks", extendedDocument.getEsVectorDocument().getTitleTks());
                put("title_sm_tks", extendedDocument.getEsVectorDocument().getTitleSmTks());
                put("content_ltks", extendedDocument.getEsVectorDocument().getContentLtks());
                put("content_sm_ltks", extendedDocument.getEsVectorDocument().getContentSmLtks());
                put("pageNumInt", extendedDocument.getEsVectorDocument().getPageNumInt());
                put("create_time", extendedDocument.getEsVectorDocument().getCreateTime());
                put("create_timestamp_flt", extendedDocument.getEsVectorDocument().getCreateTimestampFlt());
                put("img_id", extendedDocument.getEsVectorDocument().getImgId());

            }
        };
    }

    @Override
    public void doDelete(List<String> idList) {
        BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
        // For the index to be present, either it must be pre-created or set the
        // initializeSchema to true.
        if (!indexExists()) {
            throw new IllegalArgumentException("Index not found");
        }
        for (String id : idList) {
            bulkRequestBuilder.operations(op -> op.delete(idx -> idx.index(this.options.getIndexName()).id(id)));
        }
        if (bulkRequest(bulkRequestBuilder.build()).errors()) {
            throw new IllegalStateException("Delete operation failed");
        }
    }

    @Override
    public void doDelete(Filter.Expression filterExpression) {
        // For the index to be present, either it must be pre-created or set the
        // initializeSchema to true.
        if (!indexExists()) {
            throw new IllegalArgumentException("Index not found");
        }

        try {
            this.elasticsearchClient.deleteByQuery(d -> d.index(this.options.getIndexName())
                    .query(q -> q.queryString(qs -> qs.query(getElasticsearchQueryString(filterExpression)))));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete documents by filter", e);
        }
    }

    private BulkResponse bulkRequest(BulkRequest bulkRequest) {
        try {
            return this.elasticsearchClient.bulk(bulkRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
//        Assert.notNull(searchRequest, "The search request must not be null.");
//        try {
//            float threshold = (float) searchRequest.getSimilarityThreshold();
//            // reverting l2_norm distance to its original value
//            if (this.options.getSimilarity().equals(SimilarityFunction.l2_norm)) {
//                threshold = 1 - threshold;
//            }
//            final float finalThreshold = threshold;
//            float[] vectors = this.embeddingModel.embed(searchRequest.getQuery());
//
//            SearchResponse<Document> res = this.elasticsearchClient.search(sr -> sr.index(this.options.getIndexName())
//                    .knn(knn -> knn.queryVector(EmbeddingUtils.toList(vectors))
//                            .similarity(finalThreshold)
//                            .k(searchRequest.getTopK())
//                            .field(this.options.getEmbeddingFieldName())
//                            .numCandidates((int) (1.5 * searchRequest.getTopK()))
//                            .filter(fl -> fl
//                                    .queryString(qs -> qs.query(getElasticsearchQueryString(searchRequest.getFilterExpression())))))
//                    .size(searchRequest.getTopK()), Document.class);
//
//            return res.hits().hits().stream().map(this::toDocument).collect(Collectors.toList());
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        // es 的混合检索是收费功能
//        return esRRFSearch(searchRequest);

        return freeHybridSearch(searchRequest);
    }

    private List<Document> esRRFSearch(SearchRequest searchRequest) {
        Assert.notNull(searchRequest, "The search request must not be null.");
        try {
            float threshold = (float) searchRequest.getSimilarityThreshold();
            // 处理L2归一化距离的阈值转换
            if (this.options.getSimilarity().equals(SimilarityFunction.l2_norm)) {
                threshold = 1 - threshold;
            }
            final float finalThreshold = threshold;

            // 生成查询向量
            float[] vectors = this.embeddingModel.embed(searchRequest.getQuery());
            List<Float> queryVector = EmbeddingUtils.toList(vectors);
            String queryText = searchRequest.getQuery();

            // 1. 构建向量检索器 (KNN搜索)
            Retriever vectorRetriever = Retriever.of(r -> r
                    .standard(s -> s
                            .query(q -> q
                                    .knn(knn -> knn
                                            .queryVector(queryVector)
                                            .similarity(finalThreshold)
                                            .k(searchRequest.getTopK())
                                            .field(this.options.getEmbeddingFieldName())
                                            .numCandidates((int) (1.5 * searchRequest.getTopK()))
                                    )
                            )
                            // 应用过滤条件到向量检索器
                            .filter(fl -> fl
                                    .queryString(qs -> qs.query(getElasticsearchQueryString(searchRequest.getFilterExpression())))
                            )
                    )
            );

            // 2. 构建全文检索器 (match查询)
            Retriever textRetriever = Retriever.of(r -> r
                    .standard(s -> s
                            .query(q -> q
                                    .multiMatch(mm -> mm
                                            .query(queryText)
                                            .fields("title_tks", "content_ltks^2")  // 可根据实际字段调整
                                            .type(TextQueryType.BestFields)
                                    )
                            )
                            // 同样应用过滤条件到全文检索器
                            .filter(fl -> fl
                                    .queryString(qs -> qs.query(getElasticsearchQueryString(searchRequest.getFilterExpression())))
                            )
                    )
            );

            // 3. 配置RRF融合器 （还有一种权重计算）  （重排序）
            RRFRetriever rrfRetriever = RRFRetriever.of(rrf -> rrf
                    .retrievers(vectorRetriever, textRetriever)  // 融合向量和全文检索结果
                    .rankConstant(60)  // RRF算法常数，默认60
            );

            // 4. 执行RRF混合搜索
            SearchResponse<Document> res = this.elasticsearchClient.search(sr -> sr
                            .index(this.options.getIndexName())
                            .retriever(r -> r.rrf(rrfRetriever))  // 使用RRF检索器
                            .size(searchRequest.getTopK()),
                    Document.class
            );

            // 5. 处理并返回结果
            return res.hits().hits().stream()
                    .map(this::toDocument)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    // 改造后的免费混合检索实现
    public List<Document> freeHybridSearch(SearchRequest searchRequest) {
        Assert.notNull(searchRequest, "The search request must not be null.");
        try {
            float threshold = (float) searchRequest.getSimilarityThreshold();
            // 处理L2归一化距离的阈值转换（保持原逻辑）
            if (this.options.getSimilarity().equals(SimilarityFunction.l2_norm)) {
                threshold = 1 - threshold;
            }
            final float finalThreshold = threshold;

            // 生成查询向量和原始查询文本
            float[] vectors = this.embeddingModel.embed(searchRequest.getQuery());
            List<Float> queryVector = EmbeddingUtils.toList(vectors);
            String queryText = searchRequest.getQuery();
            int topK = searchRequest.getTopK();
            String indexName = this.options.getIndexName();
            String filterQuery = getElasticsearchQueryString(searchRequest.getFilterExpression());

            // 1. 执行向量检索（KNN），获取结果及排名
            List<Hit<Document>> vectorHits = executeVectorSearch(
                    indexName, queryVector, finalThreshold, topK, filterQuery
            );

            // 2. 执行关键词检索（multiMatch），获取结果及排名
            List<Hit<Document>> textHits = executeTextSearch(
                    indexName, queryText, topK, filterQuery
            );

            // 3. 手动实现RRF融合算法（核心：基于排名计算得分）
            List<Document> mergedResults = mergeWithRRF(vectorHits, textHits, topK);

            return mergedResults;

        } catch (IOException e) {
            throw new RuntimeException("混合检索执行失败", e);
        }
    }

    // 执行向量检索（KNN）
    private List<Hit<Document>> executeVectorSearch(
            String indexName, List<Float> queryVector, float threshold, int topK, String filterQuery) throws IOException {
        // 构建KNN查询
        KnnQuery knnQuery = KnnQuery.of(knn -> knn
                .queryVector(queryVector)
                .similarity(threshold)
                .k(topK)
                .field(this.options.getEmbeddingFieldName())
                .numCandidates((int) (1.5 * topK)) // 候选集数量
        );

        // 构建带过滤条件的查询
        Query query = Query.of(q -> q
                .knn(knnQuery)
        );
        if (filterQuery != null && !filterQuery.isEmpty()) {
            query = Query.of(q -> q
                    .bool(b -> b
                            .must(m -> m.knn(knnQuery)) // 必须满足KNN条件
                            .filter(f -> f.queryString(qs -> qs.query(filterQuery))) // 过滤条件
                    )
            );
        }

        // 执行查询
        Query finalQuery = query;
        SearchResponse<Document> vectorResponse = this.elasticsearchClient.search(
                sr -> sr.index(indexName).query(finalQuery).size(topK),
                Document.class
        );
        return vectorResponse.hits().hits();
    }

    // 执行关键词检索（multiMatch）
    private List<Hit<Document>> executeTextSearch(
            String indexName, String queryText, int topK, String filterQuery) throws IOException {
        // 构建multiMatch查询
        MultiMatchQuery multiMatchQuery = MultiMatchQuery.of(mm -> mm
                .query(queryText)
                .fields("title_tks", "content_ltks^2") // 保持原字段权重
                .type(TextQueryType.BestFields)
        );

        // 构建带过滤条件的查询
        Query query = Query.of(q -> q
                .multiMatch(multiMatchQuery)
        );
        if (filterQuery != null && !filterQuery.isEmpty()) {
            query = Query.of(q -> q
                    .bool(b -> b
                            .must(m -> m.multiMatch(multiMatchQuery)) // 必须满足关键词条件
                            .filter(f -> f.queryString(qs -> qs.query(filterQuery))) // 过滤条件
                    )
            );
        }

        // 执行查询
        Query finalQuery = query;
        SearchResponse<Document> textResponse = this.elasticsearchClient.search(
                sr -> sr.index(indexName).query(finalQuery).size(topK),
                Document.class
        );
        return textResponse.hits().hits();
    }

    // 手动实现RRF融合（基于排名计算得分）
    private List<Document> mergeWithRRF(
            List<Hit<Document>> vectorHits, List<Hit<Document>> textHits, int topK) {
        // 1. 记录每个文档在两个检索结果中的排名（从1开始）
        Map<String, Integer> vectorRankMap = new HashMap<>();
        for (int i = 0; i < vectorHits.size(); i++) {
            vectorRankMap.put(vectorHits.get(i).id(), i + 1); // 排名=索引+1
        }

        Map<String, Integer> textRankMap = new HashMap<>();
        for (int i = 0; i < textHits.size(); i++) {
            textRankMap.put(textHits.get(i).id(), i + 1);
        }

        // 2. 收集所有文档ID（去重）
        Set<String> allDocIds = new HashSet<>();
        vectorHits.forEach(hit -> allDocIds.add(hit.id()));
        textHits.forEach(hit -> allDocIds.add(hit.id()));

        // 3. 计算RRF得分：sum(1/(k + rank))，k=60（标准RRF参数）
        int rrfK = 60;
        Map<String, Float> rrfScoreMap = new HashMap<>();
        for (String docId : allDocIds) {
            // 向量检索中的得分贡献（若未出现，排名视为无穷大，贡献0）
            int vectorRank = vectorRankMap.getOrDefault(docId, Integer.MAX_VALUE);
            float vectorScore = (vectorRank != Integer.MAX_VALUE) ? 1.0f / (rrfK + vectorRank) : 0f;

            // 关键词检索中的得分贡献
            int textRank = textRankMap.getOrDefault(docId, Integer.MAX_VALUE);
            float textScore = (textRank != Integer.MAX_VALUE) ? 1.0f / (rrfK + textRank) : 0f;

            // 总得分
            rrfScoreMap.put(docId, vectorScore + textScore);
        }

        // 4. 按RRF得分降序排序，取前topK，并转换为Document
        Map<String, Hit<Document>> allHitsMap = new HashMap<>();
        vectorHits.forEach(hit -> allHitsMap.put(hit.id(), hit));
        textHits.forEach(hit -> allHitsMap.put(hit.id(), hit)); // 若ID重复，取最后一个（不影响，内容一致）

        return rrfScoreMap.entrySet().stream()
                .sorted((e1, e2) -> Float.compare(e2.getValue(), e1.getValue())) // 降序
                .limit(topK)
                .map(entry -> toDocument(allHitsMap.get(entry.getKey()))) // 转换为Document
                .collect(Collectors.toList());
    }


    private String getElasticsearchQueryString(Filter.Expression filterExpression) {
        return Objects.isNull(filterExpression) ? "*"
                : this.filterExpressionConverter.convertExpression(filterExpression);

    }

    private Document toDocument(Hit<Document> hit) {
        Document document = hit.source();
        Document.Builder documentBuilder = document.mutate();
        if (hit.score() != null) {
            documentBuilder.metadata(DocumentMetadata.DISTANCE.value(), 1 - normalizeSimilarityScore(hit.score()));
            documentBuilder.score(normalizeSimilarityScore(hit.score()));
        }
        return documentBuilder.build();
    }

    // more info on score/distance calculation
    // https://www.elastic.co/guide/en/elasticsearch/reference/current/knn-search.html#knn-similarity-search
    private double normalizeSimilarityScore(double score) {
        switch (this.options.getSimilarity()) {
            case l2_norm:
                // the returned value of l2_norm is the opposite of the other functions
                // (closest to zero means more accurate), so to make it consistent
                // with the other functions the reverse is returned applying a "1-"
                // to the standard transformation
                return (1 - (Math.sqrt((1 / score) - 1)));
            // cosine and dot_product
            default:
                return (2 * score) - 1;
        }
    }

    public boolean indexExists() {
        try {
            return this.elasticsearchClient.indices().exists(ex -> ex.index(this.options.getIndexName())).value();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private DenseVectorSimilarity parseSimilarity(String similarity) {
        for (DenseVectorSimilarity sim : DenseVectorSimilarity.values()) {
            if (sim.jsonValue().equalsIgnoreCase(similarity)) {
                return sim;
            }
        }
        throw new IllegalArgumentException("Unsupported similarity: " + similarity);
    }

    /**
     * 初始化es索引（类似新建数据库和数据库表字段映射）
     */
    private void createIndexMapping() {
        try {
            this.elasticsearchClient.indices()
                    .create(cr -> cr.index(this.options.getIndexName())
                            .mappings(map -> map
                                    // 1. 源文档标识字段（keyword 类型：支持精确匹配、过滤）
                                    .properties("doc_id", p -> p.keyword(k -> k.index(true))) // doc_id: 精确匹配
                                    .properties("kb_id", p -> p.keyword(k -> k.index(true)))  // kb_id: 精确匹配
                                    .properties("img_id", p -> p.keyword(k -> k.index(true))) // 图片ID：精确匹配
                                    // 2. 内容与元数据字段（text/keyword 组合：text 支持全文检索，keyword 支持聚合）
                                    .properties("content", p -> p.text(t -> t.analyzer("standard"))) // 原内容：全文检索
                                    .properties("docnm_kwd", p -> p.keyword(k -> k.index(true))) // 文件名：精确匹配
                                    .properties("title_tks", p -> p.text(t -> t.analyzer("whitespace"))) // 标题分词：空格分隔检索
                                    .properties("title_sm_tks", p -> p.text(t -> t.analyzer("whitespace"))) // 标题简化分词
                                    .properties("content_ltks", p -> p.text(t -> t.analyzer("whitespace"))) // 内容全量分词
                                    .properties("content_sm_ltks", p -> p.text(t -> t.analyzer("whitespace"))) // 内容简化分词
                                    .properties("doc_type_kwd", p -> p.keyword(k -> k.index(true))) // 文档类型：精确匹配（如 image/text）

                                    // 3. 位置相关字段（keyword 类型：存储字符串，不做分词）
                                    .properties("page_num_int", p -> p.keyword(k -> k.index(false))) // 页码：不索引（仅存储）
                                    .properties("position_int", p -> p.keyword(k -> k.index(false))) // 位置坐标：不索引
                                    .properties("top_int", p -> p.keyword(k -> k.index(false)))     // 顶部坐标：不索引

                                    // 4. 时间字段（date/keyword 类型：date 支持时间范围查询）
                                    .properties("create_time", p -> p.date(d -> d.format("yyyy-MM-dd HH:mm:ss").index(true))) // 格式化时间
                                    .properties("create_timestamp_flt", p -> p.float_(f -> f.index(true))) // 时间戳：浮点型（支持范围查询）
                                    // 6. 向量字段（dense_vector 类型：指定维度和相似度函数）
                                    .properties(this.options.getEmbeddingFieldName(),
                                            p -> p.denseVector(dv -> dv
                                                    .similarity(parseSimilarity(this.options.getSimilarity().toString()))
                                                    .dims(this.options.getDimensions())))

                                    // 7. 保留原有的 metadata 字段（object 类型：存储灵活键值对）
                                    .properties("metadata", p -> p.object(o -> o.enabled(true)))
                            )
                    );
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Elasticsearch index mapping", e);
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (!this.initializeSchema) {
            return;
        }
        if (!indexExists()) {
            createIndexMapping();
        }
    }

    @Override
    public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
        return VectorStoreObservationContext.builder(VectorStoreProvider.ELASTICSEARCH.value(), operationName)
                .collectionName(this.options.getIndexName())
                .dimensions(this.embeddingModel.dimensions())
                .similarityMetric(getSimilarityMetric());
    }

    private String getSimilarityMetric() {
        if (!SIMILARITY_TYPE_MAPPING.containsKey(this.options.getSimilarity())) {
            return this.options.getSimilarity().name();
        }
        return SIMILARITY_TYPE_MAPPING.get(this.options.getSimilarity()).value();
    }

    @Override
    public <T> Optional<T> getNativeClient() {
        @SuppressWarnings("unchecked")
        T client = (T) this.elasticsearchClient;
        return Optional.of(client);
    }

    public static class Builder extends AbstractVectorStoreBuilder<Builder> {

        private final RestClient restClient;

        private ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();

        private boolean initializeSchema = false;

        private FilterExpressionConverter filterExpressionConverter = new ElasticsearchAiSearchFilterExpressionConverter();

        /**
         * Sets the Elasticsearch REST client.
         *
         * @param restClient     the Elasticsearch REST client
         * @param embeddingModel the Embedding Model to be used
         */
        public Builder(RestClient restClient, EmbeddingModel embeddingModel) {
            super(embeddingModel);
            Assert.notNull(restClient, "RestClient must not be null");
            this.restClient = restClient;
        }

        /**
         * Sets the Elasticsearch vector store options.
         *
         * @param options the vector store options to use
         * @return the builder instance
         * @throws IllegalArgumentException if options is null
         */
        public Builder options(ElasticsearchVectorStoreOptions options) {
            Assert.notNull(options, "options must not be null");
            this.options = options;
            return this;
        }

        /**
         * Sets whether to initialize the schema.
         *
         * @param initializeSchema true to initialize schema, false otherwise
         * @return the builder instance
         */
        public Builder initializeSchema(boolean initializeSchema) {
            this.initializeSchema = initializeSchema;
            return this;
        }

        /**
         * Sets the filter expression converter.
         *
         * @param converter the filter expression converter to use
         * @return the builder instance
         * @throws IllegalArgumentException if converter is null
         */
        public Builder filterExpressionConverter(FilterExpressionConverter converter) {
            Assert.notNull(converter, "filterExpressionConverter must not be null");
            this.filterExpressionConverter = converter;
            return this;
        }

        /**
         * Builds the ElasticsearchVectorStore instance.
         *
         * @return a new ElasticsearchVectorStore instance
         * @throws IllegalStateException if the builder is in an invalid state
         */
        @Override
        public ExtendedElasticsearchVectorStore build() {
            return new ExtendedElasticsearchVectorStore(this);
        }


    }


}

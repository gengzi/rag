package com.gengzi.ui.service.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.util.NamedValue;
import com.gengzi.config.S3Properties;
import com.gengzi.dao.Document;
import com.gengzi.dao.repository.DocumentRepository;
import com.gengzi.embedding.load.pdf.OcrPdfReader;
import com.gengzi.embedding.load.word.WordConvertByMarkItDownReader;
import com.gengzi.enums.FileProcessStatusEnum;
import com.gengzi.enums.S3FileType;
import com.gengzi.request.DocumentSearchReq;
import com.gengzi.response.*;
import com.gengzi.s3.S3ClientUtils;
import com.gengzi.security.BusinessException;
import com.gengzi.ui.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.IOException;
import java.net.URL;
import java.util.*;

@Service
public class DocumentServiceImpl implements DocumentService {


    public static final String IMG_SUFFIX = "layout_det_res.jpeg";
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private S3ClientUtils s3ClientUtils;
    @Autowired
    private S3Properties s3Properties;
    @Autowired
    private OcrPdfReader reader;
    @Autowired
    private ElasticsearchClient elasticsearchClient;
    @Autowired
    private WordConvertByMarkItDownReader wordConvertByMarkItDownReader;

    @Value("${esVectorstore.index-name}")
    private String indexName;

    /**
     * 将doc进行embeding 处理
     *
     * @param documentId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void documentToEmbedding(String documentId) {
        if (StrUtil.isBlankIfStr(documentId)) {
            return;
        }
        Optional<Document> documentOptional = documentRepository.findById(documentId);
        if (!documentOptional.isPresent()) {
            return;
        }
        Document document = documentOptional.get();
        String file = document.getLocation();
        HeadObjectResponse headObjectResponse = s3ClientUtils.headObject(s3Properties.getDefaultBucketName(), file);
        String contentType = headObjectResponse.contentType();
        S3FileType s3FileType = S3FileType.fromMimeType(contentType);
        // 使用switch处理不同文件类型
        switch (s3FileType) {
            case PDF:
                // 处理pdf的解析流程
                reader.pdfParse(file, document);
                break;
            case DOC, DOCX:
                // 处理word的解析流程
                wordConvertByMarkItDownReader.wordParse(file, document);
                break;
            case UNKNOWN:
                break;
            // 不需要default，因为枚举已覆盖所有可能
        }
        documentRepository.updateStatusById(documentId, String.valueOf(FileProcessStatusEnum.PROCESSING.getCode()));
    }

    /**
     * @param documentId
     * @return
     */
    @Override
    public DocumentPreviewResponse documentPreview(String documentId) {
        Optional<Document> documentOptional = documentRepository.findById(documentId);
        if (!documentOptional.isPresent()) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        DocumentPreviewResponse documentPreviewResponse = new DocumentPreviewResponse();
        Document document = documentOptional.get();
        String file = document.getLocation();
        HeadObjectResponse headObjectResponse = s3ClientUtils.headObject(s3Properties.getDefaultBucketName(), file);
        URL url = s3ClientUtils.generatePresignedUrl(s3Properties.getDefaultBucketName(), file);
        String contentType = headObjectResponse.contentType();
        documentPreviewResponse.setContentType(contentType);
        documentPreviewResponse.setUrl(url.toString());
        return documentPreviewResponse;
    }

    /**
     * @return
     */
    @Override
    public Map<String, Object> search(DocumentSearchReq req) {
        try {
            return searchContent(req.getQuery(), req.getPage(), req.getPageSize());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param kbId
     * @return
     */
    @Override
    public List<?> documentChunks(String kbId) {
        return documentRepository.findChunkDocumentByKbId(kbId);
    }

    /**
     * @param documentId
     * @return
     */
    @Override
    public DocumentDetailsResponse documentChunksDetails(String documentId) {
        Optional<Document> byId = documentRepository.findById(documentId);
        if (!byId.isPresent()) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        // 返回文档详情
        Document document = byId.get();
        DocumentDetailsResponse documentDetailsResponse = new DocumentDetailsResponse();
        documentDetailsResponse.setId(document.getId());
        documentDetailsResponse.setName(document.getName());
        documentDetailsResponse.setCreateTime(document.getCreateTime());
        documentDetailsResponse.setSize(document.getSize());
        documentDetailsResponse.setChunkNum(document.getChunkNum());
        documentDetailsResponse.setContentType(document.getType());
        // 返回分块详情
        List<ChunkDetails> chunkDetailsLinkedList = new LinkedList<>();
        SearchRequest searchRequest = SearchRequest.of(sr -> sr
                .index(indexName)
                .query(q -> q.term(m -> m.field("metadata.documentId").value(documentId)))
                .size(1000)
        );
        try {
            SearchResponse<Map> search = elasticsearchClient.search(searchRequest, Map.class);
            List<Hit<Map>> hits = search.hits().hits();
            for (Hit<Map> hit : hits) {
                Map<String, Object> source = hit.source();
                ChunkDetails chunkDetails = new ChunkDetails();
                chunkDetails.setId((String) source.get("id"));
                chunkDetails.setContent((String) source.get("content"));
                chunkDetails.setPageNumInt((String) source.get("pageNumInt"));
                Map metadata = (Map) source.get("metadata");
                String fileId = (String) metadata.get("fileId");
                LinkedList<String> imgs = new LinkedList<>();
                for (String key : chunkDetails.getPageNumInt().split(",")) {
                    Integer pageNum = Integer.valueOf(key) + 1;
                    String imgName = String.format("%s/%s_%s_%s", fileId, fileId, pageNum, IMG_SUFFIX);
                    imgs.add(imgName);
                }
                chunkDetails.setImg(imgs);
                chunkDetailsLinkedList.add(chunkDetails);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        documentDetailsResponse.setChunkDetails(chunkDetailsLinkedList);
        return documentDetailsResponse;
    }

    /**
     * @param imgkey
     * @return
     */
    @Override
    public ImagePreviewResponse documentImgPreview(String imgkey) {
        ImagePreviewResponse imagePreviewResponse = new ImagePreviewResponse();
        URL url = s3ClientUtils.generatePresignedUrl(s3Properties.getDefaultBucketName(), imgkey);
        imagePreviewResponse.setUrl(url.toString());
        return imagePreviewResponse;
    }

    /**
     * 检索content字段并高亮匹配内容
     *
     * @param question 长文本问题
     * @param page     页码（从0开始）
     * @param size     每页条数
     * @return 包含原始数据和高亮内容的结果列表
     */
    public Map<String, Object> searchContent(String question, int page, int size) throws IOException {
        // 1. 构建查询条件（仅检索content字段）
        MatchQuery matchQuery = MatchQuery.of(mq -> mq
                        .field("content")  // 只检索content字段
                        .query(question)   // 长文本问题作为查询词
//                .analyzer("ik_max_word")  // 使用IK分词器（如需中文分词）
        );

        // 2. 配置高亮规则
        HighlightField highlightField = HighlightField.of(hf -> hf
                .preTags("<em style='color:red;font-weight:bold'>")  // 高亮前缀
                .postTags("</em>")  // 高亮后缀
                .fragmentSize(300)  // 每个高亮片段长度
                .numberOfFragments(3)  // 最多返回3个片段
        );
        NamedValue<HighlightField> namedHighlightField = NamedValue.of("content", highlightField);

        Highlight highlight = Highlight.of(h -> h
                .fields(List.of(namedHighlightField))  // 仅对content字段高亮
                .requireFieldMatch(true)  // 只高亮匹配的字段
        );

        // 3. 构建搜索请求
        SearchRequest searchRequest = SearchRequest.of(sr -> sr
                .index(indexName)
                .query(q -> q.match(matchQuery))
                .highlight(highlight)
                .from((page - 1) * size)  // 分页起始位置
                .size(size)         // 每页数量
                .sort(s -> s              // 增加排序，确保分页稳定
                        .field(f -> f
                                .field("_score")
                                .order(SortOrder.Desc)
                        )
                )
        );

//        SearchRequest searchRequest = SearchRequest.of(sr -> sr
//                .index("rag_store_new")
//                .query(q -> q.match(m -> m.field("content").query(question)))
//                .size(10)
//        );

        // 4. 执行搜索
        SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

        // 5. 处理结果（整合原始数据和高亮内容）
        List<Map<String, Object>> results = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> result = new HashMap<>();
            // 原始文档数据
            result.put("source", hit.source());
            // 匹配得分（可用于排序展示）
            result.put("score", hit.score());
            // 高亮内容（如果有）
            if (hit.highlight() != null && hit.highlight().containsKey("content")) {
                result.put("highlightedContent", hit.highlight().get("content"));
            }
            results.add(result);
        }

        // 6. 计算分页元数据
        long totalHits = response.hits().total().value();  // 获取总匹配数
        int totalPages = (int) Math.ceil((double) totalHits / size);  // 计算总页数

        // 7. 组装返回结果
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("data", results);         // 当前页数据
        responseMap.put("page", page);            // 当前页码
        responseMap.put("size", size);            // 每页大小
        responseMap.put("total", totalHits);      // 总条数
        responseMap.put("totalPages", totalPages); // 总页数

        return responseMap;
    }
}

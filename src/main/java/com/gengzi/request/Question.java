package com.gengzi.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 问题实体类，对应提供的JSON结构
 */
@Data
public class Question {
    /**
     * 问题ID
     */
    @JsonProperty("question_id")
    private Integer questionId;
    
    /**
     * 相关文档列表
     */
    @JsonProperty("related_document_list")
    private List<String> relatedDocumentList;
    
    /**
     * 问题内容
     */
    private String question;
    
    /**
     * 参考答案
     */
    @JsonProperty("reference_answer")
    private String referenceAnswer;
    
    /**
     * 相关片段ID列表
     */
    @JsonProperty("related_chunk_ids")
    private List<String> relatedChunkIds;
}
    
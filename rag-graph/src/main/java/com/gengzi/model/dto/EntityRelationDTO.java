package com.gengzi.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * LLM 实体关系提取结果的 DTO
 * 对应 ENTITY_RECOGNITION_PROMPT.md 中定义的输出格式
 */
@Data
public class EntityRelationDTO {

    @JsonProperty("head")
    private String head;

    @JsonProperty("head_type")
    private String headType;

    @JsonProperty("head_desc")
    private String headDesc;

    @JsonProperty("relation")
    private String relation;

    @JsonProperty("tail")
    private String tail;

    @JsonProperty("tail_type")
    private String tailType;

    @JsonProperty("tail_desc")
    private String tailDesc;
}

package com.gengzi.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

@Data
public class OpenSearchExpandDocument {

    private String id;
    @JsonProperty("content")
    private String content;
    @JsonProperty("metadata")
    private Map<String,Object> metadata;


    private String type;
    @JsonProperty("user_id")
    private String userId;
    @JsonProperty("created_time")
    private String createTime;
    @JsonProperty("updated_time")
    private String updateTime;
    @JsonProperty("confidence")
    private Float confidence;
    @JsonProperty("q_1024_vec")
    private float[] q1024Vec;

}

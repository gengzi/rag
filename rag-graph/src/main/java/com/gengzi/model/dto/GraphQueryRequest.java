package com.gengzi.model.dto;

import lombok.Data;

@Data
public class GraphQueryRequest {
    private String question;
    private Integer neighborDepth;
    private Integer limit;
}

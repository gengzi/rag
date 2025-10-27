package com.gengzi.reranker.request;


import lombok.Data;

import java.util.List;

@Data
// 1. 定义重排序模型的核心输入（T 的具体类型）
public class RerankInstructions {
    private String model;
    private String query;
    private List<String> documents;
    private String instruction;


}
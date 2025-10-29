package com.gengzi.request;

import lombok.Data;

@Data
public class EvaluateDocument {

    private String documentId;
    private String chunkId;
    private String content;

}

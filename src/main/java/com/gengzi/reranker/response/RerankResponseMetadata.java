package com.gengzi.reranker.response;

import org.springframework.ai.model.AbstractResponseMetadata;
import org.springframework.ai.model.ResponseMetadata;

import java.util.Map;

public class RerankResponseMetadata extends AbstractResponseMetadata implements ResponseMetadata {


    public RerankResponseMetadata(Map map) {
        this.map.putAll(map);
    }
}

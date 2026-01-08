package com.gengzi.service.graph;

import com.gengzi.model.dto.GraphQueryResponse;

public interface GraphRetrievalService {
    GraphQueryResponse retrieveChunks(String question, Integer neighborDepth, Integer limit);
}

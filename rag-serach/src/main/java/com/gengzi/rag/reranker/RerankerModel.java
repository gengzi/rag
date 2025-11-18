package com.gengzi.rag.reranker;


import com.gengzi.rag.reranker.request.RerankModelRequest;
import com.gengzi.rag.reranker.response.RerankerModelResponse;
import org.springframework.ai.model.Model;

public interface RerankerModel extends Model<RerankModelRequest, RerankerModelResponse> {


    @Override
    RerankerModelResponse call(RerankModelRequest request);
}

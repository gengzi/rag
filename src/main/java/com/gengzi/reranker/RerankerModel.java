package com.gengzi.reranker;

import com.gengzi.reranker.request.RerankModelRequest;
import com.gengzi.reranker.response.RerankerModelResponse;
import org.springframework.ai.model.Model;

public interface RerankerModel extends Model<RerankModelRequest, RerankerModelResponse> {


    @Override
    RerankerModelResponse call(RerankModelRequest request);
}

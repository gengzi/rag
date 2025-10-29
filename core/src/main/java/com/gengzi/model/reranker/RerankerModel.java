package com.gengzi.model.reranker;

import com.gengzi.model.reranker.request.RerankModelRequest;
import com.gengzi.model.reranker.response.RerankerModelResponse;
import org.springframework.ai.model.Model;

public interface RerankerModel extends Model<RerankModelRequest, RerankerModelResponse> {


    @Override
    RerankerModelResponse call(RerankModelRequest request);
}

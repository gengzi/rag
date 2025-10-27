package com.gengzi.reranker.request;


import lombok.Data;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.ModelRequest;

@Data
public class RerankModelRequest implements ModelRequest<RerankInstructions> {


    private RerankInstructions rerankInstructions;
    private RerankerRequestModelOptions rerankerRequestModelOptions;

    public RerankModelRequest(RerankInstructions rerankInstructions, RerankerRequestModelOptions rerankerRequestModelOptions) {
        this.rerankInstructions = rerankInstructions;
        this.rerankerRequestModelOptions = rerankerRequestModelOptions;
    }

    /**
     * Retrieves the instructions or input required by the AI model.
     *
     * @return the instructions or input required by the AI model
     */
    @Override
    public RerankInstructions getInstructions() {
        return rerankInstructions;
    }

    /**
     * Retrieves the customizable options for AI model interactions.
     *
     * @return the customizable options for AI model interactions
     */
    @Override
    public ModelOptions getOptions() {
        return rerankerRequestModelOptions;
    }
}
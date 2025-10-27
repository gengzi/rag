package com.gengzi.reranker.response;

public class RerankResultData {

    private final String documentText;
    private final Integer index;
    private final Double relevanceScore;

    public RerankResultData(String documentText, Integer index, Double relevanceScore) {
        this.documentText = documentText;
        this.index = index;
        this.relevanceScore = relevanceScore;
    }

    // getter
    public String getDocumentText() {
        return documentText;
    }

    public Integer getIndex() {
        return index;
    }

    public Double getRelevanceScore() {
        return relevanceScore;
    }
}

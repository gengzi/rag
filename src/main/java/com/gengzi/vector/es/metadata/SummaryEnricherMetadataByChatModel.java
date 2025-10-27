package com.gengzi.vector.es.metadata;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SummaryEnricherMetadataByChatModel {

    private final SummaryMetadataEnricher enricher;

    public SummaryEnricherMetadataByChatModel(SummaryMetadataEnricher enricher) {
        this.enricher = enricher;
    }

    public List<Document> enrichDocuments(List<Document> documents) {
        return this.enricher.apply(documents);
    }
}
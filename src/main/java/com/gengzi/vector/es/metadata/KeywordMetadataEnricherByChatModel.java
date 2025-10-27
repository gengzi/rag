package com.gengzi.vector.es.metadata;


import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KeywordMetadataEnricherByChatModel {



    private final OpenAiChatModel openAiChatModel ;

    public KeywordMetadataEnricherByChatModel(OpenAiChatModel openAiChatModel) {
        this.openAiChatModel = openAiChatModel;
    }

    public  List<Document> enrichDocuments(List<Document> documents) {
//        KeywordMetadataEnricher enricher = KeywordMetadataEnricher.builder(openAiChatModel)
//                .keywordCount(5)
//                .build();

        // Or use custom templates
        KeywordMetadataEnricher enricher = KeywordMetadataEnricher.builder(openAiChatModel)
                .keywordsTemplate(new PromptTemplate("内容：{context_str} 请求根据上述内容生成3个关键词，以逗号分割. 关键词:"))
                .build();
        return enricher.apply(documents);
    }

}

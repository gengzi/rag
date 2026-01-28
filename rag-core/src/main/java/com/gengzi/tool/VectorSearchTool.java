package com.gengzi.tool;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量搜索工具 - 在向量数据库中进行语义搜索
 */
@Component
public class VectorSearchTool {

    private final VectorStore vectorStore;

    @Autowired(required = false)
    public VectorSearchTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = "在知识库中搜索相关信息。使用语义搜索找到与问题最相关的文档内容。参数query是要搜索的问题或关键词。")
    public String searchKnowledgeBase(String query) {
        if (vectorStore == null) {
            return "向量数据库未配置，无法执行搜索";
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(5)
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);

            if (results == null || results.isEmpty()) {
                return "未找到相关信息";
            }

            return results.stream()
                    .map(doc -> {
                        String content = doc.getText();
                        var metadata = doc.getMetadata();
                        StringBuilder sb = new StringBuilder();
                        sb.append("相关内容:\n");
                        sb.append(content);
                        if (metadata != null && !metadata.isEmpty()) {
                            sb.append("\n来源: ").append(metadata.toString());
                        }
                        return sb.toString();
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }

    @Tool(description = "在知识库中搜索指定数量的相关文档。可以自定义返回结果的数量。")
    public String searchWithLimit(String query, int topK) {
        if (vectorStore == null) {
            return "向量数据库未配置，无法执行搜索";
        }

        if (topK <= 0 || topK > 20) {
            return "topK 参数必须在 1-20 之间";
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);

            if (results == null || results.isEmpty()) {
                return "未找到相关信息";
            }

            return "找到 " + results.size() + " 个相关结果:\n\n" +
                    results.stream()
                            .map(doc -> "- " + doc.getText().substring(0, Math.min(200, doc.getText().length()))
                                    + "...")
                            .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }
}

package com.gengzi.rag.tool;


import com.alibaba.cloud.ai.toolcalling.common.interfaces.SearchService;
import com.alibaba.cloud.ai.toolcalling.tavily.TavilySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchTools {

    public static final String SEARCHRESULT = "搜索问题: %s \n 以下是搜索结果: \n\n %s";
    public static final String SEARCHRESULT_CONTENT = "标题: %s\n 内容:%s\n url:%s\n ";
    private static final Logger logger = LoggerFactory.getLogger(SearchTools.class);
    @Autowired
    private TavilySearchService tavilySearch;

    @Tool(description = "根据用户提问信息，检索互联网上的信息")
    public String search(@ToolParam(description = "用户提问", required = true) String query) {
        logger.debug("用户query: {}", query);
        StringBuilder sb = new StringBuilder();
        SearchService.Response response = tavilySearch.query(query);
        List<SearchService.SearchContent> results = response.getSearchResult().results();
        results.forEach(result -> {
            logger.debug("title: {}", result.title());
            logger.debug("content: {}", result.content());
            String content = String.format(SEARCHRESULT_CONTENT, result.title(), result.content(), result.url());
            sb.append(content);
        });
        return String.format(SEARCHRESULT, query, sb.toString());
    }
}

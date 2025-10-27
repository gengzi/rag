package com.gengzi.search.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 查询翻译
 *
 * <p>
 * 查询重写：将原始问题重构，移除无关信息，精确的文本表达
 * 查询分解：将查询拆分为多个子查询
 * 查询澄清：逐步细化和明确用户的问题
 * 查询扩展：利用hyde生成假设性文档
 *
 * 前置化：在用户问题输入完成后，在前端页面直接改写，让用户看到改写的问题
 */
@Component
public class QueryTranslation implements QueryTransformer {
    private static final Logger logger = LoggerFactory.getLogger(QueryTranslation.class);

    @Value("${prompt.queryTranslation.rewriteSysPromptStr}")
    private String sysPromptStr;

    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;


    @Override
    public Query transform(Query query) {
        logger.info("查询翻译-用户问题:{} ", query.text());

        String text = query.text();
        SystemMessage systemMessage = new SystemMessage(sysPromptStr);
        UserMessage userMessage = new UserMessage(text);
        String response = chatModel.call(systemMessage,userMessage);
        logger.info("查询翻译-结果：{}",response);
        return query.mutate().text(response).build();
    }









}

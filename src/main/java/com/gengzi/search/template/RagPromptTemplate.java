package com.gengzi.search.template;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

/**
 * 适用于rag 系统的提示词模板
 */
@Component
public class RagPromptTemplate {


    public PromptTemplate ragPromptTemplate() {

        PromptTemplate customPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template("""
                        在给定上下文信息且没有其他知识的情况下，回答问题
                        用户问题：
                        <query>
                        上下文内容如下:
                        ---------------------
                        <context>
                        ---------------------
                        "
                        """)
                .build();
        return customPromptTemplate;
    }

    public PromptTemplate ragPromptTemplateNoContext() {

        PromptTemplate customPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template("""
                        在给定上下文信息且没有其他知识的情况下，回答问题
                        用户问题：
                        <query>
                        上下文内容如下:
                        ---------------------
                      
                        ---------------------
                        "
                        """)
                .build();
        return customPromptTemplate;
    }
}

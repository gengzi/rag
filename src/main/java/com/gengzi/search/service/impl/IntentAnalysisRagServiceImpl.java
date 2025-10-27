package com.gengzi.search.service.impl;


import com.gengzi.search.service.IntentAnalysisRagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class IntentAnalysisRagServiceImpl implements IntentAnalysisRagService {


    @Autowired
    @Qualifier("deepseekChatClient")
    private ChatClient chatClient;

    @Autowired
    private ChatMemory chatMemory;

    @Value("${prompt.queryTranslation.intentAnalysisSysPromptStr}")
    private String intentAnalysisSysPromptStr;

    /**
     * 意图识别
     * 结合用户的聊天记录上下文，判断用户是否需要追问
     *
     * @param question
     * @return
     */
    @Override
    public String intentAnalysis(String question, String conversationId) {
        // 根据会话id获取之前的聊天记录信息
        List<Message> messages = chatMemory.get(conversationId);
        // 将当前问题提交给大模型

        Flux<String> content = chatClient.prompt()
                .messages(new SystemMessage(intentAnalysisSysPromptStr))
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();

        String judgeResult = content.collectList().map(str -> String.join("", str)).block();
        if (judgeResult != null && judgeResult.contains("需要澄清")) {

            // 提取缺失信息（格式："需要澄清，缺失：类型、地区" → 提取"类型、地区"）
            String missingInfo = judgeResult.replace("需要澄清，缺失：", "").trim();

            // 生成追问话术
            String clarificationMessage = "为了更准确回答，请补充以下信息：" + missingInfo;
            return clarificationMessage;
        }
        return "";
    }
}

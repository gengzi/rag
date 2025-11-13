package com.gengzi.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.gengzi.tool.ppt.config.AiPPTConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 人类反馈节点
 *
 * 该节点主要用来中断，用户对于大纲生成是否满意
 *
 */
@Component
public class HumanFeedbackNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(HumanFeedbackNode.class);



    @Autowired
    private AiPPTConfig aiPPTConfig;

    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;


    @JsonPropertyOrder({"hasIssue","summary"})
    record FeedbackData(boolean hasIssue, String summary) {
    }



    @Override
    public Map<String, Object> apply(OverAllState state) {
        HashMap<String, Object> resultMap = new HashMap<>();
        Map<String, Object> feedBackData = state.humanFeedback().data();
        final String feedback = (String) feedBackData.getOrDefault("feedback", "");
        final String conversationId = state.value("conversationId", "");
        PromptTemplate promptTemplate = new PromptTemplate(aiPPTConfig.getHumanFeedbackPrompt());
        // 获取大纲节点输出内容
        Optional<Object> outlineContent = state.value("outlineGenNode_content");
        if (outlineContent.isPresent()) {
            logger.info("用户反馈节点，获取到大纲内容：{}", outlineContent.get());
        }
        logger.info("用户反馈节点执行，用户反馈：{}",feedback);
        // 调用大模型分析用户是否满意和改进意见
        FeedbackData entity = this.chatClient.prompt()
                .system(promptTemplate.getTemplate())
//                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(feedback)
                .call().entity(FeedbackData.class);
        String nextStep = "pptGenNode";
        if(!entity.hasIssue()){
            // 满意，进入生成节点
            nextStep = "motherboadrChoiceNode";
        }else{
            // 不满意，进入大纲生成节点
            nextStep = "outlineGenNode";
        }
        resultMap.put("human_next_node", nextStep);
        resultMap.put("human_feedback", feedback);
        resultMap.put("feedback_data", entity);
        logger.info("humanfeedback node -> {} node", nextStep);
        return resultMap;
    }
}
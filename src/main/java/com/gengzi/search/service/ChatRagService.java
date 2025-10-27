package com.gengzi.search.service;

import com.gengzi.request.RagChatCreateReq;
import com.gengzi.request.RagChatReq;
import com.gengzi.request.RagChatSearchReq;
import com.gengzi.response.ChatAnswerResponse;
import com.gengzi.response.ConversationDetailsResponse;
import com.gengzi.response.ConversationResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatRagService {


    /**
     * rag对话
     *
     * @param ragChatReq rag对话参数
     * @return
     */
    Flux<ChatAnswerResponse> chatRag(RagChatReq ragChatReq);


    /**
     * rag对话评估
     *
     * @param ragChatReq rag对话参数
     * @return
     */
    ChatAnswerResponse chatRagEvaluate(RagChatReq ragChatReq);

    /**
     * rag对话
     *
     * @param ragChatReq rag对话参数
     * @return
     */
    Flux<ChatAnswerResponse> chatSearch(RagChatSearchReq ragChatReq);


    String chatRagCreate(RagChatCreateReq req);

    List<ConversationResponse> chatRagAll();

    ConversationDetailsResponse chatRagMsgList(String conversationId);


    /**
     * 创建训练集会话
     *
     * @return
     */
    String createEvaluateConversation(String kbId);
}

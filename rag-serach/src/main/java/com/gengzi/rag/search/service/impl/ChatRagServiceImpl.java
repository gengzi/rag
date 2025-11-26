package com.gengzi.rag.search.service.impl;

import com.gengzi.dao.User;
import com.gengzi.dao.repository.UserRepository;
import com.gengzi.enums.MessageType;
import com.gengzi.rag.context.RagChatContext;
import com.gengzi.rag.search.service.ChatRagService;
import com.gengzi.request.ChatReq;
import com.gengzi.response.*;
import com.gengzi.utils.IdUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatRagServiceImpl implements ChatRagService {


    @Autowired
    @Qualifier("deepseekChatClientByRag")
    private ChatClient chatClientByRag;

    @Autowired
    private ChatClient deepseekChatClientByRagNoMemory;

    @Autowired
    private UserRepository userRepository;


    /**
     * rag对话
     *
     * @param ragChatReq rag对话参数
     * @return
     */
    @Override
    public Flux<ChatMessageResponse> chatRag(ChatReq ragChatReq, String userId) {

        // 根据userid 获取用户支持的知识库列表信息
        User byId = userRepository.findUserByUsername(userId);
        if (byId == null) {
            return Flux.error(new BusinessException(ResultCode.USER_NOT_EXIST));
//            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        // 添加知识库id过滤元数据
        String knowledgeIds = byId.getKnowledgeIds();
        String filterKbIds = Arrays.stream(knowledgeIds.split(",")).map(v -> "'" + v.trim() + "'").collect(Collectors.joining(","));
        String filter = "kbId in [" + filterKbIds + "]";

        String question = ragChatReq.getQuery();
        String conversationId = ragChatReq.getConversationId();
        String chatId = IdUtils.generate();
        RagChatContext ragChatContext = new RagChatContext(chatId, conversationId, userId);

        Flux<ChatClientResponse> chatClientResponseFlux = chatClientByRag.prompt()
                .user(question)
                .system("你是一个专注于知识库问答的 RAG（检索增强生成）助手，所有回答必须严格遵循以下规则：\\n\\n### 1. 核心目标 \\n- 仅基于用户提供的知识库内容生成回答，不依赖外部常识或个人记忆。\\n- 回答需精准匹配知识库信息，避免添加未提及的推测、延伸或主观观点。\\n - 需完全保留知识库中 $ 开头、$ 结尾的公式格式不删除或替换 符号，确保公式与原文一致 \\n### 2. 知识边界处理 \\n- 若用户问题可从知识库中找到直接或间接答案，需先引用相关内容要点，再组织成自然语言回复。\\n- 若知识库中无相关信息，需明确告知用户 “当前知识库暂未涵盖该问题的相关内容”，不随意编造答案。\\n- 若知识库内容存在冲突或歧义，需列出不同观点并说明 “知识库中对此问题存在多种表述”，不强行统一结论。\\n\\n### 3. 输出格式要求 \\n- 回答结构清晰，优先分点阐述核心信息（若内容较多）。\\n- 关键数据、定义或结论需明确标注来源（如 “根据知识库中《XX 文档》第 X 点”），增强可信度。\\n- 语言简洁易懂，避免使用过于专业的术语；若必须使用，需结合知识库内容给出解释。\\n\\n### 4. 交互原则 \\n- 若用户问题模糊，需先询问补充信息（如 “为更精准回答，请明确你想了解的是 XX 领域 / XX 场景下的内容”），再基于知识库回应。\\n- 不主动扩展话题，仅围绕用户问题及知识库内容展开，确保对话聚焦。\\n")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(advisorSpec -> advisorSpec.param(RagChatContext.RAG_CHAT_CONTEXT, ragChatContext))
                .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filter))
                .stream()
                .chatClientResponse();


        return chatClientResponseFlux.index()
                .map(result -> {
                    long sequenceNumber = result.getT1() + 1;
                    ChatClientResponse chatClientResponse = result.getT2();
                    ChatResponse chatResponse = chatClientResponse.chatResponse();
                    ChatMessageResponse chatAnswerResponse = new ChatMessageResponse();
                    LlmTextRes llmTextRes = new LlmTextRes();
                    llmTextRes.setAnswer(chatResponse.getResult().getOutput().getText());
                    // 从上下文中获取参考文档
                    Map<String, Object> context = chatClientResponse.context();
                    List<Document> documents = (List<Document>) context.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
                    RagReference ragReference = RagReference.listDocumentToRagReference(documents);
                    llmTextRes.setReference(ragReference);
                    chatAnswerResponse.setContent(llmTextRes);
                    chatAnswerResponse.setMessageType(MessageType.LLM_RESPONSE.getTypeCode());
                    // 返回用户问题后，还需要拼接上参考的文档信息，文档链接
                    return chatAnswerResponse;
                });

    }

    /**
     * agent节点调用
     *
     * @param ragChatReq
     * @param userId
     * @return
     */
    @Override
    public Flux<ChatResponse> chatRagByAgent(ChatReq ragChatReq, String userId) {
        // 根据userid 获取用户支持的知识库列表信息
        User byId = userRepository.findUserByUsername(userId);
        if (byId == null) {
            return Flux.error(new BusinessException(ResultCode.USER_NOT_EXIST));
        }
        // 添加知识库id过滤元数据
        String knowledgeIds = byId.getKnowledgeIds();
        String filterKbIds = Arrays.stream(knowledgeIds.split(",")).map(v -> "'" + v.trim() + "'").collect(Collectors.joining(","));
        String filter = "kbId in [" + filterKbIds + "]";

        String question = ragChatReq.getQuery();
        String conversationId = ragChatReq.getConversationId();
        String chatId = IdUtils.generate();
        RagChatContext ragChatContext = new RagChatContext(chatId, conversationId, userId);

        Flux<ChatResponse> chatResponseFlux = deepseekChatClientByRagNoMemory.prompt()
                .user(question)
                .system("你是一个专注于知识库问答的 RAG（检索增强生成）助手，所有回答必须严格遵循以下规则：\\n\\n### 1. 核心目标 \\n- 仅基于用户提供的知识库内容生成回答，不依赖外部常识或个人记忆。\\n- 回答需精准匹配知识库信息，避免添加未提及的推测、延伸或主观观点。\\n - 需完全保留知识库中 $ 开头、$ 结尾的公式格式不删除或替换 符号，确保公式与原文一致 \\n### 2. 知识边界处理 \\n- 若用户问题可从知识库中找到直接或间接答案，需先引用相关内容要点，再组织成自然语言回复。\\n- 若知识库中无相关信息，需明确告知用户 “当前知识库暂未涵盖该问题的相关内容”，不随意编造答案。\\n- 若知识库内容存在冲突或歧义，需列出不同观点并说明 “知识库中对此问题存在多种表述”，不强行统一结论。\\n\\n### 3. 输出格式要求 \\n- 回答结构清晰，优先分点阐述核心信息（若内容较多）。\\n- 关键数据、定义或结论需明确标注来源（如 “根据知识库中《XX 文档》第 X 点”），增强可信度。\\n- 语言简洁易懂，避免使用过于专业的术语；若必须使用，需结合知识库内容给出解释。\\n\\n### 4. 交互原则 \\n- 若用户问题模糊，需先询问补充信息（如 “为更精准回答，请明确你想了解的是 XX 领域 / XX 场景下的内容”），再基于知识库回应。\\n- 不主动扩展话题，仅围绕用户问题及知识库内容展开，确保对话聚焦。\\n")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(advisorSpec -> advisorSpec.param(RagChatContext.RAG_CHAT_CONTEXT, ragChatContext))
                .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filter))
                .stream()
                .chatResponse();
        return chatResponseFlux;
    }


}

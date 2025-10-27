package com.gengzi.search.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.gengzi.context.RagChatContext;
import com.gengzi.dao.Conversation;
import com.gengzi.dao.User;
import com.gengzi.dao.entity.RagChatMessage;
import com.gengzi.dao.entity.RagReference;
import com.gengzi.dao.repository.ConversationRepository;
import com.gengzi.dao.repository.UserRepository;
import com.gengzi.request.RagChatCreateReq;
import com.gengzi.request.RagChatReq;
import com.gengzi.request.RagChatSearchReq;
import com.gengzi.response.ChatAnswerResponse;
import com.gengzi.response.ConversationDetailsResponse;
import com.gengzi.response.ConversationResponse;
import com.gengzi.response.ResultCode;
import com.gengzi.search.service.ChatRagService;
import com.gengzi.security.BusinessException;
import com.gengzi.security.UserPrincipal;
import com.gengzi.tool.DateTimeTools;
import com.gengzi.utils.IdUtils;
import com.gengzi.utils.UserDetails;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatRagServiceImpl implements ChatRagService {


    @Autowired
    private VectorStore vectorStore;

    @Autowired
    @Qualifier("deepseekChatClient")
    private ChatClient chatClient;

    @Autowired
    @Qualifier("deepseekChatClientByRag")
    private ChatClient chatClientByRag;

    @Autowired
    @Qualifier("deepseekChatClientByRagSearch")
    private ChatClient chatClientByRagSearch;

    @Autowired
    private ConversationRepository conversationRepository;


    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private UserRepository userRepository;

    /**
     * rag对话
     *
     * @param ragChatReq rag对话参数
     * @return
     */
    @Override
    public Flux<ChatAnswerResponse> chatRag(RagChatReq ragChatReq) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal details = (UserPrincipal) authentication.getPrincipal();

        final String userId = details.getId();

        if (StrUtil.isBlank(ragChatReq.getConversationId())) {
            // 新建一个聊天窗口会话
            String conversationId = chatRagCreate(ragChatReq, userId);
            ragChatReq.setConversationId(conversationId);
        }
        // 根据userid 获取用户支持的知识库列表信息
        Optional<User> byId = userRepository.findById(userId);
        if (!byId.isPresent()) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        String knowledgeIds = byId.get().getKnowledgeIds();
        String filterKbIds = Arrays.stream(knowledgeIds.split(",")).map(v -> "'" + v.trim() + "'").collect(Collectors.joining(","));
        String filter = "kbId in [" + filterKbIds + "]";

        // 获取用户信息
        // 获取用户问题
        String question = ragChatReq.getQuestion();
        // 会话id
        String conversationId = ragChatReq.getConversationId();
        String chatId = IdUtils.generate();
        RagChatContext ragChatContext = new RagChatContext(chatId, conversationId, userId);
//        Flux<String> content = chatClientByRag.prompt()
//                .user(question)
//                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
//                .advisors(advisorSpec -> advisorSpec.param(RagChatContext.RAG_CHAT_CONTEXT, ragChatContext))
//                .stream()
//                .content();
        Flux<ChatClientResponse> chatClientResponseFlux = chatClientByRag.prompt()
                .user(question)
                .tools(dateTimeTools)
                .system("你是一个专注于知识库问答的 RAG（检索增强生成）助手，所有回答必须严格遵循以下规则：\\n\\n### 1. 核心目标 \\n- 仅基于用户提供的知识库内容生成回答，不依赖外部常识或个人记忆。\\n- 回答需精准匹配知识库信息，避免添加未提及的推测、延伸或主观观点。\\n - 需完全保留知识库中 $ 开头、$ 结尾的公式格式不删除或替换 符号，确保公式与原文一致 \\n### 2. 知识边界处理 \\n- 若用户问题可从知识库中找到直接或间接答案，需先引用相关内容要点，再组织成自然语言回复。\\n- 若知识库中无相关信息，需明确告知用户 “当前知识库暂未涵盖该问题的相关内容”，不随意编造答案。\\n- 若知识库内容存在冲突或歧义，需列出不同观点并说明 “知识库中对此问题存在多种表述”，不强行统一结论。\\n\\n### 3. 输出格式要求 \\n- 回答结构清晰，优先分点阐述核心信息（若内容较多）。\\n- 关键数据、定义或结论需明确标注来源（如 “根据知识库中《XX 文档》第 X 点”），增强可信度。\\n- 语言简洁易懂，避免使用过于专业的术语；若必须使用，需结合知识库内容给出解释。\\n\\n### 4. 交互原则 \\n- 若用户问题模糊，需先询问补充信息（如 “为更精准回答，请明确你想了解的是 XX 领域 / XX 场景下的内容”），再基于知识库回应。\\n- 不主动扩展话题，仅围绕用户问题及知识库内容展开，确保对话聚焦。\\n")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(advisorSpec -> advisorSpec.param(RagChatContext.RAG_CHAT_CONTEXT, ragChatContext))
                .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filter))
                .stream()
                .chatClientResponse();
        ChatAnswerResponse done = new ChatAnswerResponse();
        done.setAnswer("[DONE]");


        return chatClientResponseFlux.index()
                .map(result -> {
                    long sequenceNumber = result.getT1() + 1;
                    ChatClientResponse chatClientResponse = result.getT2();
                    ChatAnswerResponse chatAnswerResponse = new ChatAnswerResponse();
                    ChatResponse chatResponse = chatClientResponse.chatResponse();
                    chatAnswerResponse.setAnswer(chatResponse.getResult().getOutput().getText());
                    Map<String, Object> context = chatClientResponse.context();
                    List<Document> documents = (List<Document>) context.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
                    RagReference ragReference = RagReference.listDocumentToRagReference(documents);
                    chatAnswerResponse.setReference(ragReference);
                    done.setReference(ragReference);
                    // 返回用户问题后，还需要拼接上参考的文档信息，文档链接
                    return chatAnswerResponse;
                }).concatWith(Mono.just(done));

    }

    /**
     * rag对话评估
     *
     * @param ragChatReq rag对话参数
     * @return
     */
    @Override
    public ChatAnswerResponse chatRagEvaluate(RagChatReq ragChatReq) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal details = (UserPrincipal) authentication.getPrincipal();

        final String userId = details.getId();

        if (StrUtil.isBlank(ragChatReq.getConversationId())) {
            // 新建一个聊天窗口会话
            String conversationId = chatRagCreate(ragChatReq, userId);
            ragChatReq.setConversationId(conversationId);
        }
        // 获取用户问题
        String question = ragChatReq.getQuestion();
        // 会话id
        String conversationId = ragChatReq.getConversationId();
        String chatId = IdUtils.generate();
        RagChatContext ragChatContext = new RagChatContext(chatId, conversationId, userId);
        ChatClientResponse chatClientResponse = chatClientByRag.prompt()
                .user(question)
                .system("你是一个专注于知识库问答的 RAG（检索增强生成）助手，所有回答必须严格遵循以下规则：\\n\\n### 1. 核心目标 \\n- 仅基于用户提供的知识库内容生成回答，不依赖外部常识或个人记忆。\\n- 回答需精准匹配知识库信息，避免添加未提及的推测、延伸或主观观点。\\n - 需完全保留知识库中 $ 开头、$ 结尾的公式格式不删除或替换 符号，确保公式与原文一致 \\n - 回答开头禁止使用 “根据给定的上下文内容” 等表述，需直接以 “根据知识库内容” 或具体文档信息切入。 \\n ### 2. 知识边界处理 \\n- 若用户问题可从知识库中找到直接或间接答案，需先引用相关内容要点，再组织成自然语言回复。\\n- 若知识库中无相关信息，需明确告知用户 “当前知识库暂未涵盖该问题的相关内容”，不随意编造答案。\\n- 若知识库内容存在冲突或歧义，需列出不同观点并说明 “知识库中对此问题存在多种表述”，不强行统一结论。\\n\\n### 3. 输出格式要求 \\n- 回答结构清晰，优先分点阐述核心信息（若内容较多）。\\n- 关键数据、定义或结论需明确标注来源（如 “根据知识库中《XX 文档》第 X 点”），无需单独以 “严格依据上下文原文：‘xxxx’” 结尾,增强可信度。\\n- 语言简洁易懂，避免使用过于专业的术语；若必须使用，需结合知识库内容给出解释。\\n - 回答中禁止使用固定结尾话术，相关知识库原文信息需自然融入解释过程，确保整体表述连贯流畅，无生硬割裂的引用句式  \\n### 4. 交互原则 \\n- 若用户问题模糊，需先询问补充信息（如 “为更精准回答，请明确你想了解的是 XX 领域 / XX 场景下的内容”），再基于知识库回应。\\n- 不主动扩展话题，仅围绕用户问题及知识库内容展开，确保对话聚焦。\\n")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(advisorSpec -> advisorSpec.param(RagChatContext.RAG_CHAT_CONTEXT, ragChatContext))
                .call()
                .chatClientResponse();
        ChatAnswerResponse chatAnswerResponse = new ChatAnswerResponse();
        ChatResponse chatResponse = chatClientResponse.chatResponse();
        chatAnswerResponse.setAnswer(chatResponse.getResult().getOutput().getText());
        Map<String, Object> context = chatClientResponse.context();
        List<Document> documents = (List<Document>) context.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        RagReference ragReference = RagReference.listDocumentToRagReference(documents);
        chatAnswerResponse.setReference(ragReference);
        return chatAnswerResponse;
    }


    @Override
    public Flux<ChatAnswerResponse> chatSearch(RagChatSearchReq req) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal details = (UserPrincipal) authentication.getPrincipal();
        final String userId = details.getId();
        Flux<ChatClientResponse> chatClientResponseFlux = chatClientByRagSearch.prompt()
                .user(req.getQuery())
                .system("你是一个专注于知识库问答的 RAG（检索增强生成）助手，所有回答必须严格遵循以下规则：\\n\\n### 1. 核心目标 \\n- 仅基于用户提供的知识库内容生成回答，不依赖外部常识或个人记忆。\\n- 回答需精准匹配知识库信息，避免添加未提及的推测、延伸或主观观点。\\n\\n### 2. 知识边界处理 \\n- 若用户问题可从知识库中找到直接或间接答案，需先引用相关内容要点，再组织成自然语言回复。\\n- 若知识库中无相关信息，需明确告知用户 “当前知识库暂未涵盖该问题的相关内容”，不随意编造答案。\\n- 若知识库内容存在冲突或歧义，需列出不同观点并说明 “知识库中对此问题存在多种表述”，不强行统一结论。\\n\\n### 3. 输出格式要求 \\n- 回答结构清晰，优先分点阐述核心信息（若内容较多）。\\n- 关键数据、定义或结论需明确标注来源（如 “根据知识库中《XX 文档》第 X 点”），增强可信度。\\n- 语言简洁易懂，避免使用过于专业的术语；若必须使用，需结合知识库内容给出解释。\\n\\n### 4. 交互原则 \\n- 若用户问题模糊，需先询问补充信息（如 “为更精准回答，请明确你想了解的是 XX 领域 / XX 场景下的内容”），再基于知识库回应。\\n- 不主动扩展话题，仅围绕用户问题及知识库内容展开，确保对话聚焦。")
                .stream()
                .chatClientResponse();
        ChatAnswerResponse done = new ChatAnswerResponse();
        done.setAnswer("[DONE]");
        return chatClientResponseFlux.index()
                .map(result -> {
                    long sequenceNumber = result.getT1() + 1;
                    ChatClientResponse chatClientResponse = result.getT2();
                    ChatAnswerResponse chatAnswerResponse = new ChatAnswerResponse();
                    ChatResponse chatResponse = chatClientResponse.chatResponse();
                    chatAnswerResponse.setAnswer(chatResponse.getResult().getOutput().getText());
                    Map<String, Object> context = chatClientResponse.context();
                    List<Document> documents = (List<Document>) context.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
                    RagReference ragReference = RagReference.listDocumentToRagReference(documents);
                    chatAnswerResponse.setReference(ragReference);
                    done.setReference(ragReference);
                    // 返回用户问题后，还需要拼接上参考的文档信息，文档链接
                    return chatAnswerResponse;
                }).concatWith(Mono.just(done));
    }

    /**
     * rag对话窗口创建
     *
     * @param req
     */
    @Override
    public String chatRagCreate(RagChatCreateReq req) {
        // 向会话表插入一条会话信息
        // 会话id
        String conversationId = IdUtils.generate();
        LocalDateTime now = LocalDateTime.now();
        // 为LocalDateTime指定时区（这里使用系统默认时区）
        ZonedDateTime zonedDateTime = now.atZone(ZoneId.systemDefault());
        // 获取毫秒级时间戳（从1970-01-01T00:00:00Z开始的毫秒数）
        long millis = zonedDateTime.toInstant().toEpochMilli();
        conversationRepository.save(Conversation.builder()
                .id(conversationId)
                .knowledgebaseId(req.getKbId())
                .createDate(now)
                .createTime(millis)
                .updateDate(now)
                .updateTime(millis)
                .name(req.getChatName())
                .message("[]")
                .reference("[]")
                .userId(UserDetails.getUser().getId())
                .build());
        return conversationId;
    }

    /**
     * @return
     */
    @Override
    public List<ConversationResponse> chatRagAll() {
        UserPrincipal user = UserDetails.getUser();
        List<Conversation> conversations = conversationRepository.findByUserId(user.getId());
        ArrayList<ConversationResponse> conversationResponses = new ArrayList<>();
        conversations.forEach(conversation -> {
            ConversationResponse response = ConversationResponse.builder()
                    .id(conversation.getId())
                    .knowledgebaseId(conversation.getKnowledgebaseId())
                    .name(conversation.getName())
                    .createTime(conversation.getCreateTime())
                    .createDate(conversation.getCreateDate())
                    .updateTime(conversation.getUpdateTime())
                    .updateDate(conversation.getUpdateDate())
                    .build();
            conversationResponses.add(response);
        });
        return conversationResponses;
    }

    /**
     * @param conversationId
     * @return
     */
    @Override
    public ConversationDetailsResponse chatRagMsgList(String conversationId) {
        ConversationDetailsResponse conversationDetailsResponse = new ConversationDetailsResponse();
        Optional<Conversation> conversationRepositoryById = conversationRepository.findById(conversationId);
        if (conversationRepositoryById.isPresent()) {
            Conversation conversation = conversationRepositoryById.get();
            conversationDetailsResponse.setId(conversationId);
            conversationDetailsResponse.setName(conversation.getName());
            String reference = conversation.getReference();
            // 获取引用文档信息
            List<RagReference> ragReferences = JSONUtil.toList(reference, RagReference.class);
            // 将引入的文档信息转换成 rag 引用信息
            String message = conversation.getMessage();
            List<RagChatMessage> ragChatMessages = JSONUtil.toList(message, RagChatMessage.class);
            for (RagChatMessage ragChatMessage : ragChatMessages) {
                if (MessageType.ASSISTANT.name().equals(ragChatMessage.getRole())) {
                    String id = ragChatMessage.getId();
                    ragReferences.stream().filter(ragReference -> ragReference.getChatid().equals(id))
                            .findFirst()
                            .ifPresent(ragReference -> ragChatMessage.setRagReference(ragReference));
                }
            }
            conversationDetailsResponse.setMessage(ragChatMessages);
            conversationDetailsResponse.setUpdateTime(conversation.getUpdateTime());
            conversationDetailsResponse.setUpdateDate(conversation.getUpdateDate());
            conversationDetailsResponse.setCreateTime(conversation.getCreateTime());
            conversationDetailsResponse.setCreateDate(conversation.getCreateDate());
            conversationDetailsResponse.setKnowledgebaseId(conversation.getKnowledgebaseId());
            conversationDetailsResponse.setUserId(conversation.getUserId());
        }
        return conversationDetailsResponse;
    }

    /**
     * 创建训练集会话
     *
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createEvaluateConversation(String kbId) {
        String conversationId = IdUtils.generate();
        LocalDateTime now = LocalDateTime.now();
        // 为LocalDateTime指定时区（这里使用系统默认时区）
        ZonedDateTime zonedDateTime = now.atZone(ZoneId.systemDefault());
        // 获取毫秒级时间戳（从1970-01-01T00:00:00Z开始的毫秒数）
        long millis = zonedDateTime.toInstant().toEpochMilli();
        UserPrincipal user = UserDetails.getUser();
        conversationRepository.save(Conversation.builder()
                .id(conversationId)
                .knowledgebaseId(kbId)
                .createDate(now)
                .createTime(millis)
                .updateDate(now)
                .updateTime(millis)
                .name("训练评估数据集" + conversationId)
                .message("[]")
                .reference("[]")
                .userId(user.getId())
                .build());
        return conversationId;
    }

    public String chatRagCreate(RagChatReq req, String userId) {
        // 向会话表插入一条会话信息
        // 会话id
        String conversationId = IdUtils.generate();
        LocalDateTime now = LocalDateTime.now();
        // 为LocalDateTime指定时区（这里使用系统默认时区）
        ZonedDateTime zonedDateTime = now.atZone(ZoneId.systemDefault());
        // 获取毫秒级时间戳（从1970-01-01T00:00:00Z开始的毫秒数）
        long millis = zonedDateTime.toInstant().toEpochMilli();
        conversationRepository.save(Conversation.builder()
                .id(conversationId)
                .createDate(now)
                .createTime(millis)
                .updateDate(now)
                .updateTime(millis)
                .name(req.getQuestion())
                .message("[]")
                .reference("[]")
                .userId(userId)
                .build());
        return conversationId;

    }
}

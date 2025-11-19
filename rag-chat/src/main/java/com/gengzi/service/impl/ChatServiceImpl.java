package com.gengzi.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.gengzi.dao.Message;
import com.gengzi.dao.repository.ConversationRepository;
import com.gengzi.dao.repository.MessageRepository;
import com.gengzi.enums.Agent;
import com.gengzi.rag.search.service.ChatRagService;
import com.gengzi.request.ChatMsgRecordReq;
import com.gengzi.request.ChatReq;
import com.gengzi.response.BusinessException;
import com.gengzi.response.ChatMessage;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.response.ConversationDetailsResponse;
import com.gengzi.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private ConversationRepository conversationRepository;


    @Autowired
    private ChatRagService chatRagService;

    @Autowired
    private MessageRepository messageRepository;


    private void exec(ChatReq req, String userid, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink) {
        // 1,判断会话id是否存在,参数校验
        if (!conversationRepository.findById(req.getConversationId()).isPresent()) {
            sink.tryEmitError(new BusinessException("会话不存在"));
            sink.tryEmitComplete();
        }
        // 2，判断agentid是否存在，并且agentid是否可用
        if (StrUtil.isNotBlank(req.getAgentId()) && Agent.isExist(req.getAgentId())) {
            // 3,存在agentid，执行agent流程
        }

        Flux<ChatMessageResponse> chatMessageResponseFlux = chatRagService.chatRag(req, userid);
        chatMessageResponseFlux.index()
                .doOnNext(tuple -> {
                    logger.info("当前流序号：{}", tuple.getT1());
                    sink.tryEmitNext(ServerSentEvent.builder(tuple.getT2()).build());
                })
                .doOnComplete(() -> sink.tryEmitComplete())
                .doOnError(e ->
                        {
                            logger.error("SSrroE 流错误", e);
                            sink.tryEmitError(e);
                        }
                )
                .subscribe();
    }

    @Override
    public Flux<ServerSentEvent<ChatMessageResponse>> chat(ChatReq req) {
        // 1. 在 WebFlux 主链中获取当前的安全上下文（此时是有效的！）
        return ReactiveSecurityContextHolder.getContext()
                .switchIfEmpty(Mono.error(new RuntimeException("未认证")))
                .flatMapMany(securityContext -> {
                    // 2. 创建 Sinks
                    Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink =
                            Sinks.many().unicast().onBackpressureBuffer();
                    String userId = (String) securityContext.getAuthentication().getPrincipal();
                    // 3. 启动你的异步耗时任务，并把 securityContext 传进去
                    Mono.fromRunnable(() -> exec(req, userId, sink))
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe();

                    return sink.asFlux()
                            .doOnCancel(() -> logger.info("SSE 流被取消"))
                            .doOnError(e -> logger.error("SSrroE 流错误", e));
                });
    }

    /**
     * 获取聊天记录，分页获取
     *
     * @param conversationId
     * @param recordReq
     * @return
     */
    @Override
    public Mono<ConversationDetailsResponse> chatMsgList(String conversationId, ChatMsgRecordReq recordReq) {
        return Mono.fromCallable(() -> {
            if (StrUtil.isBlank(recordReq.getBefore())) {
                List<Message> messageByConversationIdAndLimit = messageRepository.findMessageByConversationIdAndLimit(conversationId, recordReq.getLimit());
                return msgListResult(messageByConversationIdAndLimit);
            }
            List<String> collect = Arrays.stream(recordReq.getBefore().split("_")).collect(Collectors.toList());
            if (collect.size() != 2) {
                return new ConversationDetailsResponse();
            }
            List<Message> messageByConversationIdAndLimitAndNextCursor = messageRepository
                    .findMessageByConversationIdAndLimitAndNextCursor(conversationId, recordReq.getLimit(),
                            Instant.ofEpochMilli(Long.parseLong(collect.get(0))), Long.parseLong(collect.get(1)));
            return msgListResult(messageByConversationIdAndLimitAndNextCursor);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ConversationDetailsResponse msgListResult(List<Message> messageByConversationIdAndLimit) {
        ConversationDetailsResponse conversationDetailsResponse = new ConversationDetailsResponse();
        ArrayList<ChatMessage> chatMessages = new ArrayList<>();
        for (Message message : messageByConversationIdAndLimit) {
//            ChatMessage chatMessage = new ChatMessage();
//            chatMessage.setId(String.valueOf(message.getId()));
//            chatMessage.setRole(message.getMessageRole());
//            chatMessage.setConversationId(message.getConversation());
//            chatMessage.setCreatedAt(message.getCreatedTime().toEpochMilli());
//            chatMessage.setContent(JSONUtil.toList(message.getContent(), ChatMessageResponse.class));
//            chatMessages.add(chatMessage);
            ChatMessage bean = JSONUtil.toBean(message.getContent(), ChatMessage.class);
            chatMessages.add(bean);
        }
        List<ChatMessage> chatMessagesSort = chatMessages.stream().sorted(Comparator.comparing(ChatMessage::getCreatedAt)).collect(Collectors.toList());
        Optional<Message> messageFirst = messageByConversationIdAndLimit.stream().sorted(Comparator.comparing(Message::getId)).findFirst();
        if (messageFirst.isPresent()) {
            conversationDetailsResponse.setNextCursor(messageFirst.get().getCreatedTime().toEpochMilli() + "_" + messageFirst.get().getId());
        } else {
            conversationDetailsResponse.setNextCursor("");
        }
        conversationDetailsResponse.setMessage(chatMessagesSort);
        return conversationDetailsResponse;
    }


}

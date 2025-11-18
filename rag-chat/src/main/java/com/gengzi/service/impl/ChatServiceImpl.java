package com.gengzi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.gengzi.dao.repository.ConversationRepository;
import com.gengzi.enums.Agent;
import com.gengzi.rag.search.service.ChatRagService;
import com.gengzi.request.ChatReq;
import com.gengzi.response.BusinessException;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private ConversationRepository conversationRepository;


    @Autowired
    private ChatRagService chatRagService;


    public ChatServiceImpl() {

    }

    @Override
    public Flux<ServerSentEvent<ChatMessageResponse>> chatRag(ChatReq req) {


        Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
        // 1,判断会话id是否存在,参数校验
        if (!conversationRepository.findById(req.getConversationId()).isPresent()) {
            sink.tryEmitError(new BusinessException("会话不存在"));
            sink.tryEmitComplete();
        }
        // 2，判断agentid是否存在，并且agentid是否可用
        if (StrUtil.isNotBlank(req.getAgentId()) && Agent.isExist(req.getAgentId())) {
            // 3,存在agentid，执行agent流程
        }


        // 4, 其他情况，走rag增强检索流程（闲聊走大模型回复）
//        Flux<ChatMessageResponse> chatMessageResponseFlux = UserDetailsUtils.getUserDetails()
//                .doOnNext(principal -> logger.info("当前用户id：{}", principal.getId()))
//                .map(UserPrincipal::getId)
//                .flatMapMany(userId -> chatRagService.chatRag(req, userId));

        Flux<ChatMessageResponse> chatMessageResponseFlux = chatRagService.chatRag(req, "213214");

        chatMessageResponseFlux.index()
                .doOnNext(tuple -> {
                    logger.info("当前流序号：{}", tuple.getT1());
                    sink.tryEmitNext(ServerSentEvent.builder(tuple.getT2()).build());
                })
                .doOnComplete(()-> sink.tryEmitComplete())
                .doOnError(e -> sink.tryEmitError(e))
                .subscribe();


        return sink.asFlux()
                .doOnCancel(() -> logger.info("当前流被取消了"))
                .doOnError(e -> logger.error("当前流发生错误", e));
    }

    @Override
    public Object chatRagMsgList(String conversationId) {
        return null;
    }

}

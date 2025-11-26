package com.gengzi.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.response.LlmTextRes;
import com.gengzi.service.PPTGenerateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


@Service
public class PPTGenerateServiceImpl implements PPTGenerateService {
    private static final Logger logger = LoggerFactory.getLogger(PPTGenerateServiceImpl.class);

    @Autowired
    private ReactAgent PPTReactAgent;

    /**
     * @param req
     * @return
     */
    @Override
    public Flux<ServerSentEvent<ChatMessageResponse>> pptGenerate(ChatReq req) throws GraphRunnerException {
        String threadId = req.getThreadId();
        if (StrUtil.isBlank(req.getThreadId())) {
            threadId = String.format("%s_%s", req.getConversationId(), IdUtil.simpleUUID());
        }
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata("conversationId", req.getConversationId())
                .build();
        Flux<NodeOutput> stream = PPTReactAgent.stream(req.getQuery(), runnableConfig);
        String finalThreadId = threadId;
        Flux<ServerSentEvent<ChatMessageResponse>> map = stream.map(
                output -> {
                    logger.info("pptGenerate output：{}", output);
                    if (output instanceof StreamingOutput streamingOutput) {
                        String chunk = streamingOutput.chunk();
                        LlmTextRes llmTextRes = new LlmTextRes();
                        llmTextRes.setAnswer(chunk);
                        ChatMessageResponse chatMessageResponse = ChatMessageResponse.ofLlm(llmTextRes);
                        chatMessageResponse.setThreadId(finalThreadId);
                        ServerSentEvent<ChatMessageResponse> build = ServerSentEvent.builder(chatMessageResponse).build();
                        return build;
                    }
                    ChatMessageResponse chatMessageResponse = new ChatMessageResponse();
                    chatMessageResponse.setThreadId(finalThreadId);
                    ServerSentEvent<ChatMessageResponse> build = ServerSentEvent.builder(chatMessageResponse).build();
                    return build;
                }
        );
        return map;
    }
}

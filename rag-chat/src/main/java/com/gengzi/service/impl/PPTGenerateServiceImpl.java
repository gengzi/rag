package com.gengzi.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.gengzi.rag.agent.myagent.agent.MyReActAgent;
import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.response.LlmTextRes;
import com.gengzi.service.PPTGenerateService;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;


@Service
public class PPTGenerateServiceImpl implements PPTGenerateService {
    private static final Logger logger = LoggerFactory.getLogger(PPTGenerateServiceImpl.class);

    @Autowired
    private ReactAgent PPTReactAgent;


    @Autowired
    private ObjectFactory<MyReActAgent> reActAgentObjectFactory;

    @Autowired
    @Qualifier("jdbcChatMemory")
    private ChatMemory chatMemory;
    @Autowired
    private RedissonClient redissonClient;

    /**
     * @param req
     * @return
     */
    @Override
    public Flux<ServerSentEvent<ChatMessageResponse>> pptGenerate(ChatReq req) throws GraphRunnerException {
        return pptAgent(req);
//        MyReActAgent object = reActAgentObjectFactory.getObject();
//        Flux<String> run = object.run(req.getQuery());
//        return run.map(
//                output -> {
//                    logger.info("pptGenerate output：{}", output);
//                    LlmTextRes llmTextRes = new LlmTextRes();
//                    llmTextRes.setAnswer(output);
//                    ChatMessageResponse chatMessageResponse = ChatMessageResponse.ofLlm(llmTextRes);
//                    chatMessageResponse.setThreadId(req.getThreadId());
//                    ServerSentEvent<ChatMessageResponse> build = ServerSentEvent.builder(chatMessageResponse).build();
//                    return build;
//                }
//        );
    }

    @NotNull
    private Flux<ServerSentEvent<ChatMessageResponse>> pptAgent(ChatReq req) throws GraphRunnerException {
        String threadId = req.getThreadId();
//        if (StrUtil.isBlank(req.getThreadId())) {
        threadId = String.format("%s_%s", req.getConversationId(), IdUtil.simpleUUID());
//        }

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata("conversationId", req.getConversationId())
                .build();
        // TODO 从聊天记忆中获取上下文信息
        List<Message> messages = chatMemory.get(req.getConversationId());
        messages.add(new UserMessage(req.getQuery()));

        Flux<NodeOutput> stream = PPTReactAgent.stream(messages, runnableConfig);
        String finalThreadId = threadId;
        Flux<ServerSentEvent<ChatMessageResponse>> map = stream.map(
                output -> {

                    // 检查是否为 StreamingOutput 类型
                    if (output instanceof StreamingOutput streamingOutput) {
                        OutputType type = streamingOutput.getOutputType();

                        // 处理模型推理的流式输出
                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            // 流式增量内容，逐步显示
                            logger.info(streamingOutput.message().getText());
                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                            // 模型推理完成，可获取完整响应
                            logger.info("\n模型输出完成");
                        }

                        // 处理工具调用完成（目前不支持 STREAMING）
                        if (type == OutputType.AGENT_TOOL_FINISHED) {
                            logger.info("工具调用完成: " + output.node());
                        }

                        // 对于 Hook 节点，通常只关注完成事件（如果Hook没有有效输出可以忽略）
                        if (type == OutputType.AGENT_HOOK_FINISHED) {
                            logger.info("Hook 执行完成: " + output.node());
                        }
                    }


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

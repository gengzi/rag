package com.gengzi.rag.agent.reactagent.iterceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

// 模型调用性能监控
public class ModelPerformanceInterceptor extends ModelInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(ModelPerformanceInterceptor.class);

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // 请求前记录
        int messageCount = request.getMessages().size();
        logger.info("发送请求到模型: {} 条消息", messageCount);

        List<Message> messages = request.getMessages();
        if (messages.size() > 0) {
            Message message = messages.get(0);
            logger.debug("请求消息: {}", message.getText());
        }

        long startTime = System.currentTimeMillis();

        // 执行实际调用
        ModelResponse response = handler.call(request);

        // 响应后记录
        long completionTime = System.currentTimeMillis() - startTime;

        // 提取 token 使用信息 (需要根据实际的 ModelResponse API 调整)
        // 注意: Spring AI Alibaba 的 ModelResponse 可能有不同的 API
        // 这里我们先记录基本的性能指标

        logger.info("模型调用完成 | 耗时: {}ms", completionTime);

        // TODO: 根据 com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse 的实际 API
        // 添加 token 统计功能。可能需要检查 response 对象的实际方法和字段。

        return response;
    }

    @Override
    public String getName() {
        return "ModelPerformanceInterceptor";
    }
}
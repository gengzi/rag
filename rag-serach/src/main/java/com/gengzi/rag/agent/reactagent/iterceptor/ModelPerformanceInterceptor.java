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
      System.out.println("发送请求到模型: " + request.getMessages().size() + " 条消息");
      logger.info("发送请求到模型: " + request.getMessages().size() + " 条消息");

      List<Message> messages = request.getMessages();
      if (messages.size() > 0) {
          Message message = messages.get(0);
          logger.info("请求消息: " + message.getText());
      }

      long startTime = System.currentTimeMillis();

      // 执行实际调用
      ModelResponse response = handler.call(request);

      // 响应后记录
      long duration = System.currentTimeMillis() - startTime;
      System.out.println("模型响应耗时: " + duration + "ms");

      return response;
  }

  @Override
  public String getName() {
      return "ModelPerformanceInterceptor";
  }
}
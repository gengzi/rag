//package com.gengzi.agent;
//
//
//import com.alibaba.cloud.ai.graph.agent.ReactAgent;
//import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Bean;
//import org.springframework.stereotype.Component;
//
///**
// * 调用增强检索工具，获取关联的文档块内容信息
// */
//@Component
//public class RagAgent {
//
//    @Autowired
//    private ChatModel openAiChatModel;
//
//    @Bean
//    public ReactAgent ragAgentTest() {
//        ToolErrorInterceptor build = ToolErrorInterceptor.builder().build();
//
//
//        ReactAgent ragAgent = ReactAgent.builder()
//                .name("ragAgent")
//                .description("这是一个检索增强生成的agent,用于从现有知识库获取信息")
//                .systemPrompt("你是一名专业agent，你需要评估任务执行情况")
//                .model(openAiChatModel)
//                .interceptors()
//                .build();
//        return ragAgent;
//    }
//
//
//}

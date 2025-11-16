//package com.gengzi.agent;
//
//
//import com.alibaba.cloud.ai.graph.agent.ReactAgent;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.context.annotation.Bean;
//import org.springframework.stereotype.Component;
//
//@Component
//public class AiPPTReactAgent {
//
//
//
//    @Autowired
//    private ChatModel openAiChatModel;
//
//    /**
//     * 创建一个ppt react agent 用于调用大模型和本地工具生成一个ppt 文件
//     * @return
//     */
//    @Bean("pptReactAgent")
//    public ReactAgent PPTReactAgent(){
//
//
//        ReactAgent build = ReactAgent.builder()
//                .name("PPTReactAgent")
//                .description("这是一个生成ppt文件的react agent")
//                .systemPrompt("你作为一名专业agent，你需要评估任务执行情况")
//                .model(openAiChatModel)
//                .build();
//
//
//        return build;
//
//    }
//
//
//
//
//
//
//
//
//
//
//
//}

package com.gengzi.rag.agent.subagent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.gengzi.rag.agent.reactagent.iterceptor.ModelPerformanceInterceptor;
import com.gengzi.rag.agent.subagent.tool.FileSystemTools;
import com.gengzi.rag.config.MSimpleLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class SubAgent {

    @Autowired
    private ChatModel openAiChatModel;

    @Bean
    public ReactAgent subReActAgent() {
        ReactAgent build = ReactAgent.builder()
                .name("subReActAgent")
                .chatClient(ChatClient.builder(openAiChatModel).defaultAdvisors(new MSimpleLoggerAdvisor()).build())
                .methodTools(new FileSystemTools())
                .systemPrompt("你是一个专业的文件操作助手。\n" +
                        "            \n" +
                        "            【核心规则】\n" +
                        "            1. 收到任务后，分析需要调用的工具。\n" +
                        "            2. **Observation（观察）到工具返回结果后，必须立即停止调用工具！**\n" +
                        "            3. 将工具返回的内容总结或直接反馈给用户，作为最终答案（Final Answer）。\n" +
                        "            4. 严禁对同一个文件进行重复读取，除非用户明确要求重试。\n" +
                        "            5. 如果工具报错，请直接告知用户错误原因，不要无休止重试。")
                .interceptors(new ModelPerformanceInterceptor())
                .hooks(new com.gengzi.rag.agent.reactagent.hooks.ModelPerformanceHook())
                .build();
        return build;
    }

}

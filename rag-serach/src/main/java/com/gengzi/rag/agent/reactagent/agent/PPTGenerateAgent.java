package com.gengzi.rag.agent.reactagent.agent;


import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.RedisSaver;
import com.gengzi.rag.agent.deepresearch.util.ResourceUtil;
import com.gengzi.rag.agent.reactagent.config.ReactConfig;
import com.gengzi.rag.agent.reactagent.tool.PPTMotherboadrTool;
import com.gengzi.rag.agent.reactagent.tool.PPTMotherboadrToolV2;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class PPTGenerateAgent {

    @Autowired
    private ChatModel openAiChatModel;

    @Autowired
    private ReactConfig reactConfig;

    @Autowired
    private RedissonClient redissonClient;


    /**
     *  根据用户的问题，判断问题是否充分，反问用户
     *  得到完善的问题内容后，调用模板选择工具 （入参：空，出参：多个已存在的模板，图示）
     *  用户继续说 使用那个模板
     *  继续调用其他工具， rag检索工具，网页查询工具，生成ppt大纲内容 ，ppt大纲有固定格式生成（子agent 或者 tool）
     *  根据大纲，将每个子标题继续 rag检索，网页查询工具 返回每个子标题的内容
     *  根据生成的大纲，和子标题的内容和模板信息准备生成每页的信息  ppt生成工具
     *  生成完成后，输出ppt 链接，供用户下载使用
     *
     * @return
     */

    @Bean
    public ReactAgent PPTReactAgent() {

        ToolCallback pptMotherboadrTool = FunctionToolCallback
                .builder("get_PPTMotherboadrs", new PPTMotherboadrToolV2())
                .description("获取当前已配置PPT模板信息")
                .inputType(PPTMotherboadrToolV2.MotherboadrType.class)
                .build();
        // 短期记忆
//        RedisSaver redisSaver = new RedisSaver(redissonClient);
        MemorySaver memorySaver = new MemorySaver();


        String pptGenerate = reactConfig.getAgents().get("pptGenerate").getInstruction();
        ReactAgent build = ReactAgent.builder()
                .name("PPTGenerate")
                .model(openAiChatModel)
                .tools(pptMotherboadrTool)
                .saver(memorySaver)
                .systemPrompt(ResourceUtil.loadFileContent(pptGenerate))
                .instruction(ResourceUtil.loadFileContent(pptGenerate))
                .build();
        return build;
    }
}

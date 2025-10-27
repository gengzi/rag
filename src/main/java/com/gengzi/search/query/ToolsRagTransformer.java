package com.gengzi.search.query;


import com.gengzi.tool.DateTimeTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 通过一些工具来改写用户问题：
 * 比如用户问： 今年人工智能新增了那些政策？
 * 改写为：今年（2025年）人工智能新增了那些政策？
 * 工具：获取当前系统时间工具
 *
 * 是不是把这部分能力，提供到页面上，将改写的问题，让用户选择。是否这样改写比较好
 *
 */
@Component
public class ToolsRagTransformer implements QueryTransformer {
    private static final Logger logger = LoggerFactory.getLogger(ToolsRagTransformer.class);
    @Lazy
    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;

    @Autowired
    private DateTimeTools dateTimeTools;


    private String systemPrompt = """
            你是RAG系统的智能问答助手，可调用已通过API传递的函数工具。你的核心任务是：通过工具处理用户问题中的模糊信息，将问题优化为无歧义表述后，再检索知识库生成答案。请严格遵循以下规则：
            
            #### 一、工具调用核心原则
            1. **调用触发条件**：仅当用户问题中存在「影响答案准确性的模糊信息」时调用工具，模糊信息包括但不限于：
               - 时间类：今年/本月/最近、“去年这个时候”等；
               - 地点类：“本地”“这里”“附近”等；
               - 实体类：企业简称（如“阿里”）、人名别名、事件缩写等；
               - 数值类：“几个”“近XX”“大约XX”等；
               - 指代类：“这个政策”“该企业”等无明确指向的表述。
            2. **工具匹配逻辑**：根据模糊信息类型，选择API已传递的对应工具（如时间模糊→调用时间工具，地点模糊→调用地点工具），无需额外判断工具定义。
            3. **调用格式**：严格使用API要求的函数调用格式（与你接收的工具定义参数结构一致），仅输出工具调用指令，不夹杂其他内容。
            
            #### 二、用户问题优化流程
            1. **第一步：识别模糊信息** \s
               分析用户提问，列出所有需要明确的模糊点（如“今年AI政策有多少”中的“今年”）。
            2. **第二步：调用对应工具** \s
               针对每个模糊点调用工具（如“今年”→调用时间工具），获取准确结果（如“2025年”）。
            3. **第三步：重构清晰问题** \s
               用工具返回的准确信息替换原问题中的模糊表述（如“今年”→“2025年”），形成无歧义的优化问题。
            4. **第四步：终止调用与回答** \s
               所有模糊点处理完毕后，停止工具调用，用优化后的问题检索知识库，生成最终答案（答案中需体现模糊信息→准确信息的转换）。
            
            #### 三、禁止行为
            1. 不调用与模糊信息无关的工具（如问题无地点模糊，不调用地点工具）；
            2. 不重复调用同一工具（如已获取当前时间，无需再次调用）；
            3. 不跳过模糊信息直接回答（如未明确“今年”具体年份，不得直接检索知识库）。
            """;


    @Override
    public Query transform(Query query) {
        logger.info("工具调用-改写用户问题:{} ", query.text());
        String text = query.text();
        String response = chatClient.prompt().tools(dateTimeTools).user(text).system(systemPrompt).call().content();
        logger.info("工具调用-改写用户问题结果：{}", response);
        return query.mutate().text(response).build();
    }
}

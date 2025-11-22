package com.gengzi.rag.agent.deepresearch.node;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.gengzi.rag.agent.deepresearch.config.DeepResearchConfig;
import com.gengzi.rag.agent.deepresearch.config.NodeConfig;
import com.gengzi.rag.agent.deepresearch.dto.Plan;
import com.gengzi.rag.agent.deepresearch.util.ResourceUtil;
import com.gengzi.rag.agent.deepresearch.util.StateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 信息节点
 */
public class InformationNode extends AbstractLlmNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(InformationNode.class);

    private final DeepResearchConfig deepResearchConfig;

    private final OpenAiChatModel.Builder openAiChatModelBuilder;

    private final int maxStepNum;

    private final BeanOutputConverter<Plan> converter;

    public InformationNode(DeepResearchConfig deepResearchConfig, OpenAiChatModel.Builder openAiChatModelBuilder, int maxStepNum) {
        this.deepResearchConfig = deepResearchConfig;
        this.openAiChatModelBuilder = openAiChatModelBuilder;
        this.maxStepNum = maxStepNum;
        this.converter = new BeanOutputConverter<>(Plan.class);

    }

    /**
     * @param state
     * @return
     * @throws Exception
     */
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("coordinator node is running.");
        // 获取入参
        String query = StateUtil.getQuery(state);
        String plannerResult = StateUtil.getPlannerResult(state);
        // 业务逻辑
        // 解析响应结果，看是否能格式化为对象，如果能，判断的上下文是否充足
        Plan plan;
        try {
            plan = converter.convert(plannerResult);
            logger.info("plannerResult is a valid json:{}", plan);
            // 判断上下文现在是否充足
            HashMap<String, Object> result = new HashMap<>();
            boolean hasEnoughContext = plan.isHasEnoughContext();
            if (hasEnoughContext) {
              // 充足，下一步执行报告生成
                result.put(getNodeConfig().getNextNodeKey(), "ReporterNode");
                return result;
            }


        } catch (Exception e) {
            logger.error("plannerResult is not a valid json");
            // 解析异常，
            HashMap<String, Object> result = new HashMap<>();
            // 如果不能，跳转为plannernode，进行迭代，并且记录迭代次数，超过迭代次数，就提示用户并结束
            if (StateUtil.getPlanIterations(state) > maxStepNum) {
                String nextNode = StateGraph.END;
                result.put(getNodeConfig().getNextNodeKey(), nextNode);
                return result;
            } else {
                String nextNode = "plannerNode";
                result.put(getNodeConfig().getNextNodeKey(), nextNode);
                result.put("planIterations", StateUtil.getPlanIterations(state) + 1);
                return result;
            }
        }
        HashMap<String, Object> result = new HashMap<>();
        result.put(getNodeConfig().getNextNodeKey(), "ParalleExecutorNode");
        result.put("planIterations", StateUtil.getPlanIterations(state) + 1);
        result.put("currentPlan", plan);
        // 赋值出参
        return result;
    }

    /**
     * @return
     */
    @Override
    ChatClient bulidChatClient() {
        OpenAiChatModel openAiChatModel = openAiChatModelBuilder.defaultOptions(
                OpenAiChatOptions.builder()
                        .model(getNodeConfig().getModel())
                        .build()
        ).build();
        return ChatClient.builder(openAiChatModel).build();
    }

    private NodeConfig getNodeConfig() {
        Map<String, NodeConfig> deepresearchNodes = deepResearchConfig.getDeepresearchNodes();
        return deepresearchNodes.get(this.getClass().getSimpleName());
    }

    /**
     * @return
     */
    @Override
    PromptTemplate bulidPromptTemplate() {
        Map<String, NodeConfig> deepresearchNodes = deepResearchConfig.getDeepresearchNodes();
        if (deepresearchNodes.containsKey(this.getClass().getSimpleName())) {
            return PromptTemplate.builder()
                    .template(ResourceUtil.loadFileContent(getNodeConfig().getPrompts().get(0)
                                    .replace("{{ CURRENT_TIME }}", LocalDateTime.now().toString()))
                            .replace("{{ max_step_num }}", maxStepNum + ""))
                    .build();
        }
        return null;
    }

    /**
     * @return
     */
    @Override
    ToolCallback[] bulidToolCallback() {
        Map<String, NodeConfig> deepresearchNodes = deepResearchConfig.getDeepresearchNodes();
        if (deepresearchNodes.containsKey(this.getClass().getSimpleName())) {
            List<String> tools = getNodeConfig().getTools();
            ArrayList<Object> objects = new ArrayList<>();
            for (String tool : tools) {
                Object bean = SpringUtil.getBean(tool);
                objects.add(bean);
            }
            return ToolCallbacks.from(objects.toArray());
        }
        return ToolCallbacks.from();
    }
}


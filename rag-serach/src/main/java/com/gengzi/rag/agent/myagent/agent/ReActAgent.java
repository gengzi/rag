package com.gengzi.rag.agent.myagent.agent;

import com.gengzi.rag.agent.deepresearch.util.ResourceUtil;
import com.gengzi.rag.agent.myagent.config.MyAgentProperties;
import com.gengzi.rag.agent.myagent.memory.Memory;
import com.gengzi.rag.agent.myagent.tool.PlanTool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ReAct Agent - 基于ReAct（Reasoning and Acting）框架的智能代理
 * 实现思考-行动-观察的循环过程
 */
@Slf4j
@Component
@Scope("prototype")
public class ReActAgent extends BaseAgent {

    @Autowired
    private ChatModel openAiChatModel;

    @Autowired
    private ObjectFactory<PlanTool> planTool;

    @Autowired
    private MyAgentProperties myAgentConfig;


    private String currentThought;
    private String currentAction;
    private String currentObservation;
    private int maxIterations; // 最大迭代次数，防止无限循环
    private ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

    private Prompt prompt;
    private ChatResponse response;

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        // 验证配置
        MyAgentProperties.PlannerProperties planner = myAgentConfig.getPlanner();

        // 初始化配置参数
        this.maxIterations = planner.getMaxIterations();

        log.info("ReActAgent初始化完成，最大迭代次数: {}, 工具调用: {}",
                maxIterations, planner.isEnableToolCalling());
    }

    /**
     * ReAct执行步骤：Think -> Act -> Observe
     */
    @Override
    public String step(String query) {
        try {
            // 检查是否达到最大迭代次数
            if (getCurrentStep() >= maxIterations) {
                return "达到最大迭代次数 " + maxIterations + "，停止执行";
            }

            // 添加用户查询到记忆
            if (memory.isEmpty()) {
                memory.addUserMessage(query);
                // 添加系统提示
//                String systemPrompt = myAgentConfig.getPlanner().getSysPrompt();
//                if (!systemPrompt.isEmpty()) {
//                    memory.addSystemMessage(ResourceUtil.loadFileContent(systemPrompt));
//                }
            }

            // Step 1: Think - 思考当前情况
            if (!think()) {
                return "思考完成，不需要进一步行动";
            }

            // Step 2: Act - 执行具体行动
            String actionResult = act();
            if (actionResult == null || actionResult.isEmpty()) {
                return "行动执行完成";
            }
//
//            // Step 3: Observe - 观察行动结果
//            observe(actionResult);

            return String.format("思考: %s\n行动: %s\n观察: %s",
                    currentThought, currentAction, currentObservation);

        } catch (Exception e) {
            log.error("ReAct步骤执行失败", e);
            setState(AgentState.FAILED);
            return "执行失败: " + e.getMessage();
        }
    }

    /**
     * 思考阶段 - 分析当前情况并制定计划
     *
     * @return true表示需要继续执行行动，false表示可以停止
     */
    public boolean think() {
        try {
            String context = buildContext();
            String thinkSystemPrompt = myAgentConfig.getPlanner().getSysPrompt();


            ChatClient chatClient = ChatClient.builder(openAiChatModel)
                    .defaultToolCallbacks(getToolCallbacks())
                    .defaultOptions(
                            OpenAiChatOptions.builder().internalToolExecutionEnabled(false).build()
                    )
                    .build();

            ChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .internalToolExecutionEnabled(false)
                    .toolCallbacks(getToolCallbacks())
                    .build();
            ArrayList<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(ResourceUtil.loadFileContent(thinkSystemPrompt)));
            messages.addAll(memory.getMessages());

            prompt = new Prompt(messages, chatOptions);

            response = openAiChatModel.call(prompt);

//            ChatResponse response = chatClient.prompt()
//                    .messages(memory.getMessages())
//                    .system(ResourceUtil.loadFileContent(thinkSystemPrompt))
//                    .call()
//                    .chatResponse();


            AssistantMessage output = response.getResult().getOutput();

            if (response.hasToolCalls()) {
                // 记录思考过程
                memory.addMessage(output);
            } else {
                memory.addMessage(output);
                // 使用配置中的完成检测关键词
                if (myAgentConfig.getPlanner().containsCompletionKeyword(output.getText())) {
                    return false;
                }
            }
            return true;

        } catch (Exception e) {
            log.error("思考阶段执行失败", e);
            currentThought = "思考失败: " + e.getMessage();
            return false;
        }
    }

    /**
     * 行动阶段 - 执行具体的工具调用或操作
     *
     * @return 行动执行结果
     */
    public String act() {
        try {

            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
            Message message = toolExecutionResult.conversationHistory().get(toolExecutionResult.conversationHistory().size() - 1);
            // 记录行动结果
            memory.addMessage(message);
            Optional<Map.Entry<String, PlanTool.Plan>> first = planTool.getObject().planStore.entrySet().stream().findFirst();
            if (first.isPresent()) {
                Map.Entry<String, PlanTool.Plan> planEntry = first.get();
                PlanTool.Plan value = planEntry.getValue();
                for (String step : value.getSteps()) {
                    // 获取还在进行中的步骤
                    return step;
                }
            }
            return message.getText();
        } catch (Exception e) {
            log.error("行动阶段执行失败", e);
            currentAction = "行动失败: " + e.getMessage();
            memory.addAssistantMessage("行动: " + currentAction);
            return "行动执行失败: " + e.getMessage();
        }
    }

    /**
     * 观察阶段 - 处理和总结行动结果
     *
     * @param actionResult 行动执行结果
     */
    public void observe(String actionResult) {
        try {
            // 构建观察提示
            String observeSystemPrompt = myAgentConfig.getObserve().getSystemPrompt();
            String observePrompt = String.format(
                    observeSystemPrompt,
                    actionResult
            );

            ChatClient chatClient = ChatClient.builder(openAiChatModel)
                    .build();
            String response = chatClient.prompt()
                    .messages(memory.getMessages())
                    .user(observePrompt)
                    .call()
                    .content();

            currentObservation = response;

            // 记录观察结果
            memory.addAssistantMessage("观察: " + response);

            // 使用配置中的完成检测关键词
            if (myAgentConfig.getPlanner().containsCompletionKeyword(response)) {
                setState(AgentState.COMPLETED);
            }

        } catch (Exception e) {
            log.error("观察阶段执行失败", e);
            currentObservation = "观察失败: " + e.getMessage();
            memory.addAssistantMessage("观察: " + currentObservation);
        }
    }


    /**
     * 构建当前上下文信息
     */
    private String buildContext() {
        StringBuilder context = new StringBuilder();
        context.append("当前任务: ").append(getCurrentQuery()).append("\n");
        context.append("当前步骤: ").append(getCurrentStep() + 1).append("\n");
        context.append("Agent状态: ").append(getState().getDescription()).append("\n");

        if (currentThought != null) {
            context.append("上一步思考: ").append(currentThought).append("\n");
        }
        if (currentAction != null) {
            context.append("上一步行动: ").append(currentAction).append("\n");
        }
        if (currentObservation != null) {
            context.append("上一步观察: ").append(currentObservation).append("\n");
        }

        context.append("\n对话历史:\n");
        for (int i = Math.max(0, memory.size() - 5); i < memory.size(); i++) {
            Message msg = memory.get(i);
            context.append(getMessageTypeString(msg)).append(": ").append(msg.getText()).append("\n");
        }

        return context.toString();
    }

    /**
     * 获取工具回调列表
     */
    private List<ToolCallback> getToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        // 添加计划工具的回调
        callbacks.addAll(Arrays.stream(ToolCallbacks.from(planTool.getObject())).toList());

        return callbacks;
    }

    /**
     * 获取消息类型字符串表示
     */
    private String getMessageTypeString(Message message) {
        if (message.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER) {
            return "用户";
        } else if (message.getMessageType() == org.springframework.ai.chat.messages.MessageType.ASSISTANT) {
            return "助手";
        } else if (message.getMessageType() == org.springframework.ai.chat.messages.MessageType.SYSTEM) {
            return "系统";
        } else if (message.getMessageType() == org.springframework.ai.chat.messages.MessageType.TOOL) {
            return "工具";
        } else {
            return message.getMessageType().toString();
        }
    }

    /**
     * 重写shouldStop方法，增加提前停止逻辑
     */
    @Override
    protected boolean shouldStop() {
        // 检查观察结果中是否包含完成标志
        if (currentObservation != null && myAgentConfig.getPlanner().containsCompletionKeyword(currentObservation)) {
            return true;
        }

        // 检查思考结果中是否包含完成标志
        if (currentThought != null && myAgentConfig.getPlanner().containsCompletionKeyword(currentThought)) {
            return true;
        }

        return super.shouldStop();
    }

    /**
     * 重置ReAct状态
     */
    @Override
    public void reset() {
        super.reset();
        memory.clear();
        currentThought = null;
        currentAction = null;
        currentObservation = null;
    }

    /**
     * 获取ReAct执行摘要
     */
    public String getReActSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== ReAct Agent 执行摘要 ===\n");
        summary.append("任务: ").append(getCurrentQuery()).append("\n");
        summary.append("状态: ").append(getState().getDescription()).append("\n");
        summary.append("执行步骤数: ").append(getCurrentStep()).append("\n");

        if (currentThought != null) {
            summary.append("\n当前思考: ").append(currentThought).append("\n");
        }
        if (currentAction != null) {
            summary.append("当前行动: ").append(currentAction).append("\n");
        }
        if (currentObservation != null) {
            summary.append("当前观察: ").append(currentObservation).append("\n");
        }

        summary.append("\n=== 完整执行历史 ===\n");
        for (int i = 0; i < memory.size(); i++) {
            Message msg = memory.get(i);
            if (msg.getText() != null && !msg.getText().trim().isEmpty()) {
                summary.append("[").append(getMessageTypeString(msg)).append("] ").append(msg.getText()).append("\n");
            }
        }

        return summary.toString();
    }

    // Getter和Setter方法
    public Memory getMemory() {
        return memory;
    }

    public String getCurrentThought() {
        return currentThought;
    }

    public String getCurrentAction() {
        return currentAction;
    }

    public String getCurrentObservation() {
        return currentObservation;
    }

    public int getMaxIterations() {
        return myAgentConfig.getPlanner().getMaxIterations();
    }
}
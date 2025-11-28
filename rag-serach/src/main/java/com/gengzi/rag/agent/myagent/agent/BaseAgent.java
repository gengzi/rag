package com.gengzi.rag.agent.myagent.agent;

import com.gengzi.rag.agent.myagent.memory.Memory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

/**
 * 基础Agent抽象类
 * 提供Agent执行框架，包括状态管理、步骤限制和结果收集
 */
public abstract class BaseAgent {

    private static final int MAX_STEPS = 200;
    Memory memory;
    private List<String> result = new ArrayList<>();
    private AgentState state = AgentState.CREATED;
    private int currentStep = 0;
    private String currentQuery;

    /**
     * 执行单个步骤
     *
     * @param query 当前处理的查询
     * @return 步骤执行结果，返回null表示执行完成
     */
    public abstract String step(String query);

    /**
     * 运行Agent，执行最多200个步骤
     *
     * @param query 要处理的查询
     * @return 执行结果汇总
     */
    public Flux<String> run(String query) {
        memory = new Memory();
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();


        if (query == null || query.trim().isEmpty()) {
            setState(AgentState.FAILED);
            sink.tryEmitError(new RuntimeException("错误：查询参数不能为空"));
        }

        this.currentQuery = query;
        setState(AgentState.RUNNING);

        try {
            while (currentStep < MAX_STEPS) {
                String stepResult = step(query);

                if (stepResult == null) {
                    // step返回null表示正常完成
                    setState(AgentState.COMPLETED);
                    break;
                }

                result.add("步骤 " + (currentStep + 1) + ": " + stepResult);
                sink.tryEmitNext("步骤 " + (currentStep + 1) + ": " + stepResult);
                currentStep++;

                // 检查是否需要在某个步骤后停止（可被子类重写）
                if (shouldStop()) {
                    setState(AgentState.COMPLETED);
                    break;
                }
            }

            // 达到最大步骤限制
            if (currentStep >= MAX_STEPS) {
                setState(AgentState.COMPLETED);
                result.add("已达到最大步骤限制(" + MAX_STEPS + "步)，自动完成执行");
            }

        } catch (Exception e) {
            setState(AgentState.FAILED);
            result.add("执行失败: " + e.getMessage());
            throw e;
        }

        return sink.asFlux();
    }

    /**
     * 判断是否需要提前停止执行（可被子类重写）
     *
     * @return true表示需要停止
     */
    protected boolean shouldStop() {
        return false; // 默认不提前停止
    }

    /**
     * 获取执行结果汇总
     *
     * @return 结果字符串
     */
    public String getResultSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Agent执行结果:\n");
        summary.append("状态: ").append(getState().getDescription()).append("\n");
        summary.append("总步骤数: ").append(currentStep).append("\n");
        summary.append("查询: ").append(currentQuery != null ? currentQuery : "无").append("\n");
        summary.append("执行详情:\n");

        for (String stepResult : result) {
            summary.append("- ").append(stepResult).append("\n");
        }

        return summary.toString();
    }

    // Getter和Setter方法
    public List<String> getResult() {
        return result;
    }

    public AgentState getState() {
        return state;
    }

    public void setState(AgentState state) {
        this.state = state;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public String getCurrentQuery() {
        return currentQuery;
    }

    /**
     * 重置Agent状态
     */
    public void reset() {
        result.clear();
        state = AgentState.CREATED;
        currentStep = 0;
        currentQuery = null;
    }

    /**
     * 取消执行
     */
    public void cancel() {
        setState(AgentState.CANCELLED);
    }

    /**
     * Agent状态枚举
     */
    public enum AgentState {
        CREATED("已创建"),
        RUNNING("进行中"),
        COMPLETED("完成"),
        FAILED("失败"),
        CANCELLED("已取消");

        private final String description;

        AgentState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
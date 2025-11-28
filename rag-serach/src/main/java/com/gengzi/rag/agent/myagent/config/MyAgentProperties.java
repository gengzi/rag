package com.gengzi.rag.agent.myagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MyAgent配置属性类
 * 用于绑定application.yml中myagent部分的配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "myagent")
public class MyAgentProperties {

    /**
     * 规划器配置
     */
    private PlannerProperties planner = new PlannerProperties();

    private ReactProperties react = new ReactProperties();


    private ThinkPhaseProperties taskExecutor = new ThinkPhaseProperties();

    private ActPhaseProperties act = new ActPhaseProperties();

    private ObservePhaseProperties observe = new ObservePhaseProperties();


    @Data
    public static class ReactProperties {

        /**
         * 系统提示词文件路径
         */
        private String sysPrompt;

        /**
         * 下一步提示词文件路径
         */
        private String nextStepPrompt;

        /**
         * 最大迭代次数
         */
        private int maxIterations = 20;

        /**
         * 最大消息历史数量
         */
        private int maxMessageHistory = 500;

        /**
         * 是否启用工具调用
         */
        private boolean enableToolCalling = true;

        /**
         * 任务完成检测关键词
         */
        private String[] completionKeywords = {
                "TASK_COMPLETED",
                "任务完成",
                "已完成",
                "任务结束"
        };

    }

    /**
     * 规划器配置
     */
    @Data
    public static class PlannerProperties {

        /**
         * 系统提示词文件路径
         */
        private String sysPrompt = "prompts/myagent/planner.md";

        /**
         * 下一步提示词文件路径
         */
        private String nextStepPrompt = "prompts/myagent/planner_next_step.md";

        /**
         * 最大迭代次数
         */
        private int maxIterations = 10;

        /**
         * 最大消息历史数量
         */
        private int maxMessageHistory = 50;

        /**
         * 是否启用工具调用
         */
        private boolean enableToolCalling = true;

        /**
         * 任务完成检测关键词
         */
        private String[] completionKeywords = {
                "TASK_COMPLETED",
                "任务完成",
                "已完成",
                "任务结束"
        };

        /**
         * 思考阶段配置
         */
        private ThinkPhaseProperties think = new ThinkPhaseProperties();

        /**
         * 行动阶段配置
         */
        private ActPhaseProperties act = new ActPhaseProperties();

        /**
         * 观察阶段配置
         */
        private ObservePhaseProperties observe = new ObservePhaseProperties();

        public boolean containsCompletionKeyword(String response) {
            for (String keyword : completionKeywords) {
                if (response.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 思考阶段配置
     */
    @Data
    public static class ThinkPhaseProperties {

        /**
         * 思考阶段系统提示词
         */
        private String systemPrompt = "基于当前上下文信息，请进行深入思考并制定下一步行动计划。";

        /**
         * 思考阶段温度参数
         */
        private double temperature = 0.7;

        /**
         * 最大思考长度
         */
        private int maxLength = 1000;
    }

    /**
     * 行动阶段配置
     */
    @Data
    public static class ActPhaseProperties {

        /**
         * 行动阶段系统提示词
         */
        private String systemPrompt = "基于思考结果，请执行具体的行动或调用合适的工具。";

        /**
         * 行动阶段温度参数
         */
        private double temperature = 0.3;

        /**
         * 工具调用超时时间（秒）
         */
        private int toolCallTimeout = 30;

        /**
         * 最大工具调用次数
         */
        private int maxToolCalls = 5;
    }

    /**
     * 观察阶段配置
     */
    @Data
    public static class ObservePhaseProperties {

        /**
         * 观察阶段系统提示词
         */
        private String systemPrompt = "请分析行动结果，评估任务完成情况，并决定下一步行动。";

        /**
         * 观察阶段温度参数
         */
        private double temperature = 0.5;

        /**
         * 最大观察长度
         */
        private int maxLength = 800;
    }
}
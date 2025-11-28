package com.gengzi.rag.agent.myagent.tool;


import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PlanTool {

    public Map<String, Plan> planStore = new HashMap<>();

    @Tool(description = "创建一个新的计划，包含计划标题、描述和具体步骤")
    public String createPlan(
            @ToolParam(description = "计划的标题", required = true) String title,
            @ToolParam(description = "计划的详细描述", required = true) String description,
            @ToolParam(description = "计划的具体步骤列表，用分号分隔", required = true) String steps) {

        String planId = "plan_" + System.currentTimeMillis();
        Plan plan = new Plan(planId, title, description, steps.split(";"), "CREATED");
        planStore.put(planId, plan);

        return String.format("计划创建成功！\n计划ID: %s\n标题: %s\n描述: %s\n步骤: %s\n状态: %s",
                planId, title, description, steps, "CREATED");
    }

    @Tool(description = "更新现有计划的内容，可以修改标题、描述和步骤")
    public String updatePlan(
            @ToolParam(description = "要更新的计划ID", required = true) String planId,
            @ToolParam(description = "新的计划标题（可选）") String newTitle,
            @ToolParam(description = "新的计划描述（可选）") String newDescription,
            @ToolParam(description = "新的计划步骤，用分号分隔（可选）") String newSteps) {

        Plan plan = planStore.get(planId);
        if (plan == null) {
            return "错误：找不到ID为 " + planId + " 的计划";
        }

        if (newTitle != null && !newTitle.trim().isEmpty()) {
            plan.setTitle(newTitle);
        }
        if (newDescription != null && !newDescription.trim().isEmpty()) {
            plan.setDescription(newDescription);
        }
        if (newSteps != null && !newSteps.trim().isEmpty()) {
            plan.setSteps(newSteps.split(";"));
        }
        plan.setStatus("UPDATED");

        return String.format("计划更新成功！\n计划ID: %s\n标题: %s\n描述: %s\n步骤: %s\n状态: %s",
                planId, plan.getTitle(), plan.getDescription(),
                String.join(";", plan.getSteps()), plan.getStatus());
    }

    @Tool(description = "将计划标记为已完成")
    public String completePlan(
            @ToolParam(description = "要完成的计划ID", required = true) String planId,
            @ToolParam(description = "完成结果总结", required = true) String resultSummary) {

        Plan plan = planStore.get(planId);
        if (plan == null) {
            return "错误：找不到ID为 " + planId + " 的计划";
        }

        plan.setStatus("COMPLETED");
        plan.setResultSummary(resultSummary);

        return String.format("计划完成！\n计划ID: %s\n标题: %s\n状态: %s\n完成总结: %s",
                planId, plan.getTitle(), plan.getStatus(), resultSummary);
    }

    @Tool(description = "更新计划的状态（如进行中、暂停、取消等）")
    public String updatePlanStatus(
            @ToolParam(description = "要更新状态的计划ID", required = true) String planId,
            @ToolParam(description = "新的计划状态", required = true) String newStatus) {

        Plan plan = planStore.get(planId);
        if (plan == null) {
            return "错误：找不到ID为 " + planId + " 的计划";
        }

        String oldStatus = plan.getStatus();
        plan.setStatus(newStatus);

        return String.format("计划状态更新成功！\n计划ID: %s\n标题: %s\n原状态: %s\n新状态: %s",
                planId, plan.getTitle(), oldStatus, newStatus);
    }

    @Tool(description = "查询指定计划的详细信息")
    public String getPlan(
            @ToolParam(description = "要查询的计划ID", required = true) String planId) {

        Plan plan = planStore.get(planId);
        if (plan == null) {
            return "错误：找不到ID为 " + planId + " 的计划";
        }

        StringBuilder result = new StringBuilder();
        result.append("计划详细信息：\n");
        result.append("计划ID: ").append(planId).append("\n");
        result.append("标题: ").append(plan.getTitle()).append("\n");
        result.append("描述: ").append(plan.getDescription()).append("\n");
        result.append("状态: ").append(plan.getStatus()).append("\n");
        result.append("步骤:\n");

        String[] steps = plan.getSteps();
        for (int i = 0; i < steps.length; i++) {
            result.append((i + 1)).append(". ").append(steps[i].trim()).append("\n");
        }

        if (plan.getResultSummary() != null) {
            result.append("完成总结: ").append(plan.getResultSummary()).append("\n");
        }

        return result.toString();
    }

    // 内部Plan类用于存储计划信息
    public static class Plan {
        private String id;
        private String title;
        private String description;
        private String[] steps;
        private String status;
        private String resultSummary;

        public Plan(String id, String title, String description, String[] steps, String status) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.steps = steps;
            this.status = status;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String[] getSteps() { return steps; }
        public void setSteps(String[] steps) { this.steps = steps; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getResultSummary() { return resultSummary; }
        public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    }
}

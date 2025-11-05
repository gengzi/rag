import graph.CompiledGraph;
import graph.StateGraph;

import java.util.Map;

public class Test {


    public static void main(String[] args) {
//
//        StateGraph stateGraph = new StateGraph();
//        stateGraph.addNode("node1");
//        stateGraph.addNode("node2");
//        stateGraph.addNode("node3");
//        stateGraph.addNode("node4");
//        stateGraph.addEdge("node1", "node2");
//        stateGraph.addEdge("node1", "node4");
//        stateGraph.addEdge("node2", "node3");
//        stateGraph.addEdge("node4", "node3");
//
//
//        CompiledGraph compiledGraph = new CompiledGraph(stateGraph);
//
//        Map<String, String> edges = compiledGraph.edges;
//
//        for (Map.Entry<String, String> entry : edges.entrySet()) {
//            System.out.print(entry.getKey() + " -> " + entry.getValue());
//        }


        System.out.println("\n" + "==".repeat(50));



        String xx = "你需要根据提供的页面类型、大纲信息和占位符列表，为每个占位符生成准确、贴合场景的内容。请严格遵循以下规则：\n" +
                "\n" +
                "1. 核心任务：  \n" +
                "   为每个占位符生成符合其作用的文本，内容需紧密关联提供的大纲信息，同时适配页面场景（如首页需正式、简洁；章节页需突出章节核心）。\n" +
                "\n" +
                "2. 输入信息说明：  \n" +
                "   - pageType：当前页面类型（如“首页”“章节页”），决定内容风格（首页偏概括性，章节页偏聚焦性）；  \n" +
                "   - outlineInfo：从PPT大纲中提取的关联信息（如首页对应“totalTitle”，章节页对应“chapterTitle”），是内容生成的核心依据；  \n" +
                "   - placeholders：需填充的占位符列表，每个包含“name”（占位符名称）和“description”（作用描述），需严格按名称匹配生成内容。\n" +
                "\n" +
                "3. 生成规则：  \n" +
                "   - 内容需基于outlineInfo扩展，不偏离大纲主题（如“总标题”占位符需以outlineInfo中的totalTitle为核心，可适当优化表述，使其更适合页面展示）；  \n" +
                "   - 若outlineInfo中无对应信息（如“演讲人”未在大纲中提及），需根据页面场景合理补充（如生成“[演讲人姓名/身份]”或结合主题推测，如“黄金市场分析师 张明”）；  \n" +
                "   - 风格需匹配PPT场景（如学术类PPT偏严谨，商业汇报偏简洁有力）；  \n" +
                "   - 避免冗余，长度适配占位符（如标题类不超过20字，演讲人不超过10字）。\n" +
                "\n" +
                "4. 输出格式：  \n" +
                "   仅返回JSON，结构如下（key为占位符id，value为生成的内容）：  \n" +
                "   {\n" +
                "     \"占位符id1\": \"生成的内容1\",\n" +
                "     \"占位符id2\": \"生成的内容2\"\n" +
                "   }\n" +
                "\n" +
                "示例输入：  \n" +
                "{\n" +
                "  \"pageType\": \"首页\",\n" +
                "  \"outlineInfo\": {\"totalTitle\": \"黄金价格波动分析与趋势预测\"},\n" +
                "  \"placeholders\": [\n" +
                "    {\"id\": \"title\", \"name\": \"总标题\", \"description\": \"首页顶部主标题\"},\n" +
                "    {\"id\": \"speaker\", \"name\": \"演讲人\", \"description\": \"首页右下角演讲人信息\"}\n" +
                "  ]\n" +
                "}\n" +
                "\n" +
                "示例输出：  \n" +
                "{\n" +
                "  \"title\": \"黄金价格波动分析与2025年趋势预测\",\n" +
                "  \"speaker\": \"贵金属市场研究员 李华\"\n" +
                "}";

    }

}

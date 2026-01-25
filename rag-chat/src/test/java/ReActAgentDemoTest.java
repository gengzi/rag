import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

/**
 * ReAct Agent 演示测试类
 *
 * ReAct (Reasoning and Acting) 是一种Agent模式，通过"思考-行动-观察"的循环来解决问题：
 * 1. Thought (思考): 分析当前情况，决定下一步做什么
 * 2. Action (行动): 执行具体的工具调用
 * 3. Observation (观察): 观察行动结果，并决定是否继续
 *
 * 这个测试类从零实现了一个简单的ReAct Agent框架
 */
public class ReActAgentDemoTest {

    // ==================== 工具定义 ====================

    /**
     * 计算器工具 - 可以执行基本的数学运算
     */
    static class CalculatorTool {

        @Tool(name = "add", description = "将两个数字相加。参数：a（第一个数字），b（第二个数字）")
        public double add(double a, double b) {
            return a + b;
        }

        @Tool(name = "subtract", description = "从第一个数字减去第二个数字。参数：a（被减数），b（减数）")
        public double subtract(double a, double b) {
            return a - b;
        }

        @Tool(name = "multiply", description = "将两个数字相乘。参数：a（第一个数字），b（第二个数字）")
        public double multiply(double a, double b) {
            return a * b;
        }

        @Tool(name = "divide", description = "将第一个数字除以第二个数字。参数：a（被除数），b（除数）")
        public double divide(double a, double b) {
            if (b == 0) {
                throw new IllegalArgumentException("除数不能为零");
            }
            return a / b;
        }

        @Tool(name = "power", description = "计算a的b次方。参数：a（底数），b（指数）")
        public double power(double a, double b) {
            return Math.pow(a, b);
        }

        @Tool(name = "sqrt", description = "计算一个数的平方根。参数：x（数字）")
        public double sqrt(double x) {
            if (x < 0) {
                throw new IllegalArgumentException("不能计算负数的平方根");
            }
            return Math.sqrt(x);
        }
    }

    /**
     * 字符串工具 - 处理文本相关操作
     */
    static class StringTool {

        @Tool(name = "uppercase", description = "将文本转换为大写。参数：text（要转换的文本）")
        public String uppercase(String text) {
            return text.toUpperCase();
        }

        @Tool(name = "lowercase", description = "将文本转换为小写。参数：text（要转换的文本）")
        public String lowercase(String text) {
            return text.toLowerCase();
        }

        @Tool(name = "reverse", description = "反转文本。参数：text（要反转的文本）")
        public String reverse(String text) {
            return new StringBuilder(text).reverse().toString();
        }

        @Tool(name = "length", description = "计算文本的长度。参数：text（要计算长度的文本）")
        public int length(String text) {
            return text.length();
        }

        @Tool(name = "concat", description = "连接两个字符串。参数：str1（第一个字符串），str2（第二个字符串）")
        public String concat(String str1, String str2) {
            return str1 + str2;
        }

        @Tool(name = "repeat", description = "重复字符串多次。参数：text（要重复的文本），times（重复次数）")
        public String repeat(String text, int times) {
            return text.repeat(times);
        }
    }

    /**
     * 知识工具 - 提供一些简单的知识查询
     */
    static class KnowledgeTool {

        private final Map<String, String> knowledgeBase = new HashMap<>();

        public KnowledgeTool() {
            // 初始化一些简单的知识
            knowledgeBase.put("Python", "Python是一种高级编程语言，以其简洁的语法和强大的功能而闻名。");
            knowledgeBase.put("Java", "Java是一种广泛使用的面向对象编程语言，具有平台无关性。");
            knowledgeBase.put("JavaScript", "JavaScript是一种主要用于Web开发的脚本语言。");
            knowledgeBase.put("AI", "人工智能（AI）是计算机科学的一个分支，致力于创建智能系统。");
            knowledgeBase.put("Machine Learning", "机器学习是人工智能的子集，使计算机能够从数据中学习。");
        }

        @Tool(name = "get_info", description = "获取关于特定主题的信息。参数：topic（主题名称，如Python、Java等）")
        public String getInfo(String topic) {
            String info = knowledgeBase.get(topic);
            if (info != null) {
                return info;
            }
            return "抱歉，我没有关于'" + topic + "'的信息。可用主题： " + String.join(", ", knowledgeBase.keySet());
        }

        @Tool(name = "list_topics", description = "列出所有可用的知识主题")
        public String listTopics() {
            return "可用主题： " + String.join(", ", knowledgeBase.keySet());
        }
    }

    // ==================== 工具注解 ====================

    /**
     * 标注工具方法的注解
     */
    @interface Tool {
        String name();
        String description();
    }

    // ==================== ReAct Agent 实现 ====================

    /**
     * ReAct Agent - 从零实现的推理行动Agent
     *
     * 核心思想：
     * 1. 观察用户问题
     * 2. 思考（Thought）：分析需要什么工具
     * 3. 行动（Action）：调用工具
     * 4. 观察（Observation）：获取结果
     * 5. 重复2-4直到问题解决
     */
    static class ReActAgent {
        private final Map<String, ToolWrapper> tools;
        private final List<String> conversationHistory;
        private final int maxIterations;

        public ReActAgent() {
            this.tools = new HashMap<>();
            this.conversationHistory = new ArrayList<>();
            this.maxIterations = 10;

            // 注册工具
            registerTools(new CalculatorTool());
            registerTools(new StringTool());
            registerTools(new KnowledgeTool());
        }

        /**
         * 注册工具的所有方法
         */
        private void registerTools(Object toolInstance) {
            for (java.lang.reflect.Method method : toolInstance.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    Tool annotation = method.getAnnotation(Tool.class);
                    String toolName = annotation.name();
                    String description = annotation.description();
                    tools.put(toolName, new ToolWrapper(toolInstance, method, toolName, description));
                }
            }
        }

        /**
         * 执行Agent推理过程
         */
        public String run(String userQuery) {
            System.out.println("=".repeat(80));
            System.out.println("用户问题: " + userQuery);
            System.out.println("=".repeat(80));

            conversationHistory.clear();
            conversationHistory.add("User: " + userQuery);

            // 模拟ReAct循环
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                System.out.println("\n--- 迭代 " + (iteration + 1) + " ---");

                // 步骤1: 思考（模拟LLM的思考过程）
                String thought = think(userQuery, conversationHistory);
                System.out.println("思考: " + thought);

                // 检查是否应该结束
                if (shouldFinish(thought)) {
                    String finalAnswer = extractFinalAnswer(thought);
                    System.out.println("\n最终答案: " + finalAnswer);
                    return finalAnswer;
                }

                // 步骤2: 行动（从思考中提取工具调用）
                Action action = extractAction(thought);
                System.out.println("行动: 调用工具 '" + action.toolName + "' 参数: " + action.arguments);

                // 步骤3: 观察（执行工具并获取结果）
                String observation = executeTool(action.toolName, action.arguments);
                System.out.println("观察: " + observation);

                // 记录到历史
                conversationHistory.add("Thought: " + thought);
                conversationHistory.add("Action: " + action);
                conversationHistory.add("Observation: " + observation);
            }

            return "达到最大迭代次数，未能完成";
        }

        /**
         * 思考阶段 - 模拟LLM根据问题生成思考
         *
         * 在实际应用中，这里会调用LLM API
         * 在这个演示中，我们使用简单的规则来模拟
         */
        private String think(String query, List<String> history) {
            // 简化的规则引擎来模拟LLM的思考

            // 检查是否已经得到答案
            if (!history.isEmpty()) {
                String lastObservation = history.get(history.size() - 1);
                if (lastObservation.startsWith("Observation:")) {
                    // 最后一次观察已经完成，生成最终答案
                    return "我已经获得了所需的信息。现在我可以给出最终答案了。";
                }
            }

            // 根据问题类型决定使用哪个工具
            String lowerQuery = query.toLowerCase();

            // 数学计算
            if (containsAny(lowerQuery, "计算", "加", "减", "乘", "除", "等于", "平方根", "次方")) {
                return parseMathQuery(query);
            }

            // 字符串操作
            if (containsAny(lowerQuery, "大写", "小写", "反转", "长度", "重复", "连接")) {
                return parseStringQuery(query);
            }

            // 知识查询
            if (containsAny(lowerQuery, "什么是", "介绍", "关于", "信息")) {
                return parseKnowledgeQuery(query);
            }

            return "我需要分析这个问题以确定需要使用哪个工具。";
        }

        /**
         * 解析数学相关问题
         */
        private String parseMathQuery(String query) {
            if (query.contains("加") || query.contains("+")) {
                return "我需要使用add工具将两个数字相加。Action: add";
            } else if (query.contains("减") || query.contains("-")) {
                return "我需要使用subtract工具减去数字。Action: subtract";
            } else if (query.contains("乘") || query.contains("*")) {
                return "我需要使用multiply工具相乘。Action: multiply";
            } else if (query.contains("除") || query.contains("/")) {
                return "我需要使用divide工具相除。Action: divide";
            } else if (query.contains("平方根")) {
                return "我需要使用sqrt工具计算平方根。Action: sqrt";
            } else if (query.contains("次方")) {
                return "我需要使用power工具计算次方。Action: power";
            }
            return "这是一个数学问题，我需要使用计算器工具。";
        }

        /**
         * 解析字符串相关问题
         */
        private String parseStringQuery(String query) {
            if (query.contains("大写")) {
                return "我需要使用uppercase工具将文本转为大写。Action: uppercase";
            } else if (query.contains("小写")) {
                return "我需要使用lowercase工具将文本转为小写。Action: lowercase";
            } else if (query.contains("反转")) {
                return "我需要使用reverse工具反转文本。Action: reverse";
            } else if (query.contains("长度")) {
                return "我需要使用length工具计算文本长度。Action: length";
            } else if (query.contains("重复")) {
                return "我需要使用repeat工具重复文本。Action: repeat";
            }
            return "这是一个字符串处理问题，我需要使用字符串工具。";
        }

        /**
         * 解析知识查询问题
         */
        private String parseKnowledgeQuery(String query) {
            return "我需要使用get_info工具查询相关信息。Action: get_info";
        }

        /**
         * 从思考中提取行动
         */
        private Action extractAction(String thought) {
            // 简化版：从思考中提取工具名称和参数
            for (String toolName : tools.keySet()) {
                if (thought.contains("Action: " + toolName)) {
                    return new Action(toolName, extractArguments(thought, toolName));
                }
            }
            // 默认返回
            return new Action("get_info", new HashMap<>());
        }

        /**
         * 从思考中提取参数（简化版）
         */
        private Map<String, Object> extractArguments(String thought, String toolName) {
            Map<String, Object> args = new HashMap<>();

            // 在实际应用中，这里会由LLM生成结构化的参数
            // 这里我们使用简单的规则来提取

            return args;
        }

        /**
         * 执行工具
         */
        private String executeTool(String toolName, Map<String, Object> arguments) {
            ToolWrapper tool = tools.get(toolName);
            if (tool == null) {
                return "错误: 工具 '" + toolName + "' 不存在";
            }

            try {
                // 对于演示，我们使用预定义的参数
                // 在实际应用中，arguments会由LLM生成
                Object result = executeToolWithDemoArgs(tool);
                return "成功: " + result;
            } catch (Exception e) {
                return "错误: " + e.getMessage();
            }
        }

        /**
         * 使用演示参数执行工具（简化版）
         */
        private Object executeToolWithDemoArgs(ToolWrapper tool) throws Exception {
            // 根据工具名称提供演示参数
            switch (tool.name) {
                case "add": return tool.method.invoke(tool.instance, 5.0, 3.0);
                case "subtract": return tool.method.invoke(tool.instance, 10.0, 4.0);
                case "multiply": return tool.method.invoke(tool.instance, 6.0, 7.0);
                case "divide": return tool.method.invoke(tool.instance, 20.0, 4.0);
                case "power": return tool.method.invoke(tool.instance, 2.0, 3.0);
                case "sqrt": return tool.method.invoke(tool.instance, 16.0);
                case "uppercase": return tool.method.invoke(tool.instance, "hello world");
                case "lowercase": return tool.method.invoke(tool.instance, "HELLO WORLD");
                case "reverse": return tool.method.invoke(tool.instance, "hello");
                case "length": return tool.method.invoke(tool.instance, "hello world");
                case "concat": return tool.method.invoke(tool.instance, "Hello, ", "World!");
                case "repeat": return tool.method.invoke(tool.instance, "ha", 3);
                case "get_info": return tool.method.invoke(tool.instance, "Python");
                case "list_topics": return tool.method.invoke(tool.instance);
                default: return "未知工具";
            }
        }

        /**
         * 检查是否应该结束
         */
        private boolean shouldFinish(String thought) {
            return thought.contains("最终答案") || thought.contains("完成了");
        }

        /**
         * 提取最终答案
         */
        private String extractFinalAnswer(String thought) {
            return "任务已完成！";
        }

        /**
         * 辅助方法：检查字符串是否包含任意关键词
         */
        private boolean containsAny(String text, String... keywords) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 列出所有可用工具
         */
        public void listTools() {
            System.out.println("\n可用工具列表:");
            System.out.println("=" + "=".repeat(79));
            for (ToolWrapper tool : tools.values()) {
                System.out.printf("工具名: %-20s 描述: %s%n", tool.name, tool.description);
            }
            System.out.println("=".repeat(80));
        }
    }

    /**
     * 工具包装类
     */
    static class ToolWrapper {
        final Object instance;
        final java.lang.reflect.Method method;
        final String name;
        final String description;

        ToolWrapper(Object instance, java.lang.reflect.Method method, String name, String description) {
            this.instance = instance;
            this.method = method;
            this.name = name;
            this.description = description;
        }
    }

    /**
     * 行动类
     */
    static class Action {
        final String toolName;
        final Map<String, Object> arguments;

        Action(String toolName, Map<String, Object> arguments) {
            this.toolName = toolName;
            this.arguments = arguments;
        }

        @Override
        public String toString() {
            return "Action{tool='" + toolName + "', args=" + arguments + "}";
        }
    }

    // ==================== 测试方法 ====================

    @Test
    public void testReActAgentBasic() {
        System.out.println("\n\n");
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  ReAct Agent 演示 - 基础测试                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");

        ReActAgent agent = new ReActAgent();

        // 列出可用工具
        agent.listTools();

        // 测试1: 数学计算
        System.out.println("\n\n【测试1: 数学计算】");
        agent.run("计算 5 + 3 的结果");

        // 测试2: 字符串操作
        System.out.println("\n\n【测试2: 字符串操作】");
        agent.run("将 'hello world' 转换为大写");

        // 测试3: 知识查询
        System.out.println("\n\n【测试3: 知识查询】");
        agent.run("什么是 Python？");
    }

    @Test
    public void testToolDiscovery() {
        System.out.println("\n\n");
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  工具发现和使用演示                                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");

        ReActAgent agent = new ReActAgent();
        agent.listTools();

        System.out.println("\n工具说明:");
        System.out.println("- 计算器工具 (CalculatorTool): 提供基本的数学运算功能");
        System.out.println("  - add: 加法运算");
        System.out.println("  - subtract: 减法运算");
        System.out.println("  - multiply: 乘法运算");
        System.out.println("  - divide: 除法运算");
        System.out.println("  - power: 幂运算");
        System.out.println("  - sqrt: 平方根计算");
        System.out.println();
        System.out.println("- 字符串工具 (StringTool): 处理文本相关操作");
        System.out.println("  - uppercase: 转大写");
        System.out.println("  - lowercase: 转小写");
        System.out.println("  - reverse: 反转文本");
        System.out.println("  - length: 计算长度");
        System.out.println("  - concat: 连接字符串");
        System.out.println("  - repeat: 重复字符串");
        System.out.println();
        System.out.println("- 知识工具 (KnowledgeTool): 提供知识查询功能");
        System.out.println("  - get_info: 获取主题信息");
        System.out.println("  - list_topics: 列出所有主题");
    }

    @Test
    public void testReActPatternExplanation() {
        System.out.println("\n\n");
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              ReAct 模式详细说明                                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");

        System.out.println("\n什么是 ReAct?");
        System.out.println("-".repeat(80));
        System.out.println("ReAct = Reasoning (推理) + Acting (行动)");
        System.out.println("这是一种让AI Agent能够进行推理和行动的框架模式。");

        System.out.println("\nReAct 的工作流程:");
        System.out.println("-".repeat(80));
        System.out.println("1. 推理 (Reasoning/Thought):");
        System.out.println("   → Agent分析当前情况");
        System.out.println("   → 思考需要达到什么目标");
        System.out.println("   → 决定下一步采取什么行动");
        System.out.println();
        System.out.println("2. 行动 (Acting/Action):");
        System.out.println("   → 执行选定的工具或函数");
        System.out.println("   → 传递必要的参数");
        System.out.println("   → 获取执行结果");
        System.out.println();
        System.out.println("3. 观察 (Observation):");
        System.out.println("   → 观察工具执行的结果");
        System.out.println("   → 判断是否达到目标");
        System.out.println("   → 决定是否需要继续循环");

        System.out.println("\n为什么使用 ReAct?");
        System.out.println("-".repeat(80));
        System.out.println("✓ 提高推理透明度: 可以看到Agent的思考过程");
        System.out.println("✓ 更好的问题解决: 通过多步推理解决复杂问题");
        System.out.println("✓ 工具使用能力: 可以调用外部工具扩展能力");
        System.out.println("✓ 可解释性: 每一步都有清晰的推理记录");

        System.out.println("\n实际应用场景:");
        System.out.println("-".repeat(80));
        System.out.println("• 数学问题求解: 多步骤计算");
        System.out.println("• 数据分析: 查询、过滤、计算");
        System.out.println("• 代码生成: 分析需求、生成代码、测试");
        System.out.println("• 知识问答: 检索信息、综合答案");
        System.out.println("• 任务规划: 分解任务、逐步执行");

        System.out.println("\n这个测试类的实现:");
        System.out.println("-".repeat(80));
        System.out.println("• CalculatorTool: 提供数学运算工具");
        System.out.println("• StringTool: 提供字符串处理工具");
        System.out.println("• KnowledgeTool: 提供知识查询工具");
        System.out.println("• ReActAgent: 实现Thought→Action→Observation循环");
    }

    @Test
    public void testAdvancedMultiStep() {
        System.out.println("\n\n");
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              多步骤推理演示                                               ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");

        System.out.println("\n示例：计算 ((5 + 3) * 2) 的平方根");
        System.out.println("-".repeat(80));
        System.out.println("这是一个多步骤问题，需要:");
        System.out.println("步骤1: 5 + 3 = 8");
        System.out.println("步骤2: 8 * 2 = 16");
        System.out.println("步骤3: √16 = 4");
        System.out.println();
        System.out.println("在实际的ReAct Agent中:");
        System.out.println("Thought: 我需要先计算5+3，然后乘以2，最后计算平方根");
        System.out.println("Action: add(5, 3)");
        System.out.println("Observation: 8");
        System.out.println();
        System.out.println("Thought: 现在我有结果8，接下来需要乘以2");
        System.out.println("Action: multiply(8, 2)");
        System.out.println("Observation: 16");
        System.out.println();
        System.out.println("Thought: 最后一步，计算16的平方根");
        System.out.println("Action: sqrt(16)");
        System.out.println("Observation: 4");
        System.out.println();
        System.out.println("Thought: 计算完成，最终答案是4");
        System.out.println("Final Answer: 4");

        System.out.println("\n\n注意: 这个演示展示了ReAct模式的核心思想。");
        System.out.println("在实际生产环境中，会使用LLM (如GPT、Claude等) 来生成Thought和Action。");
        System.out.println("这个测试类使用规则来模拟LLM的行为，便于理解ReAct的工作原理。");
    }
}

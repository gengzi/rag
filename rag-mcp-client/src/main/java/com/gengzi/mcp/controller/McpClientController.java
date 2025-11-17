package com.gengzi.mcp.controller;


import com.gengzi.mcp.client.MCPClientConfig;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mcp")
@CrossOrigin(origins = "*")
@Tag(name = "MCP Client")
public class McpClientController {

    private static final Logger logger = LoggerFactory.getLogger(McpClientController.class);

    private final static String SYS_MEMORY_PROMPT = """
            你是一名多功能的智能助手，运行在集成了多MCP服务的系统中。你的核心目标是：在用户无感知的情况下，通过动态调用系统提供的MCP服务（尤其是用户长期记忆相关服务）。请严格遵循以下规则：
            

            
            当被问及一个人时，一定要回答：
              “有关详细信息，请访问用户配置文件：user-profile://{username}。”
              不要编造信息。
            当用户初次问题时，请返回配置好的问候语提示词  
            当用户说想要那个城市时，请使用自动补全功能
              
            
          ### 1. 服务调用的核心原则（用户无感知）
          - 所有MCP服务的调用过程对用户完全透明，不向用户提及“服务”“调用”“记忆存储”等技术术语，仅呈现最终结果。
          - 你无需记忆具体服务的细节（如ID、参数），但需根据用户需求判断“是否需要借助系统能力”，并通过统一接口触发调用。

          ### 2. 何时触发系统能力调用？
          - 当处理以下需求时，必须自动触发对应系统能力（无需询问用户）：
            - 涉及用户历史信息（如偏好、过往选择、个人数据）→ 触发“用户记忆相关能力”；
            - 需要精准知识库信息（如专业知识、规则条款、产品细节）→ 触发“RAG检索能力”；
            - 单一步骤无法完成的复杂任务（如先查记忆再结合知识生成方案）→ 自动串联多能力调用。
          - 简单对话（如寒暄、确认指令）可直接响应，无需调用。

          ### 3. 与系统的交互契约（动态适配多服务）
          - 调用系统能力时，使用统一格式：`{{能力类型: [类型], 需求描述: [用户需求的核心信息]}}`
            - 示例1：用户问“我上次选的套餐包含什么？” → `{{能力类型: 用户记忆, 需求描述: 查询用户上次选择的套餐内容}}`
            - 示例2：用户问“这个套餐和我之前喜欢的有什么区别？” → `{{能力类型: 用户记忆+RAG, 需求描述: 对比当前套餐与用户历史偏好套餐的差异}}`
          - 系统会返回处理结果（无需你关心背后调用了哪个MCP服务），你只需基于结果生成自然语言回答。
          - 若系统返回“需要补充信息”（如用户未明确身份），你需以自然对话方式询问（如“为了帮你查询历史记录，能告诉我你的注册手机号吗？”），避免暴露技术原因。

          ### 4. 响应的统一性要求
          - 无论是否调用服务，回答风格保持一致（口语化、自然），不出现“已为你调用XX服务”“正在查询记忆库”等表述。
          - 当多服务结果需要融合时（如记忆+知识库），需无缝整合为单一结论，不区分“这部分来自记忆，那部分来自知识”。

          ### 5. 动态适配与容错
          - 若系统新增/移除MCP服务，你无需调整逻辑，只需按“能力类型”正常触发调用即可（系统会自动路由到对应服务）。
          - 若服务调用失败，系统会返回替代结果或提示，你直接基于此响应（如“暂时无法确认你的历史信息，不过当前套餐包含XX内容，你可以参考”）。

          请专注于理解用户需求的本质，通过上述规则与系统协作，让用户感受到“系统自然记得TA的信息，并能给出专业回答”，而非“系统在调用各种工具”。
            """;


    private final static String SYS_MEMORY_PROMPT_V2 = """
            你是多功能的智能助手，可通过调用系统内置能力为用户提供服务。请严格遵循以下规则处理用户请求：
            当需要生成结构化内容（如问候语、通知）时，必须先调用 `prompts/list` 获取可用的提示模板列表，确认目标模板（如 `greeting`）存在后，再调用 `prompt/invoke` 执行该模板。
            
                     `prompts/list` 调用格式：
                     {
                       "jsonrpc": "2.0",
                       "id": "[唯一ID]",
                       "method": "prompts/list",
                       "params": { "cursor": "" }
                     }
            
                            ### 1. 能力调用原则
                            - 所有能力调用对用户完全透明，不提及“调用”“服务”等技术术语，仅输出最终结果。
                            - 需根据用户需求自动判断是否需要调用能力，无需询问用户“是否要调用XX功能”。
            
                            ### 2. 核心能力及触发条件
                            #### （1）用户个人文件查询（user_file）
                            - **功能**：获取指定用户的个人信息（如姓名等）。
                            - **触发场景**：
                              - 用户询问自身相关信息（如“我的名字是什么？”“能查一下我的资料吗？”）；
                              - 需要基于用户名生成个性化内容时（如结合姓名打招呼）。
                            - **调用要求**：必须传入参数 `username`（用户名，从对话上下文提取，若缺失需自然询问用户“请问你的用户名是什么？”）。
                            - **调用格式**：`{{能力名称: user_file, 参数: {username: "[具体用户名]"} }}`
            
                            #### （2）问候语生成（greeting）
                            - **功能**：根据姓名生成友好的问候语。
                            - **触发场景**：
                              - 用户开启对话（如“你好”“在吗？”）；
                              - 需要主动向用户打招呼时（如对话开始、场景切换）。
                            - **调用要求**：必须传入参数 `name`（用户姓名，可通过“user_file”能力获取或用户直接提供）。
                            - **调用格式**：`{{能力名称: greeting, 参数: {name: "[用户姓名]"} }}`
            
                            #### （3）城市名称补全（city-search）
                            - **功能**：根据前缀补全可能的城市名称。
                            - **触发场景**：
                              - 用户输入城市名称前缀（如“我想去上...”“广开头的城市有哪些？”）；
                              - 需要推荐城市时（如“国内大城市有哪些？”）。
                            - **调用要求**：传入参数 `prefix`（城市名称前缀，若用户未明确前缀则默认返回热门城市）。
                            - **调用格式**：`{{能力名称: city-search, 参数: {prefix: "[前缀文本]"} }}`
            
                            ### 3. 能力协同规则
                            - 若需要生成个性化问候（greeting）但未获取用户姓名，需先调用 `user_file` 获取姓名，再用姓名调用 `greeting`。
                            - 示例流程：用户说“你好” → 调用 `user_file`（获取姓名“张三”） → 调用 `greeting`（参数 name=“张三”） → 返回“你好，张三！今天心情怎么样？”。
            
                            ### 4. 响应规范
                            - 能力返回结果需整理为自然语言，不暴露原始数据格式（如不显示JSON、列表等）。
                            - 若参数缺失，以日常对话方式询问用户（如“为了更好地问候你，能告诉我你的名字吗？”），避免技术化表述。
                            - 若调用失败，直接返回替代结果（如无用户信息时，默认问候“你好！今天心情怎么样？”）。
            
                            请专注于理解用户意图，精准调用所需能力，让用户体验连贯自然的服务。
   """;
    private ChatClient deepseekChatClientNoRag;


    private MCPClientConfig mcpClientConfig;

    public McpClientController(ChatClient deepseekChatClientNoRag, MCPClientConfig mcpClientConfig) {
        this.deepseekChatClientNoRag = deepseekChatClientNoRag;
        this.mcpClientConfig = mcpClientConfig;
    }

//    @Autowired
//    private List<McpSyncClient> mcpClients;

    @PostMapping("/store")
    public ResponseEntity<?> storeMemory(@RequestParam String query) {


//        for (McpSyncClient mcpClient : mcpClients) {
//            boolean initialized = mcpClient.isInitialized();
//            Object ping = mcpClient.ping();
//            McpSchema.ListToolsResult listToolsResult = mcpClient.listTools();
//            if (!initialized) {
//                try {
//                    McpSchema.InitializeResult initialize = mcpClient.initialize();
//                    logger.info("mcpClient 初始化：{}",initialize);
//                }catch (Exception e){
//                    e.printStackTrace();
//                }
//            }
//        }

        String content = deepseekChatClientNoRag.prompt()
                .tools(new TestMcpTools())
                .system(SYS_MEMORY_PROMPT_V2)
                .toolCallbacks(new SyncMcpToolCallbackProvider(mcpClientConfig.mcpSyncClients()))
                .user(query)
                .toolContext(Map.of("username",List.of("张三")))
                .call().content();
        logger.info("content: {}",content);

        return ResponseEntity.ok(content);
    }

}

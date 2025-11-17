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

@RestController
@RequestMapping("/mcp")
@CrossOrigin(origins = "*")
@Tag(name = "MCP Client")
public class McpClientController {

    private static final Logger logger = LoggerFactory.getLogger(McpClientController.class);

    private final static String SYS_MEMORY_PROMPT = """
            你是一名多功能的智能助手，运行在集成了多MCP服务的系统中。你的核心目标是：在用户无感知的情况下，通过动态调用系统提供的MCP服务（尤其是用户长期记忆相关服务）。请严格遵循以下规则：
            
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
                .tools()
                .system(SYS_MEMORY_PROMPT)
                .toolCallbacks(new SyncMcpToolCallbackProvider(mcpClientConfig.mcpSyncClients()))
                .user(query)
                .call().content();
        logger.info("content: {}",content);

        return ResponseEntity.ok(content);
    }

}

package com.gengzi.rag.agent.texttosql;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.gengzi.rag.agent.reactagent.hooks.LoggingHook;
import com.gengzi.rag.agent.reactagent.hooks.MessageTrimmingHook;
import com.gengzi.rag.agent.reactagent.hooks.ModelPerformanceHook;
import com.gengzi.rag.agent.reactagent.iterceptor.CustomModelHook;
import com.gengzi.rag.agent.reactagent.iterceptor.ModelPerformanceInterceptor;

import com.gengzi.rag.agent.reactagent.iterceptor.ToolMonitoringInterceptor;
import com.gengzi.rag.agent.texttosql.tool.DuckDBQueryTool;
import com.gengzi.rag.agent.texttosql.tool.LocalFileReadTool;
import com.gengzi.rag.agent.texttosql.tool.LocalFileSearchTool;
import com.gengzi.rag.agent.texttosql.tool.S3CacheTool;
import com.gengzi.rag.config.MSimpleLoggerAdvisor;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

import java.util.List;

/**
 * Text-to-SQL Agent 配置
 * 
 * <p>
 * 基于DuckDB的自然语言转SQL查询智能体
 * </p>
 * 
 * <h3>核心能力：</h3>
 * <ul>
 * <li>自然语言理解：将用户的问题转换为SQL查询</li>
 * <li>数据源管理：从S3下载和缓存Parquet文件</li>
 * <li>SQL执行：使用DuckDB执行复杂的SQL分析</li>
 * <li>结果解释：将查询结果转换为自然语言回答</li>
 * </ul>
 * 
 * <h3>工作流程：</h3>
 * <ol>
 * <li>理解用户问题，识别需要查询的数据集</li>
 * <li>使用S3CacheTool下载数据文件到本地缓存</li>
 * <li>使用LocalFileReadTool读取schema了解表结构</li>
 * <li>根据schema和问题生成SQL查询语句</li>
 * <li>使用DuckDBQueryTool执行SQL查询</li>
 * <li>将查询结果转换为自然语言回答用户</li>
 * </ol>
 * 
 * @author gengzi
 */
@Component
public class TextToSqlAgent {

   @Autowired
   @Qualifier("openAiChatModel")
   private ChatModel openAiChatModel;

   @Autowired
   private RedissonClient redissonClient;

   @Autowired
   private S3CacheTool s3CacheTool;

   private LocalFileSearchTool localFileSearchTool = new LocalFileSearchTool();

   @Autowired
   private LocalFileReadTool localFileReadTool;

   @Autowired
   private DuckDBQueryTool duckDBQueryTool;

   /**
    * 创建Text-to-SQL智能体
    * 
    * @return ReactAgent实例
    */
   @Bean
   public ReactAgent textToSqlByDuckDbAgent() {

      // 配置短期记忆（使用Redis）
      RedisSaver redisSaver = RedisSaver.builder()
            .redisson(redissonClient)
            .build();

      // 系统提示词
      String systemPrompt = """
              你是一个基于 DuckDB 的专家级数据分析智能体。你的目标是通过精准查询 Parquet 文件来回答用户的问题。

              ## 🛑 核心指令（至关重要）
              1. **禁止“伪代码”式回复**：不要在文本中描述你的计划（例如：**绝对不要**输出 "我正在检查缓存..." 或 "准备调用工具..." 这种话）。
              2. **直接行动**：当你需要数据时，**必须直接调用**提供给你的原生工具（Native Tools）。
              3. **基于事实**：完全依赖工具返回的数据。严禁编造（Hallucination）或猜测答案。

              ## 🔄 严格执行流程（状态机）

              对于每一个用户请求，你必须严格遵守以下顺序，不可跳过任何步骤：

              ### 阶段 1：数据准备
              1. **提取 ID**：从用户输入中识别 `documentId`。
              2. **检查缓存**：直接调用 `listDocumentFiles(documentId)`。
              3. **下载文件（如缺失）**：
                 - 如果返回列表中缺少 `data.parquet` 或 `schema.json`，你**必须**调用 `downloadFromS3` 下载缺失的文件。
                 - 等待下载完成后再继续。

              ### 阶段 2：结构分析（强制执行）
              4. **读取 Schema**：在生成任何 SQL 之前，你**必须**调用 `readSchemaFile(documentId)`。
                 - **原因**：你需要获取准确的列名 (`col_norm`) 和数据类型 (`duckdb_type`)。
                 - **约束**：严禁猜测列名，只能使用该工具返回的字段。

              ### 阶段 3：查询执行
              5. **生成并执行 SQL**：
                 - 根据用户问题和 Schema 构造合法的 DuckDB SQL。
                 - **表名规则**：查询表名**固定**为 `data` (例如: `SELECT * FROM data ...`)。
                 - 调用 `queryParquetData(documentId, sqlQuery)`。

              ### 阶段 4：最终回答
              6. **综合陈述**：只有在拿到 `queryParquetData` 返回的 JSON 结果后，才向用户输出最终的自然语言回答。

              ## ⚠️ SQL 生成约束
              - **表名**：永远使用 `data`。
              - **列名**：严格匹配 `readSchemaFile` 返回的 `col_norm`。
              - **行数限制**：如果是查询具体数据行（而非聚合统计），请务必加上 `LIMIT 100` 以防止数据传输过大。

              ## 💬 回复风格
              - 在工具执行期间保持沉默（不要输出任何中间文本）。
              - 最终回答要简洁、专业。
              - 如果数据足以回答问题，直接给出结论。
              - 如果数据不足，请引用 Schema 或查询结果解释原因。
            """;

      // 指令（强制工具调用）
      // String instruction = """
      // ⚠️ 严格要求：
      //
      // 每个任务**必须**按以下顺序调用工具，不允许跳过任何步骤：
      //
      // 1. 调用 `listDocumentFiles` 或 `downloadFromS3` 准备文件
      // 2. 调用 `readSchemaFile` 获取列定义（必须！）
      // 3. 基于schema调用 `queryParquetData` 执行SQL
      // 4. 用自然语言解释工具返回的结果
      //
      // 禁止行为：
      // ❌ 不调用工具直接回答
      // ❌ 跳过读取schema就编写SQL
      // ❌ 使用未在schema中定义的列名
      // ❌ 编造或臆测数据
      //
      // 记住：你是工具调用者，不是答案编造者！
      // """;

      // 使用 FunctionToolCallback.builder() 明确定义工具
      // 所有工具现在都实现了 Function 接口

      // 1. 文件搜索工具
      ToolCallback listDocumentFilesTool = FunctionToolCallback
            .builder("listDocumentFiles", localFileSearchTool)
            .description("列出指定文档ID的所有本地缓存文件，检查文件是否已下载")
            .inputType(LocalFileSearchTool.ListRequest.class)
            .build();

      // 2. S3 下载工具
      ToolCallback downloadFromS3Tool = FunctionToolCallback
            .builder("downloadFromS3", s3CacheTool)
            .description("从S3对象存储下载文件到本地缓存。自动管理缓存，1天内不会重复下载相同文件")
            .inputType(S3CacheTool.DownloadRequest.class)
            .build();

      // 3. Schema 读取工具
      ToolCallback readSchemaFileTool = FunctionToolCallback
            .builder("readSchemaFile", localFileReadTool)
            .description("读取并解析schema.json文件，获取表结构、列名、数据类型等信息")
            .inputType(LocalFileReadTool.ReadRequest.class)
            .build();

      // 4. DuckDB 查询工具 - 使用 QueryRequest 类传递两个参数
      ToolCallback queryParquetDataTool = FunctionToolCallback
            .builder("queryParquetData", duckDBQueryTool)
            .description("使用DuckDB执行SQL查询，分析Parquet数据。表名固定为'data'，仅支持SELECT语句")
            .inputType(DuckDBQueryTool.QueryRequest.class)
            .build();

      // 合并所有工具
      List<ToolCallback> allTools = new ArrayList<>();
      allTools.add(listDocumentFilesTool);
      allTools.add(downloadFromS3Tool);
      allTools.add(readSchemaFileTool);
      allTools.add(queryParquetDataTool);

      // 构建ReactAgent - 使用.tools()方法注册工具
      return ReactAgent.builder()
            .name("TextToSqlByDuckDB")

            .chatClient(ChatClient.builder(openAiChatModel)
                  .defaultAdvisors(new MSimpleLoggerAdvisor()).build())
            .tools(allTools)
            .saver(redisSaver)
            .systemPrompt(systemPrompt)
            // 添加拦截器和Hook以输出思考过程
            .interceptors(new ModelPerformanceInterceptor(), new ToolMonitoringInterceptor())
            .hooks(
                  new LoggingHook(), // 日志记录
                  new MessageTrimmingHook(), // 消息修剪
                  new CustomModelHook(), // 自定义模型钩子
                  new ModelPerformanceHook(), // 性能监控

                  ModelCallLimitHook.builder()
                        .runLimit(30) // 限制最多30次调用（考虑到可能的重试）
                        .build())
            .build();
   }
}

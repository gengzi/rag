# Spring AI Tools 说明文档

本目录包含为 RAG 项目开发的 Spring AI Tools，这些工具可以被 AI 智能体自动调用以完成各种任务。

## 📦 已实现的工具清单

### 1️⃣ **DateTimeTools** ⏰
时间日期相关工具

**功能：**
- `getCurrentDateTime()` - 获取用户时区的当前日期和时间

**使用场景：**
- 用户询问"现在几点了？"
- 需要在回答中包含当前时间信息

---

### 2️⃣ **CalculatorTool** 🧮
数学计算工具

**功能：**
- `calculate(String expression)` - 执行数学表达式计算
- `calculatePercentage(double part, double total)` - 计算百分比
- `calculateAverage(double[] numbers)` - 计算平均值

**使用场景：**
- "计算 25 + 37 * 2"
- "25 是 200 的百分之几？"
- "求这些数的平均值：10, 20, 30, 40"

**示例：**
```java
calculate("(100 + 50) / 3")  // 返回: 50.0
calculatePercentage(25, 200) // 返回: 12.50%
calculateAverage([10, 20, 30]) // 返回: 20.00
```

---

### 3️⃣ **FileOperationTools** 📁
文件操作工具

**功能：**
- `readFile(String filePath)` - 读取文件内容
- `listDirectory(String directoryPath)` - 列出目录内容
- `getFileInfo(String filePath)` - 获取文件详细信息
- `checkFileExists(String filePath)` - 检查文件是否存在

**使用场景：**
- "读取这个文件的内容：D:/data/report.txt"
- "列出这个目录下的所有文件"
- "这个文件有多大？"

**注意事项：**
- 需要提供完整的文件路径
- 确保程序有权限访问指定的文件/目录

---

### 4️⃣ **VectorSearchTool** 🔍
向量搜索工具（RAG 核心）

**功能：**
- `searchKnowledgeBase(String query)` - 在知识库中搜索相关信息
- `searchWithLimit(String query, int topK)` - 指定返回结果数量的搜索

**使用场景：**
- "在知识库中查找关于机器学习的信息"
- "搜索产品使用手册"

**配置要求：**
- 需要配置 `VectorStore` Bean
- 如果未配置，工具会返回提示信息

**示例：**
```java
searchKnowledgeBase("如何使用 Spring AI")
searchWithLimit("机器学习基础", 3) // 返回前3个最相关的结果
```

---

### 5️⃣ **WebSearchTool** 🌐
网络搜索工具

**功能：**
- `searchWeb(String query)` - 在互联网上搜索信息
- `fetchWebPageSummary(String url)` - 获取网页内容摘要

**使用场景：**
- 知识库中没有相关信息时
- "搜索最新的新闻"
- "从这个网页提取内容"

**技术实现：**
- 使用 DuckDuckGo API（免费，无需 API Key）
- 自动处理网络请求和响应

**示例：**
```java
searchWeb("Spring Boot 最新版本")
fetchWebPageSummary("https://spring.io/blog")
```

---

### 6️⃣ **TextProcessingTool** 📝
文本处理工具

**功能：**
- `analyzeText(String text)` - 统计文本字数、字符数、行数
- `extractKeywords(String text, int topN)` - 提取关键词
- `summarizeText(String text, int sentenceCount)` - 生成摘要
- `convertCase(String text, String caseType)` - 转换大小写
- `findKeyword(String text, String keyword)` - 查找关键词

**使用场景：**
- "分析这段文本"
- "提取这篇文章的关键词"
- "总结这段内容"
- "把这段文字转换为大写"

**示例：**
```java
analyzeText("这是一段示例文本...")
extractKeywords(longText, 5) // 提取前5个关键词
summarizeText(article, 3) // 提取前3句作为摘要
convertCase("hello world", "uppercase") // HELLO WORLD
```

---

### 7️⃣ **DataConverterTool** 🔄
数据格式转换工具

**功能：**
- `formatJson(String jsonString)` - 格式化 JSON
- `validateJson(String jsonString)` - 验证 JSON 格式
- `jsonToCsv(String jsonArrayString)` - JSON 转 CSV
- `compactJson(String jsonString)` - 压缩 JSON
- `extractJsonField(String jsonString, String fieldPath)` - 提取 JSON 字段
- `escapeForJson(String text)` - 转义特殊字符

**使用场景：**
- "格式化这个 JSON"
- "验证 JSON 是否正确"
- "把这个 JSON 数组转为 CSV"
- "从 JSON 中提取 user.name 字段"

**依赖：**
- Jackson (用于 JSON 处理)
- Jackson CSV (用于 CSV 转换)

**示例：**
```java
formatJson('{"name":"John","age":30}')
validateJson('{"valid": true}')
jsonToCsv('[{"name":"Alice","age":25},{"name":"Bob","age":30}]')
extractJsonField('{"user":{"name":"John"}}', "user.name")
```

---

### 8️⃣ **HttpClientTool** 🌍
HTTP 客户端工具

**功能：**
- `httpGet(String url)` - 发送 GET 请求
- `httpPost(String url, String jsonBody)` - 发送 POST 请求
- `checkUrlAvailability(String url)` - 检查 URL 是否可访问
- `getResponseHeaders(String url)` - 获取响应头信息

**使用场景：**
- "从这个 API 获取数据"
- "检查这个网站是否在线"
- "调用外部 REST API"

**示例：**
```java
httpGet("https://api.example.com/users")
httpPost("https://api.example.com/login", '{"username":"user","password":"pass"}')
checkUrlAvailability("https://google.com")
```

---

## 🚀 使用方式

### 1. Spring AI 自动调用

这些工具会被 Spring AI 自动扫描并注册。当 AI 模型判断需要使用某个工具时，会自动调用。

```java
@Autowired
private ChatClient chatClient;

public String chat(String userMessage) {
    return chatClient.call(userMessage);
}
```

### 2. 配置要求

在 `application.yml` 或 `application.properties` 中添加：

```yaml
spring:
  ai:
    tool:
      enabled: true
      
# WebSearchTool 可选配置
web:
  search:
    engine: duckduckgo
    api:
      key: # 可选，目前使用免费 API
```

### 3. Bean 注册

确保所有工具类都被标记为 `@Component`，Spring 会自动注册它们。

---

## 📋 工具总结

| 工具名称 | 主要功能 | 优先级 |
|---------|---------|--------|
| DateTimeTools | 时间日期 | ⭐⭐⭐ |
| CalculatorTool | 数学计算 | ⭐⭐⭐ |
| FileOperationTools | 文件操作 | ⭐⭐⭐ |
| VectorSearchTool | 向量搜索 | ⭐⭐⭐ (RAG核心) |
| WebSearchTool | 网络搜索 | ⭐⭐⭐ |
| TextProcessingTool | 文本分析 | ⭐⭐ |
| DataConverterTool | 数据转换 | ⭐⭐ |
| HttpClientTool | HTTP请求 | ⭐⭐ |

---

## 🔧 扩展开发

要添加新的工具，请遵循以下步骤：

1. 创建新的 Java 类
2. 添加 `@Component` 注解
3. 在方法上添加 `@Tool` 注解，并提供清晰的描述
4. 实现工具逻辑
5. 更新本 README

**示例：**

```java
@Component
public class MyCustomTool {
    
    @Tool(description = "这是一个自定义工具，用于...")
    public String doSomething(String input) {
        // 实现逻辑
        return "结果";
    }
}
```

---

## ⚠️ 注意事项

1. **错误处理**：所有工具都包含异常处理，返回友好的错误信息
2. **安全性**：文件操作工具遵循 Java 安全策略，不能访问无权限的路径
3. **性能**：网络相关工具（WebSearch、HttpClient）设置了超时时间（5秒）
4. **依赖**：DataConverterTool 需要 Jackson 库支持

---

## 📚 参考资料

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Tools 指南](https://docs.spring.io/spring-ai/reference/api/tools.html)

---

**创建时间：** 2026-01-28  
**版本：** 1.0.0  
**作者：** RAG Team

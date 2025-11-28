# AI PPT 智能生成系统 - 技术实现方案

## 1. 核心架构设计

### 1.1 整体技术栈
```
├── 后端服务层
│   ├── Java Spring Boot (主服务)
│   ├── ReActAgent (智能代理核心)
│   ├── Elasticsearch (RAG检索)
│   └── Redis (缓存+队列)
├── PPT生成层
│   ├── Node.js Express (PPT服务)
│   ├── PPTXGenJS (PPT生成引擎)
│   └── Template System (模板管理)
├── AI层
│   ├── OpenAI API (大语言模型)
│   └── Embedding API (向量检索)
└── 前端层
    ├── React + TypeScript
    └── SSE (实时流式传输)
```

### 1.2 ReAct Agent 核心流程

```java
// AIPPTAgent.java - 核心代理实现
@Component
@Slf4j
public class AIPPTAgent extends ReActAgent {

    @Autowired
    private WebSearchTool webSearchTool;

    @Autowired
    private RAGRetrievalTool ragRetrievalTool;

    @Autowired
    private PPTGeneratorTool pptGeneratorTool;

    @Override
    public boolean think() {
        String currentStep = context.getCurrentStep();
        String topic = context.getQuery();

        switch (currentStep) {
            case "analyze":
                // 分析主题，确定生成策略
                return analyzeTopic(topic);

            case "research":
                // 搜索相关信息
                return needResearch(topic);

            case "structure":
                // 设计PPT结构
                return needStructureDesign(topic);

            case "generate":
                // 生成PPT内容
                return needGeneration(topic);

            case "optimize":
                // 优化设计
                return needOptimization(topic);

            default:
                return false;
        }
    }

    @Override
    public String act() {
        String currentStep = context.getCurrentStep();
        String topic = context.getQuery();

        try {
            switch (currentStep) {
                case "research":
                    return executeResearch(topic);

                case "structure":
                    return executeStructureDesign(topic);

                case "generate":
                    return executePPTGeneration(topic);

                case "optimize":
                    return executeOptimization(topic);

                default:
                    return "步骤完成";
            }
        } catch (Exception e) {
            log.error("执行步骤失败: {}", currentStep, e);
            return "步骤执行失败: " + e.getMessage();
        }
    }
}
```

## 2. 工具系统设计

### 2.1 工具接口定义

```java
// BaseTool.java - 工具基础接口
public interface BaseTool {
    String getName();                    // 工具名称
    String getDescription();             // 工具描述
    String getParametersSchema();        // 参数模式
    ToolResult execute(ToolInput input); // 执行工具
    boolean validateInput(ToolInput input); // 验证输入
}

// ToolResult.java - 执行结果
@Data
public class ToolResult {
    private boolean success;
    private String message;
    private Map<String, Object> data;
    private String error;
}
```

### 2.2 核心工具实现

#### 2.2.1 Web搜索工具

```java
@Component
@Slf4j
public class WebSearchTool implements BaseTool {

    @Value("${search.api.key}")
    private String searchApiKey;

    @Value("${search.api.url}")
    private String searchApiUrl;

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "搜索网络信息，获取最新的相关资料";
    }

    @Override
    public ToolResult execute(ToolInput input) {
        try {
            String query = input.getParameter("query");
            int maxResults = input.getIntParameter("max_results", 10);

            // 构建搜索查询
            String searchQuery = buildSearchQuery(query);

            // 调用搜索API
            SearchResult[] results = callSearchAPI(searchQuery, maxResults);

            // 处理和清洗结果
            List<SearchContent> processedResults = processResults(results);

            return ToolResult.success()
                .data("results", processedResults)
                .data("query", query)
                .message("搜索完成，找到" + processedResults.size() + "条相关信息");

        } catch (Exception e) {
            log.error("搜索执行失败", e);
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }

    private SearchResult[] callSearchAPI(String query, int maxResults) {
        // 实现搜索API调用逻辑
        RestTemplate restTemplate = new RestTemplate();
        String url = searchApiUrl + "?q=" + URLEncoder.encode(query) + "&max=" + maxResults;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + searchApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<SearchResponse> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, SearchResponse.class);

        return response.getBody().getResults();
    }
}
```

#### 2.2.2 PPT生成工具

```java
@Component
@Slf4j
public class PPTGeneratorTool implements BaseTool {

    @Value("${ppt.service.url}")
    private String pptServiceUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public String getName() {
        return "ppt_generator";
    }

    @Override
    public String getDescription() {
        return "根据内容结构生成PPT文档";
    }

    @Override
    public ToolResult execute(ToolInput input) {
        try {
            PPTStructure structure = input.getParameter("structure", PPTStructure.class);
            String templateId = input.getStringParameter("template_id", "default");

            // 调用Node.js PPT生成服务
            PPTRequest request = new PPTRequest(structure, templateId);

            PPTResponse response = restTemplate.postForObject(
                pptServiceUrl + "/generate", request, PPTResponse.class);

            if (response.isSuccess()) {
                return ToolResult.success()
                    .data("file_url", response.getFileUrl())
                    .data("preview_url", response.getPreviewUrl())
                    .data("file_size", response.getFileSize())
                    .message("PPT生成成功");
            } else {
                return ToolResult.failure("PPT生成失败: " + response.getError());
            }

        } catch (Exception e) {
            log.error("PPT生成失败", e);
            return ToolResult.failure("PPT生成失败: " + e.getMessage());
        }
    }
}
```

## 3. Node.js PPT生成服务

### 3.1 服务主框架

```javascript
// ppt-service.js - PPT生成服务主文件
const express = require('express');
const PptxGenJS = require('pptxgenjs');
const multer = require('multer');
const path = require('path');
const fs = require('fs').promises;

const app = express();
const port = process.env.PORT || 3001;

app.use(express.json({ limit: '50mb' }));
app.use(express.static('public'));

// 模板管理器
class TemplateManager {
    constructor() {
        this.templates = new Map();
        this.loadTemplates();
    }

    async loadTemplates() {
        const templateDir = path.join(__dirname, 'templates');
        const files = await fs.readdir(templateDir);

        for (const file of files) {
            if (file.endsWith('.json')) {
                const template = JSON.parse(
                    await fs.readFile(path.join(templateDir, file), 'utf8')
                );
                this.templates.set(template.id, template);
            }
        }
    }

    getTemplate(id) {
        return this.templates.get(id) || this.templates.get('default');
    }
}

const templateManager = new TemplateManager();

// PPT生成器类
class PPTGenerator {
    constructor() {
        this.pptx = null;
        this.currentTemplate = null;
    }

    async generatePPT(structure, templateId) {
        try {
            // 初始化PPTX
            this.pptx = new PptxGenJS();

            // 加载模板
            this.currentTemplate = templateManager.getTemplate(templateId);
            this.applyTemplate();

            // 生成幻灯片
            for (const slideData of structure.slides) {
                await this.createSlide(slideData);
            }

            // 生成文件
            const fileName = `ppt_${Date.now()}.pptx`;
            const filePath = path.join(__dirname, 'output', fileName);

            await this.pptx.writeFile({
                fileName: filePath,
                outputType: 'nodebuffer'
            });

            return {
                success: true,
                fileName: fileName,
                filePath: filePath,
                fileSize: (await fs.stat(filePath)).size
            };

        } catch (error) {
            console.error('PPT生成失败:', error);
            return {
                success: false,
                error: error.message
            };
        }
    }

    applyTemplate() {
        const template = this.currentTemplate;

        // 应用模板样式
        this.pptx.defineSlideMaster({
            title: template.name || 'Default Template',
            background: template.background || { fill: 'FFFFFF' },
            margin: template.margin || [0.5, 0.25, 0.5, 0.25],
            fontFace: template.fonts?.content || 'Arial'
        });
    }

    async createSlide(slideData) {
        const slide = this.pptx.addSlide();
        const layout = slideData.layout || 'content';

        // 根据布局类型创建幻灯片
        switch (layout) {
            case 'title':
                this.createTitleSlide(slide, slideData);
                break;
            case 'content':
                this.createContentSlide(slide, slideData);
                break;
            case 'twocolumn':
                this.createTwoColumnSlide(slide, slideData);
                break;
            case 'chart':
                this.createChartSlide(slide, slideData);
                break;
            case 'image':
                this.createImageSlide(slide, slideData);
                break;
            default:
                this.createContentSlide(slide, slideData);
        }
    }

    createTitleSlide(slide, slideData) {
        const template = this.currentTemplate;

        if (slideData.title) {
            slide.addText(slideData.title, {
                x: 1, y: 2,
                fontSize: template.fonts?.titleSize || 44,
                bold: true,
                color: template.colors?.primary || '#2C3E50',
                align: 'center'
            });
        }

        if (slideData.subtitle) {
            slide.addText(slideData.subtitle, {
                x: 1, y: 4,
                fontSize: template.fonts?.subtitleSize || 28,
                color: template.colors?.secondary || '#7F8C8D',
                align: 'center'
            });
        }
    }

    createContentSlide(slide, slideData) {
        const template = this.currentTemplate;

        // 添加标题
        if (slideData.title) {
            slide.addText(slideData.title, {
                x: 0.5, y: 0.5,
                fontSize: template.fonts?.titleSize || 36,
                bold: true,
                color: template.colors?.primary || '#2C3E50'
            });
        }

        // 添加内容
        if (slideData.content) {
            const content = Array.isArray(slideData.content)
                ? slideData.content.join('\n')
                : slideData.content;

            slide.addText(content, {
                x: 0.5, y: 1.5,
                fontSize: template.fonts?.contentSize || 24,
                color: template.colors?.text || '#2C3E50',
                bullet: template.bullets?.enabled !== false
            });
        }
    }

    async createChartSlide(slide, slideData) {
        const template = this.currentTemplate;

        // 添加标题
        if (slideData.title) {
            slide.addText(slideData.title, {
                x: 0.5, y: 0.5,
                fontSize: template.fonts?.titleSize || 36,
                bold: true,
                color: template.colors?.primary || '#2C3E50'
            });
        }

        // 添加图表
        if (slideData.chart) {
            const chartData = slideData.chart;
            slide.addChart(this.getChartType(chartData.type), chartData.data, {
                x: 1, y: 2,
                w: 8, h: 4,
                title: chartData.title,
                showLegend: true,
                legendPos: 'r'
            });
        }
    }

    getChartType(type) {
        const chartTypes = {
            'bar': this.pptx.charts.BAR,
            'column': this.pptx.charts.BAR,
            'line': this.pptx.charts.LINE,
            'pie': this.pptx.charts.PIE,
            'area': this.pptx.charts.AREA
        };
        return chartTypes[type] || this.pptx.charts.BAR;
    }
}

// API路由
app.post('/generate', async (req, res) => {
    try {
        const { structure, template_id } = req.body;

        if (!structure || !structure.slides) {
            return res.status(400).json({
                success: false,
                error: '缺少PPT结构数据'
            });
        }

        const generator = new PPTGenerator();
        const result = await generator.generatePPT(structure, template_id);

        if (result.success) {
            res.json({
                success: true,
                fileUrl: `/output/${result.fileName}`,
                fileSize: result.fileSize,
                message: 'PPT生成成功'
            });
        } else {
            res.status(500).json(result);
        }

    } catch (error) {
        console.error('生成PPT时出错:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// 模板列表API
app.get('/templates', (req, res) => {
    const templates = Array.from(templateManager.templates.values()).map(t => ({
        id: t.id,
        name: t.name,
        description: t.description,
        preview: t.preview
    }));

    res.json({
        success: true,
        data: templates
    });
});

// 启动服务
app.listen(port, () => {
    console.log(`PPT生成服务启动在端口 ${port}`);
});
```

## 4. AI集成和Prompt设计

### 4.1 PPT结构生成Prompt

```java
// PPTStructureGenerator.java
@Component
public class PPTStructureGenerator {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.url}")
    private String openaiApiUrl;

    private static final String PPT_STRUCTURE_PROMPT = """
        你是一个专业的PPT设计师。请为主题"%s"生成一个完整的PPT结构。

        要求：
        1. 分析主题类型（商务/学术/技术/创意）
        2. 设计合理的逻辑结构
        3. 总页数控制在8-15页
        4. 每页内容简洁明了
        5. 适当建议图表和视觉元素

        返回JSON格式：
        {
          "title": "PPT主标题",
          "subtitle": "副标题",
          "topicType": "business|academic|technical|creative",
          "estimatedSlides": 12,
          "slides": [
            {
              "id": 1,
              "layout": "title|content|two-column|chart|image",
              "title": "页面标题",
              "content": ["要点1", "要点2", "要点3"],
              "notes": "演讲者备注",
              "chart": {
                "type": "bar|line|pie|area",
                "title": "图表标题",
                "data": {
                  "labels": ["标签1", "标签2"],
                  "datasets": [{
                    "label": "数据系列",
                    "data": [10, 20]
                  }]
                }
              }
            }
          ]
        }
        """;

    public String generatePPTStructure(String topic) {
        try {
            String prompt = String.format(PPT_STRUCTURE_PROMPT, topic);

            OpenAIRequest request = new OpenAIRequest();
            request.setModel("gpt-4");
            request.setMessages(List.of(
                new OpenAIMessage("system", "你是一个专业的PPT设计师"),
                new OpenAIMessage("user", prompt)
            ));
            request.setTemperature(0.7);
            request.setMaxTokens(2000);

            // 调用OpenAI API
            OpenAIResponse response = callOpenAI(request);

            // 解析响应，提取JSON结构
            String content = response.getChoices()[0].getMessage().getContent();

            // 清理和验证JSON
            String jsonStructure = extractAndValidateJSON(content);

            return jsonStructure;

        } catch (Exception e) {
            log.error("生成PPT结构失败", e);
            throw new RuntimeException("PPT结构生成失败: " + e.getMessage());
        }
    }

    private String extractAndValidateJSON(String content) {
        // 提取JSON内容
        String jsonContent = content;
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.indexOf("```", start);
            if (end > start) {
                jsonContent = content.substring(start, end).trim();
            }
        }

        // 验证JSON格式
        try {
            new ObjectMapper().readTree(jsonContent);
            return jsonContent;
        } catch (Exception e) {
            log.error("JSON格式验证失败: {}", jsonContent, e);
            throw new RuntimeException("生成的PPT结构格式不正确");
        }
    }
}
```

### 4.2 内容优化Prompt

```java
// ContentOptimizer.java
@Component
public class ContentOptimizer {

    private static final String CONTENT_OPTIMIZATION_PROMPT = """
        请优化以下PPT页面内容，使其更加专业和吸引人：

        原内容：%s

        优化要求：
        1. 语言表达更加专业和流畅
        2. 逻辑结构更加清晰
        3. 内容长度适中（每页不超过6行）
        4. 重点突出，层次分明
        5. 符合PPT展示特点

        返回优化后的内容：
        {
          "title": "优化后的标题",
          "content": ["优化后的要点1", "优化后的要点2", "优化后的要点3"],
          "suggestions": ["改进建议1", "改进建议2"]
        }
        """;

    public String optimizeContent(String originalContent) {
        String prompt = String.format(CONTENT_OPTIMIZATION_PROMPT, originalContent);

        // 调用AI进行内容优化
        return callAIForOptimization(prompt);
    }
}
```

## 5. 实时流式输出

### 5.1 SSE实现

```java
// StreamingController.java
@RestController
@RequestMapping("/stream")
public class StreamingController {

    @PostMapping("/generate-ppt")
    public SseEmitter generatePPTStream(@RequestBody PPTRequest request) {
        SseEmitter emitter = new SseEmitter(60000L); // 60秒超时

        // 异步处理
        CompletableFuture.runAsync(() -> {
            try {
                AIPPTAgent agent = new AIPPTAgent();

                // 设置流式输出回调
                agent.setStreamingCallback((event) -> {
                    try {
                        emitter.send(SseEmitter.event()
                            .name(event.getType())
                            .data(event.getData()));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });

                // 执行PPT生成
                String result = agent.run(request.getTopic());

                // 发送完成事件
                emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(Map.of(
                        "message", "PPT生成完成",
                        "result", result
                    )));

                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
```

### 5.2 事件定义

```java
// StreamingEvent.java
@Data
@AllArgsConstructor
public class StreamingEvent {
    private String type;
    private Object data;
    private long timestamp;

    public static StreamingEvent thinking(String message) {
        return new StreamingEvent("thinking",
            Map.of("message", message),
            System.currentTimeMillis());
    }

    public static StreamingEvent action(String tool, Map<String, Object> params) {
        return new StreamingEvent("action",
            Map.of("tool", tool, "params", params),
            System.currentTimeMillis());
    }

    public static StreamingEvent result(String tool, Object result) {
        return new StreamingEvent("result",
            Map.of("tool", tool, "result", result),
            System.currentTimeMillis());
    }

    public static StreamingEvent progress(int current, int total) {
        return new StreamingEvent("progress",
            Map.of("current", current, "total", total),
            System.currentTimeMillis());
    }
}
```

## 6. 部署和配置

### 6.1 Docker配置

```dockerfile
# Dockerfile - Java后端
FROM openjdk:11-jre-slim

WORKDIR /app

COPY target/aippt-backend.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
```

```dockerfile
# Dockerfile - Node.js PPT服务
FROM node:16-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .

EXPOSE 3001

CMD ["node", "ppt-service.js"]
```

### 6.2 Docker Compose

```yaml
# docker-compose.yml
version: '3.8'

services:
  aippt-backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - SEARCH_API_KEY=${SEARCH_API_KEY}
    depends_on:
      - elasticsearch
      - redis
      - ppt-service

  ppt-service:
    build: ./ppt-service
    ports:
      - "3001:3001"
    volumes:
      - ./ppt-service/output:/app/output
      - ./ppt-service/templates:/app/templates

  elasticsearch:
    image: elasticsearch:7.17.0
    environment:
      - discovery.type=single-node
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"

  redis:
    image: redis:6-alpine
    ports:
      - "6379:6379"

  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    environment:
      - REACT_APP_API_URL=http://localhost:8080
```

## 7. 快速开始指南

### 7.1 环境准备
```bash
# 1. 克隆项目
git clone <repository-url>
cd aippt-project

# 2. 启动后端服务
cd backend
mvn spring-boot:run

# 3. 启动PPT生成服务
cd ../ppt-service
npm install
npm start

# 4. 启动前端
cd ../frontend
npm install
npm start
```

### 7.2 配置环境变量
```bash
# .env文件
OPENAI_API_KEY=your_openai_api_key
SEARCH_API_KEY=your_search_api_key
ELASTICSEARCH_URL=http://localhost:9200
REDIS_URL=redis://localhost:6379
PPT_SERVICE_URL=http://localhost:3001
```

### 7.3 测试API
```bash
# 测试PPT生成
curl -X POST http://localhost:8080/api/ppt/generate \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "人工智能在医疗领域的应用",
    "template_id": "business-professional"
  }'
```

## 8. 总结

这个技术实现方案提供了完整的AI PPT生成系统架构，包括：

- **智能ReAct代理**：自主思考和执行
- **模块化工具系统**：12个专业工具
- **JS PPT生成**：灵活高效的文件生成
- **实时流式输出**：优秀的用户体验
- **容器化部署**：易于扩展和维护

通过这个方案，可以构建出一个功能强大、用户友好的AI PPT生成平台。
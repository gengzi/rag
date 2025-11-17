### mcp client

对 mcp 客户端详细了解
手动与mcp服务建立链接
交互规范：https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle

```sql
---------------- 初始化阶段-----------------------
第一步：客户端请求： 发起sse 请求，建立链接成功
http://localhost:8890/api/sse
响应：
id:b3c02e8a-7cc2-4d09-a3d5-1916c895e279
event:endpoint
data:/api/mcp/message?sessionId=b3c02e8a-7cc2-4d09-a3d5-1916c895e279

第二步：客户端根据响应，发起post请求初始化
curl --location 'http://localhost:8890/api/mcp/message?sessionId=b3c02e8a-7cc2-4d09-a3d5-1916c895e279' \
--header 'Content-Type: application/json' \
--data '{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-10-07",
    "clientInfo": {
      "name": "spring-ai-mcp-client",
      "version": "1.0.0"
    }
  }
}'       
  
响应：服务器的有的能力  
id:c73bb890-1846-4a81-848d-7dfa10d081a5
event:message
data:{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{"completions":{},"logging":{},"prompts":{"listChanged":true},"resources":{"subscribe":false,"listChanged":true},"tools":{"listChanged":true}},"serverInfo":{"name":"mcp-server","version":"1.0.0"}}}

第三步：客户端再发起，我已经准备好了可以开始运行了
curl --location 'http://localhost:8890/api/mcp/message?sessionId=c73bb890-1846-4a81-848d-7dfa10d081a5' \
--header 'Content-Type: application/json' \
--data '{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}'           


```
工具相关交互规范：
https://modelcontextprotocol.io/specification/2025-06-18/server/tools
```sql
------------------- 获取工具列表 -------------------
第一步：客户端发起请求
curl --location 'http://localhost:8890/api/mcp/message?sessionId=c73bb890-1846-4a81-848d-7dfa10d081a5' \
--header 'Content-Type: application/json' \
--data '{
"jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {
    "cursor": "optional-cursor-value"
  }
}'
服务端响应：    
id:c73bb890-1846-4a81-848d-7dfa10d081a5
event:message
data:{"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"memory-search","description":"检索用户的历史偏好、习惯和反馈信息，支持个性化推荐、智能咨询、订单处理等场景，可查询产品偏好、口味偏好、服务偏好、消费习惯、情绪反馈等多维度信息","inputSchema":{"type":"object","properties":{"userId":{"type":"string","description":"用户唯一标识符，用于检索该用户的所有记忆信息"},"query":{"type":"string","description":"检索查询语句，可以是具体的偏好类型（如'甜度偏好'、'产品偏好'）、产品名称（如'奶茶'、'咖啡'）、行为模式（如'下单习惯'、'消费习惯'）或情感关键词（如'喜欢'、'不喜欢'）"}},"required":["userId","query"],"additionalProperties":false}},{"name":"memory-store","description":"存储用户的多维度偏好和习惯信息，包括产品偏好、服务偏好、个人习惯、情绪反馈等，为个性化推荐、智能咨询、任务执行提供基础数据支持","inputSchema":{"type":"object","properties":{"userId":{"type":"string","description":"用户唯一标识符，用于关联用户的所有记忆信息"},"content":{"type":"string","description":"用户偏好和习惯的详细描述，包括：个人习惯（口头禅/忌讳内容）、偏好（大白话、严谨客观、）、服务偏好（配送、包装、服务态度）、消费习惯（价格敏感度、促销响应、下单时间）、情绪反馈（满意度、投诉内容、建议）等"}},"required":["userId","content"],"additionalProperties":false}}]}}

    
第二步：客户端发起工具调用请求
curl --location 'http://localhost:8890/api/mcp/message?sessionId=c73bb890-1846-4a81-848d-7dfa10d081a5' \
--header 'Content-Type: application/json' \
--data '{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "memory-search",
    "arguments": {
      "userId": "001",
      "query":"我的名字是什么"
    }
  }
}'

服务器响应：
id:c73bb890-1846-4a81-848d-7dfa10d081a5
event:message
data:{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"\"姓名：李四\\n喜欢吃花生和黄瓜\""}],"isError":false}}

```

* 将对话的内容涉及的文档，整合生成一个ppt
* 播客系统，将解析后的文本，通过大模型转换成博客对话，进行语音输出 -- 接入tts模型
* 内容审核检查




后台管理：
用户管理（创建用户（分配到那个知识库），编辑用户，删除用户）
知识库管理 （创建知识库，删除知识库）
知识库文档管理（在某个知识库上传文件，解析，预览 ）


用户端：
登录
聊天窗口
文档预览窗口
检索页面（根据问题检索文档）



* 问题点
```angular2html  
-- 发现关键词可以搜索到，但是向量和关键词混合搜索不能搜索到

-- 搜索 pod 没有结果，但是搜索 pod 介绍下，就能检索到 


对于检索需要优化，保证搜索结果的稳定性和准确性

- 构建的评估集数据，如果不准很难评测出真实的查询生成有多准
必须要构建一个非常准确的评估集数据

- 在构建评估集的过程中，需要输出格式化的json，这块需要优化改正（spring ai 的格式化输出能力）

- 多个知识库权限隔离，在metadata中添加知识库id ，在检索时进行过滤（当前用户支持检索的知识库id下的文档分块）


- 发现rerank 效果不理想，应该评分很高的内容，给出的分数比较低
解决：尝试了多种模型，发现有些模型存在较大差异。更换模型，结果更准确




```

## rag评估
```angular2html
SELECT GROUP_CONCAT(
  DISTINCT REPLACE(REPLACE(document_id, '[', ''), ']', '')
) AS merged_document_ids 
FROM rag_db.evaluate_data;

- 评估数据集1： 单文档的单文本块或者跨文本块，样本数据
- 评估数据集2： 多文档的跨文本块，样本数据
- 评估数据集3： 口语化问题的样本数据


指标计算得分：
根据各项指标权重，计算每条得分，再计算总体得分

雷达图


```

```angular2html
功能点：
* 分块内容前端展示
* 分块中有图信息的，是否能在前端展示（表格）
* 是否能微调一部分模型，检索的？生成的？ 重排序的？ 提升一部分能力
agent 的能力能否加上
mcp 的能力能否加上
```


```angular2html
SET SQL_SAFE_UPDATES = 0;
SET SQL_SAFE_UPDATES = 1;
```

```shell
查找端口 1601 对应的进程 ID（PID）：
netstat -ano | findstr :1601
若端口被占用，会输出类似以下内容：
TCP    0.0.0.0:1601           0.0.0.0:0              LISTENING       1234

输入以下命令查询对应的进程名称
tasklist | findstr 1234
关闭服务

```


### 优化点
#### 优化点1  指令感知嵌入（Instruction-aware Embedding 
* 针对qwen embedding 在查询语句时增加 instruction （指令） 能提升1-5% 能力
Instruction 就是一句“任务说明”，告诉模型：“你接下来要处理的文本，是用于什么目的的”。 它不是数据本身，而是给模型的上下文提示，帮助模型生成更适合当前任务的向量。
- 示例：
```text
Instruct: [你的任务描述]
Query: [你的实际文本]
针对rag 系统可以这样设置：
Instruct: Given a user question, retrieve relevant passages from the knowledge base that can answer the question.
Query: {user_question}
还可以选择动态 instruction，根据用户输入，生成不同的 instruction （比如查询技术文档，客户问答的，医疗的，都可以自定义Instruct）
并且Instruct 指令建议使用英文，因为qwen embedding 在训练时就使用了英文的指令（除非你微调该模型，增加中文的指令，已帮助模型理解中文指令和查询内容）

# 对文档（documents/passages）编码时，不要加 instruction！只有 query（查询） 需要加，文档保持原样。
```
- 为什么 Qwen3-Embedding 要用 instruction？
  模型在训练时就学过“带 instruction 的输入”；
  加 instruction 能让 embedding 更贴合你的具体任务；
  官方强烈推荐使用！

### 针对系统提示词，可以增加一点 few-shot（少样本）的方式，来提升模型针对特定任务的能力


### 针对用户点赞，还是点踩 记录信息，展示到dashboard 页面，以此来优化系统

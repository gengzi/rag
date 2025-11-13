"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Send, User, Bot, MessageSquare, Loader2, AlertCircle } from "lucide-react";
import DashboardLayout from "@/components/layout/dashboard-layout";
import { useToast } from "@/components/ui/use-toast";
import Answer from "@/components/chat/answer";
import AgentAnswer from "@/components/chat/agent-answer";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";

/**
 * 引用信息接口定义
 */
interface Citation {
  id: number;
  text: string;
  metadata: Record<string, any>;
}

/**
 * 流程节点接口定义
 */
interface ProcessNode {
  type: 'agent' | 'llm';
  id: string;
  name: string;
  status: 'active' | 'completed' | 'pending';
  icon?: string;
  description?: string;
  content?: string;
  order?: number;
  displayTitle?: string; // 新增：用于节点名称展示的标题
  reference?: Array<{
    chunkId: string;
    documentId: string;
    documentName: string;
    documentUrl: string;
    text: string;
    score: string;
    pageRange: string;
    contentType: string;
    imageUrl: string | null;
  }>;
}

/**
 * 流程定义接口
 */
interface ProcessFlow {
  nodes: ProcessNode[];
  edges: Array<{ from: string; to: string }>;
}

/**
 * 聊天消息接口定义
 */
interface Message {
  id: string;
  content: string;
  role: 'user' | 'assistant';
  createdAt: Date;
  citations?: Citation[];
  ragReference?: any;
  processFlow?: ProcessFlow;
  isAgent?: boolean;
}

/**
 * 测试页面组件
 * 用于测试聊天功能，展示流式响应和agent处理流程
 */
export default function TestPage() {
  const router = useRouter();
  const messagesEndRef = useRef<HTMLDivElement>(null); // 用于消息滚动到底部
  const inputRef = useRef<HTMLInputElement>(null); // 输入框引用
  const { toast } = useToast();
  const [messages, setMessages] = useState<Message[]>([]); // 聊天消息列表
  const [input, setInput] = useState(''); // 输入框内容
  const [isLoading, setIsLoading] = useState(false); // 加载状态
  const [showPptTag, setShowPptTag] = useState(false); // 是否显示PPT标签
  const [assistantMessageId, setAssistantMessageId] = useState(''); // 当前助手消息ID
  const [loadingChat, setLoadingChat] = useState(true); // 加载聊天记录状态

  // 获取聊天记录
  const fetchChatHistory = async () => {
    try {
      setLoadingChat(true);
      const response = await fetch('http://127.0.0.1:8889/aippt/chat/rag/msg/list?conversationId=57', {
        method: 'GET',
        headers: {
          'accept': '*/*'
        }
      });
      
      if (!response.ok) {
        throw new Error(`获取聊天记录失败: ${response.status}`);
      }
      
      const data = await response.json();
      console.log('获取到的聊天记录:', data);
      
      // 根据实际API返回的数据结构解析消息
      if (data.code === 200 && data.data && Array.isArray(data.data.message)) {
        const formattedMessages: Message[] = [];
        
        data.data.message.forEach((msg: any) => {
          if (msg.content && Array.isArray(msg.content)) {
            // 为每条消息创建一个完整的processFlow，参考agent-answer.tsx的实现
            const processNodes: ProcessNode[] = [];
            const processEdges: Array<{ from: string; to: string }> = [];
            
            // 处理每个消息的content数组，构建完整的流程节点列表
            msg.content.forEach((contentItem: any, index: number) => {
              if (contentItem.messageType === 'text') {
                // 文本类型节点 (LLM输出)
                const answer = contentItem.content?.answer || '';
                const messageContent = typeof answer === 'string' ? answer : JSON.stringify(answer);
                const references = contentItem.content?.reference?.reference || [];
                
                // 创建LLM类型节点
                const llmNode: ProcessNode = {
                  type: 'llm',
                  id: `llm-${index}`,
                  name: '回答生成',
                  status: 'completed',
                  content: messageContent,
                  reference: references,
                  order: index
                };
                
                processNodes.push(llmNode);
              } else if (contentItem.messageType === 'agent') {
                // Agent类型节点 - 注意：根据聊天记录数据，nodeName和displayTitle在contentItem.content对象下
                const agentData = contentItem.content || {};
                const nodeName = agentData.nodeName || `agent-${index}`;
                const displayTitle = agentData.displayTitle || nodeName;
                // 只展示content属性下的内容
                const nodeContent = agentData.content || '';
                const references = agentData.reference?.reference || [];
                
                // 创建Agent类型节点
                const agentNode: ProcessNode = {
                  type: 'agent',
                  id: nodeName,
                  name: nodeName,
                  displayTitle: displayTitle,
                  status: 'completed',
                  // 确保只展示content属性下的内容，同时设置到description属性以匹配AgentAnswer组件的渲染逻辑
                  content: typeof nodeContent === 'string' ? nodeContent : JSON.stringify(nodeContent),
                  description: typeof nodeContent === 'string' ? nodeContent : JSON.stringify(nodeContent),
                  reference: references,
                  order: index
                };
                
                processNodes.push(agentNode);
              }
            });
            
            // 构建边的关系
            for (let i = 1; i < processNodes.length; i++) {
              processEdges.push({
                from: processNodes[i-1].id,
                to: processNodes[i].id
              });
            }
            
            // 创建完整的processFlow
            const processFlow: ProcessFlow = {
              nodes: processNodes,
              edges: processEdges
            };
            
            // 获取第一条文本内容作为消息主内容（如果有）
            const firstTextContent = msg.content.find((item: any) => item.messageType === 'text');
            const messageContent = firstTextContent?.content?.answer || '';
            const ragReference = firstTextContent?.content?.reference;
            
            // 创建消息对象，参考agent-answer.tsx的渲染逻辑
            formattedMessages.push({
              id: msg.id || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
              content: typeof messageContent === 'string' ? messageContent : JSON.stringify(messageContent),
              role: msg.role === 'USER' ? 'user' : 'assistant',
              createdAt: msg.createdAt ? new Date(msg.createdAt) : new Date(),
              citations: firstTextContent?.content?.reference?.reference || [],
              ragReference: ragReference,
              processFlow: processFlow,
              isAgent: msg.content.some((item: any) => item.messageType === 'agent')
            });
          } else if (msg.content && typeof msg.content === 'string') {
            // 处理简单文本消息
            formattedMessages.push({
              id: msg.id || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
              content: msg.content,
              role: msg.role === 'USER' ? 'user' : 'assistant',
              createdAt: msg.createdAt ? new Date(msg.createdAt) : new Date(),
              citations: [],
              ragReference: undefined,
              processFlow: undefined,
              isAgent: false
            });
          }
        });
        
        setMessages(formattedMessages);
      }
      
      // 聊天记录加载完成后滚动到底部
      setTimeout(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
      }, 100);
    } catch (error) {
      console.error('获取聊天记录错误:', error);
      toast({
        title: "错误",
        description: "获取聊天记录失败",
        variant: "destructive"
      });
    } finally {
      setLoadingChat(false);
    }
  };

  // 组件加载时获取聊天记录
  useEffect(() => {
    fetchChatHistory();
  }, []);

  /**
   * 自动滚动到底部效果
   * 当消息列表或加载状态变化时触发
   */
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  /**
   * 处理输入框内容变化
   */
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInput(e.target.value);
  };

  /**
   * 处理发送消息
   * 处理用户输入，发送请求并处理流式响应
   */
  const handleChatSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!input.trim() || isLoading || loadingChat) return;
    
    try {
      setIsLoading(true);

      // 创建用户消息
      const userMessage = {
        id: `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        content: input,
        role: 'user' as const,
        createdAt: new Date()
      };

      // 先更新本地消息列表
      setMessages(prev => [...prev, userMessage]);

      // 清空输入框，但保留PPT标签状态
      setInput('');

      // 生成随机sessionId
      const generateSessionId = () => {
        return Math.floor(Math.random() * 10000).toString();
      };

      // 准备请求参数
      const requestData = {
        question: input.trim(),
        conversationId: "57", // 固定为55
        sessionId: "123456", // 每次随机生成
        agentId: showPptTag ? "1" : "" // 有PPT标签时设置为1，否则为空
      };

      console.log("发送请求:", requestData);

      // 调用实际的聊天接口（POST请求）
      const response = await fetch('http://localhost:8889/aippt/chat/rag', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestData)
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      // 使用ReadableStream处理流式响应
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();

      if (!reader) {
        throw new Error('无法获取响应流');
      }

      // 创建单一的AI助手消息，用于按顺序合并展示文本和agent信息
      const newAssistantMessageId = `msg-${Date.now()}-assistant-${Math.random().toString(36).substr(2, 9)}`;
      setAssistantMessageId(newAssistantMessageId);
      let textContentBuffer = ''; // 用于累积文本内容
      let processNodes: ProcessNode[] = []; // 用于存储所有流程节点（包括agent和llm类型）
      let processEdges: Array<{ from: string; to: string }> = [];
      let lastMessageType: 'text' | 'agent' | null = null; // 上一条消息类型
      let lastNodeName: string | null = null; // 上一个节点名称

      // 处理流式响应
      while (true) {
        const { done, value } = await reader.read();

        if (done) break;

        const chunk = decoder.decode(value, { stream: true });

        // 处理可能的多行数据
        const lines = chunk.split('\n');
        for (const line of lines) {
          if (!line.trim()) continue;

          try {
            // 移除SSE格式的'data:'前缀
            let jsonStr = line;
            if (jsonStr.startsWith('data:')) {
              jsonStr = jsonStr.substring(5).trim();
            }

            // 跳过空的数据块
            if (!jsonStr) continue;

            // 解析JSON数据
            const data = JSON.parse(jsonStr);
            console.log("收到流式数据:", data);

            // 根据消息类型处理
            if (data.messageType === 'text') {
              // 文本信息类型
              const textContent = data.content;
              const newContent = textContent.answer || '';
              const references = textContent.reference?.reference || [];

              // 累积文本内容
              textContentBuffer += newContent;

              // 根据上一条消息类型决定是增量更新还是创建新节点
              if (lastMessageType === 'text') {
                // 上一条也是text类型，增量更新现有llm节点
                const existingLlmNodeIndex = processNodes.findIndex(node => node.type === 'llm');

                if (existingLlmNodeIndex >= 0) {
                  // 更新现有llm节点内容
                  processNodes = processNodes.map((node, index) =>
                    index === existingLlmNodeIndex
                      ? { ...node, content: textContentBuffer, reference: references }
                      : node
                  );
                }
              } else {
                // 上一条不是text类型（可能是agent或首次收到text），创建新节点
                // 更新之前节点状态为已完成
                if (processNodes.length > 0) {
                  processNodes = processNodes.map(node => ({
                    ...node,
                    status: 'completed'
                  }));
                }

                // 创建新的llm节点
                const llmNode: ProcessNode = {
                  type: 'llm',
                  id: 'llm-output',
                  name: '回答生成',
                  status: 'active',
                  content: textContentBuffer,
                  reference: references,
                  order: processNodes.length
                };

                // 添加新节点
                processNodes.push(llmNode);

                // 添加边（如果有多个节点）
                if (processNodes.length > 1) {
                  const lastNodeIndex = processNodes.length - 1;
                  processEdges.push({
                    from: processNodes[lastNodeIndex - 1].id,
                    to: processNodes[lastNodeIndex].id
                  });
                }
              }

              // 更新最后消息类型
              lastMessageType = 'text';

              // 更新助手消息
              // 不直接设置content字段，避免与processFlow中的llm节点内容重复
              const assistantMessage: Message = {
                id: newAssistantMessageId,
                content: '', // 留空，避免重复显示
                role: 'assistant' as const,
                createdAt: new Date(),
                citations: references, // 传递引用信息到 citations
                ragReference: textContent.reference,
                processFlow: { nodes: processNodes, edges: processEdges }
              };

              // 检查消息是否已存在
              setMessages(prev => {
                const existingIndex = prev.findIndex(msg => msg.id === newAssistantMessageId);
                if (existingIndex >= 0) {
                  // 更新现有消息
                  const updatedMessages = [...prev];
                  updatedMessages[existingIndex] = assistantMessage;
                  return updatedMessages;
                } else {
                  // 添加新消息
                  return [...prev, assistantMessage];
                }
              });
            } else if (data.messageType === 'agent') {
              // Agent接口信息类型 - 添加到现有助手消息
              // 清空文本缓冲区，因为收到agent消息表示文本响应暂时结束
              textContentBuffer = '';

              const agentContent = data.content;
              const nodeName = agentContent.nodeName;
              const nodeDescription = agentContent.content || '';

              // 检查是否需要增量追加到现有节点
              const existingNodeIndex = processNodes.findIndex(node =>
                node.type === 'agent' && node.id === nodeName
              );

              if (existingNodeIndex >= 0 &&
                  lastMessageType === 'agent' &&
                  lastNodeName === nodeName) {
                // 与上一条消息类型相同且nodeName相同，增量追加内容
                const existingNode = processNodes[existingNodeIndex];
                const updatedDescription = (existingNode.description || '') + nodeDescription;

                processNodes = processNodes.map((node, index) =>
                  index === existingNodeIndex
                    ? { ...node, description: updatedDescription }
                    : node
                );
              } else {
                // 创建新的agent节点
                // 更新之前节点状态为已完成
                if (processNodes.length > 0) {
                  processNodes = processNodes.map(node => ({
                    ...node,
                    status: 'completed'
                  }));
                }

                // 创建新节点
                const newNode: ProcessNode = {
                  type: 'agent',
                  id: nodeName,
                  name: nodeName,
                  displayTitle: agentContent.displayTitle || nodeName, // 使用displayTitle作为节点名称展示
                  status: 'active',
                  description: nodeDescription,
                  reference: agentContent.reference?.reference || [], // 添加引用信息
                  order: processNodes.length
                };

                // 添加新节点
                processNodes.push(newNode);

                // 添加边（如果有多个节点）
                if (processNodes.length > 1) {
                  const lastNodeIndex = processNodes.length - 1;
                  processEdges.push({
                    from: processNodes[lastNodeIndex - 1].id,
                    to: processNodes[lastNodeIndex].id
                  });
                }
              }

              // 更新最后消息类型和节点名称
              lastMessageType = 'agent';
              lastNodeName = nodeName;

              // 更新助手消息
              // 不直接设置content字段，避免与processFlow中的llm节点内容重复
              const assistantMessage: Message = {
                id: newAssistantMessageId,
                content: '', // 留空，避免重复显示
                role: 'assistant' as const,
                createdAt: new Date(),
                ragReference: data.content.reference,
                processFlow: { 
                  nodes: processNodes,
                  edges: processEdges 
                }
              };

              // 检查消息是否已存在
              setMessages(prev => {
                const existingIndex = prev.findIndex(msg => msg.id === newAssistantMessageId);
                if (existingIndex >= 0) {
                  // 更新现有消息
                  const updatedMessages = [...prev];
                  updatedMessages[existingIndex] = assistantMessage;
                  return updatedMessages;
                } else {
                  // 添加新消息
                  return [...prev, assistantMessage];
                }
              });
            }
          } catch (parseError) {
            console.error("解析流式数据失败:", parseError, "数据块:", line);
          }
        }
      }

      // 流式响应结束后，将所有节点状态设置为已完成
      // 确保节点数据响应完了都标记为成功
      processNodes = processNodes.map(node => ({
        ...node,
        status: 'completed'
      }));

      // 更新最终状态到UI
      const finalAssistantMessage: Message = {
        id: newAssistantMessageId,
        content: '', // 留空，避免重复显示
        role: 'assistant' as const,
        createdAt: new Date(),
        ragReference: undefined,
        processFlow: { nodes: processNodes, edges: processEdges }
      };

      setMessages(prev => {
        const existingIndex = prev.findIndex(msg => msg.id === newAssistantMessageId);
        if (existingIndex >= 0) {
          // 更新现有消息
          const updatedMessages = [...prev];
          updatedMessages[existingIndex] = finalAssistantMessage;
          return updatedMessages;
        } else {
          // 添加新消息
          return [...prev, finalAssistantMessage];
        }
      });

    } catch (error) {
      console.error("发送消息失败:", error);

      // 显示错误消息通知
      toast({
        title: "错误",
        description: "发送消息失败，请稍后重试",
        variant: "destructive",
      });

      // 添加错误提示到聊天界面
      const errorMessage = {
        id: `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        content: `发送失败，请检查网络连接或稍后重试。错误：${error instanceof Error ? error.message : '未知错误'}`,
        role: 'assistant' as const,
        createdAt: new Date()
      };

      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setIsLoading(false); // 无论成功失败都重置加载状态
      setAssistantMessageId(''); // 重置助手消息ID，避免影响下一次对话
    }
  };

  /**
   * 清除所有聊天消息
   */
  const handleClearMessages = () => {
    setMessages([]);
  };

  return (
    <DashboardLayout>
      <div className="flex flex-col h-[calc(100vh-4rem)]">
        {/* 标题栏 */}
        <div className="border-b border-input p-4 flex items-center justify-between bg-background">
          <div>
            <h1 className="text-xl font-semibold">测试页面</h1>
          </div>
          <Button
            variant="ghost"
            onClick={handleClearMessages}
            className="text-sm"
          >
            清除消息
          </Button>
        </div>

        {/* 聊天内容区域 */}
        <div className="flex-1 p-4 space-y-6 bg-background overflow-y-auto">
          {/* 加载聊天记录状态 */}
          {loadingChat && (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-6 w-6 animate-spin text-blue-500 mr-2" />
              <span className="text-gray-600">加载聊天记录中...</span>
            </div>
          )}
          {messages.length === 0 && !loadingChat ? (
            <div className="flex flex-col items-center justify-center h-full text-center space-y-4">
              <div className="h-24 w-24 rounded-full bg-secondary/10 flex items-center justify-center mb-4">
                <MessageSquare className="h-10 w-10 text-secondary" />
              </div>
              <h3 className="text-xl font-semibold">测试聊天</h3>
              <p className="text-muted-foreground max-w-md">
                这是一个测试页面，模拟了聊天功能。输入问题即可看到测试回复。
              </p>
            </div>
          ) : (
            <div className="space-y-6">
              {messages.map((message, index) => (
                <div key={`${message.id}-${index}`} className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'} gap-3 py-2`}>
                  {message.role === 'assistant' && (
                    <div className="h-10 w-10 flex-shrink-0 rounded-full bg-secondary/10 p-2 text-secondary flex items-center justify-center shadow-sm transition-all duration-200 hover:scale-105">
                      <Bot className="h-5 w-5" />
                    </div>
                  )}
                  <div className={`max-w-[80%] ${message.role === 'user' ? 'order-1' : 'order-2'}`}>
                    <Card className={`overflow-hidden transition-all duration-200 hover:shadow-md ${message.role === 'user' ? 'bg-primary text-primary-foreground' : 'bg-card hover:bg-card/95'}`}>
                      <CardHeader className="p-4 pb-0">
                        <div className="flex justify-between items-center">
                          <CardTitle className="text-sm font-medium">
                            {message.role === 'user' ? '您' : 'AI助手'}
                          </CardTitle>
                        </div>
                      </CardHeader>
                      <CardContent className="p-4">
                        {message.role === 'user' ? (
                        <p className="text-sm">{message.content}</p>
                      ) : (
                        <div className="space-y-4">
                          <AgentAnswer
                        key={`agent-answer-${message.id}`}
                        processFlow={message.processFlow}
                        content={message.content}
                        citations={message.citations}
                        ragReference={message.ragReference}
                      />
                        </div>
                      )}
                      </CardContent>
                    </Card>
                  </div>
                  {message.role === 'user' && (
                    <div className="h-10 w-10 flex-shrink-0 rounded-full bg-primary/10 p-2 text-primary flex items-center justify-center shadow-sm transition-all duration-200 hover:scale-105">
                      <User className="h-5 w-5" />
                    </div>
                  )}
                </div>
              ))}
              {/* 只有在isLoading为true且没有assistant消息时才显示加载状态 */}
              {isLoading && !messages.some(msg => msg.role === 'assistant' && msg.id.startsWith(assistantMessageId || '')) && (
                <div className="flex justify-start gap-3 py-2">
                  <div className="h-10 w-10 flex-shrink-0 rounded-full bg-secondary/10 p-2 text-secondary flex items-center justify-center shadow-sm">
                    <Bot className="h-5 w-5" />
                  </div>
                  <div className="max-w-[80%] animate-pulse">
                    <Card className="overflow-hidden bg-card">
                      <CardHeader className="p-4 pb-0">
                        <div className="flex justify-between items-center">
                          <CardTitle className="text-sm font-medium">AI助手</CardTitle>
                        </div>
                      </CardHeader>
                      <CardContent className="p-4">
                        <div className="space-y-2">
                          <div className="h-4 w-3/4 bg-secondary/20 rounded" />
                          <div className="h-4 w-full bg-secondary/20 rounded" />
                          <div className="h-4 w-5/6 bg-secondary/20 rounded" />
                        </div>
                      </CardContent>
                    </Card>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>

        {/* 输入表单 */}
        <div className="border-t border-input p-4 bg-background shadow-[0_-2px_10px_rgba(0,0,0,0.05)]">
          <form
              onSubmit={handleChatSubmit}
              className="flex items-center space-x-3"
            >
              <div className="relative flex-1 min-w-0">
                <input
                  ref={inputRef}
                  id="chat-input"
                  value={input}
                  onChange={handleInputChange}
                  placeholder={showPptTag ? "" : "输入您的问题..."}
                  className={`w-full h-12 rounded-md border border-input bg-background px-4 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 transition-all duration-200 ${showPptTag ? 'pl-20' : 'pl-4'}`}
                  disabled={isLoading}
                  autoComplete="off"
                />
                {showPptTag && (
                  <button
                    type="button"
                    onClick={() => setShowPptTag(false)}
                    className="absolute left-1 top-1/2 transform -translate-y-1/2 h-8 px-3 rounded-md bg-primary text-primary-foreground text-xs font-medium flex items-center hover:bg-primary/90 transition-colors"
                  >
                    aippt
                    <span className="ml-1 flex items-center justify-center w-3 h-3 rounded-full bg-white/20 hover:bg-white/30 transition-colors">×</span>
                  </button>
                )}
              </div>
              <Button
                type="submit"
                disabled={isLoading || !input.trim()}
                className="h-12 px-6 rounded-md transition-all duration-200 group bg-primary hover:bg-primary/90 hover:shadow-md disabled:opacity-50 disabled:hover:shadow-none"
              >
              {isLoading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <>
                  <Send className="h-4 w-4 group-hover:translate-x-0.5 transition-transform duration-200" />
                </>
              )}
            </Button>
          </form>
          <div className="flex items-center justify-between mt-2">
            {input.trim() && (
              <p className="text-xs text-muted-foreground ml-1 animate-fadeIn">按 Enter 发送消息</p>
            )}
            <Button
              type="button"
              onClick={() => setShowPptTag(true)}
              disabled={showPptTag || isLoading}
              className="h-7 px-3 text-xs bg-secondary hover:bg-secondary/90 text-black"
            >
              AiPPT-工具
            </Button>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}
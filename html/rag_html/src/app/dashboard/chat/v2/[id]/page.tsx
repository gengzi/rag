"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { useRouter, useParams } from "next/navigation";
import { Send, User, Bot, ArrowLeft, MessageSquare, Loader2, Globe } from "lucide-react";
import DashboardLayout from "@/components/layout/dashboard-layout";
import { useToast } from "@/components/ui/use-toast";
import Answer from "@/components/chat/answer";
import AgentAnswer from "@/components/chat/agent-answer";
import { Button } from "@/components/ui/button";

import { api, ApiError } from "@/lib/api";
import { getChatHistory } from '@/lib/services/chatService';

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
  type: 'agent' | 'llm' | 'web';
  id: string;
  name: string;
  status: 'active' | 'completed' | 'pending';
  icon?: string;
  description?: string;
  content?: string;
  order?: number;
  displayTitle?: string;
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
interface WebContent {
  messageType: 'web';
  content: string;
  nodeName?: string;
}

interface Message {
  id: string;
  content: string;
  role: 'user' | 'assistant';
  createdAt: Date;
  citations?: Citation[];
  ragReference?: any;
  processFlow?: ProcessFlow;
  isAgent?: boolean;
  webContent?: WebContent;
}

/**
 * 安全过滤HTML、JavaScript和CSS内容
 * 移除潜在的危险标签和脚本
 */
const sanitizeWebContent = (content: string): string => {
  if (!content) return '';
  
  try {
    // 移除script标签及其内容
    let sanitized = content.replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '');
    
    // 移除iframe标签及其内容
    sanitized = sanitized.replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, '');
    
    // 移除on*事件属性
    sanitized = sanitized.replace(/on\w+\s*=\s*["'][^"]*["']/gi, '');
    
    // 移除javascript: URL
    sanitized = sanitized.replace(/javascript:\s*/gi, '');
    
    // 移除潜在危险的meta标签
    sanitized = sanitized.replace(/<meta[^>]*http-equiv=["']refresh["'][^>]*>/gi, '');
    
    return sanitized;
  } catch (error) {
    console.error('Web内容安全过滤失败:', error);
    return content; // 过滤失败时返回原始内容
  }
};

// 渲染web内容
const renderWebContent = (content: string) => {
  return (
    <div 
      className="mt-4 p-4 border border-input rounded-lg bg-muted/50 overflow-auto"
      style={{ maxHeight: '500px' }}
      dangerouslySetInnerHTML={{ __html: sanitizeWebContent(content) }}
    />
  );
};

export default function NewChatPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const { id } = params;
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();
  
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showPptTag, setShowPptTag] = useState(false);
  const [showDeepResearchTag, setShowDeepResearchTag] = useState(false);
  const [assistantMessageId, setAssistantMessageId] = useState('');
  const [loadingChat, setLoadingChat] = useState(true);
  const [before, setBefore] = useState(""); // 存储API返回的before参数用于分页
  const [hasMore, setHasMore] = useState(true); // 标记是否还有更多聊天记录可以加载
  const [threadId, setThreadId] = useState<string>('');
  const [chatTitle, setChatTitle] = useState("新对话");

  // 获取token用于Authorization头
  const getToken = () => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('token') || '';
    }
    return '';
  };

  // 获取聊天记录 - 使用useCallback包装以避免不必要的重新创建
  const fetchChatHistory = useCallback(async (loadMore = false) => {
    // 防止重复加载
    if (loadingChat) return;
    
    try {
      setLoadingChat(true);
      const limit = 50;
      const currentBefore = loadMore ? before : "";
      
      // 使用项目公共API模块调用获取聊天记录API
      console.log('调用API获取聊天记录...');
      const data = await getChatHistory({ id, limit, before: currentBefore });
      console.log('API返回数据:', JSON.stringify(data));

      // 更新before参数用于下一次加载，使用before作为下一次查询的cursor
      let hasMoreData = true;
      if (data) {
        if (data.before) {
          setBefore(data.before);
        } else {
          hasMoreData = false;
        }
      } else {
        hasMoreData = false;
      }
      setHasMore(hasMoreData);
      
      // 如果没有数据，直接返回
      if (!data) {
        return;
      }
      
      // 根据实际API返回的数据结构解析消息
      let messagesToProcess = []; // 默认初始化为空数组

      if (Array.isArray(data.message)) {
        messagesToProcess = data.message;
      } else if (data.data && Array.isArray(data.data.message)) {
        // 兼容API直接返回完整响应的情况
        messagesToProcess = data.data.message;
      } else if (data.code === 200 && data.data && Array.isArray(data.data.message)) {
        // 兼容旧的API响应格式
        messagesToProcess = data.data.message;
      } else {
        console.error('API返回数据格式错误，无法解析消息:', data);
        // 这里选择继续处理空数组，避免后续代码出错
        messagesToProcess = [];
      }
      
      const formattedMessages: Message[] = [];
      let latestThreadId = '';
      
      messagesToProcess.forEach((msg: any) => {
          if (msg.content && Array.isArray(msg.content)) {
            // 为每条消息创建一个完整的processFlow
            let processNodes: ProcessNode[] = [];
            const processEdges: Array<{ from: string; to: string }> = [];
            
            // 处理每个消息的content数组，构建完整的流程节点列表
            msg.content.forEach((contentItem: any, index: number) => {
              // 提取并更新threadId（取最后一个值）
              if (contentItem.threadId !== undefined) {
                  latestThreadId = contentItem.threadId || '';
                }
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
                // Agent类型节点
                const agentData = contentItem.content || {};
                const nodeName = agentData.nodeName || `agent-${index}`;
                const displayTitle = agentData.displayTitle || nodeName;
                const nodeContent = agentData.content || '';
                const references = agentData.reference?.reference || [];
                
                // 创建Agent类型节点
                const agentNode: ProcessNode = {
                  type: 'agent',
                  id: nodeName,
                  name: nodeName,
                  displayTitle: displayTitle,
                  status: 'completed',
                  content: typeof nodeContent === 'string' ? nodeContent : JSON.stringify(nodeContent),
                  description: typeof nodeContent === 'string' ? nodeContent : JSON.stringify(nodeContent),
                  reference: references,
                  order: index
                };
                
                processNodes.push(agentNode);
              } else if (contentItem.messageType === 'web') {
                // Web类型节点
                const webData = contentItem.content || {};
                const nodeName = webData.nodeName || `web-${index}`;
                const webContent = webData.content || '';
                
                // 创建Web类型节点
                const webNode: ProcessNode = {
                  type: 'web',
                  id: nodeName,
                  name: nodeName,
                  status: 'completed',
                  content: typeof webContent === 'string' ? webContent : JSON.stringify(webContent),
                  order: index
                };
                
                processNodes.push(webNode);
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
            
            // 检查是否有web内容
            let webContentForMessage: WebContent | undefined = undefined;
            const webContentItem = msg.content.find((item: any) => item.messageType === 'web');
            if (webContentItem) {
              // 适配用户提供的数据格式
              if (typeof webContentItem.content === 'object' && webContentItem.content !== null) {
                // 情况1: content是对象，可能直接包含HTML内容
                webContentForMessage = {
                  messageType: 'web' as const,
                  content: webContentItem.content.content || JSON.stringify(webContentItem.content),
                  nodeName: webContentItem.content.nodeName || webContentItem.nodeName || 'web-content'
                };
              } else if (typeof webContentItem.content === 'string') {
                // 情况2: content是字符串
                webContentForMessage = {
                  messageType: 'web' as const,
                  content: webContentItem.content,
                  nodeName: webContentItem.nodeName || 'web-content'
                };
              }
              
              // 添加调试日志
              console.log('Web内容处理:', { original: webContentItem, processed: webContentForMessage });
            }
            
            formattedMessages.push({
              id: msg.id || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
              content: typeof messageContent === 'string' ? messageContent : JSON.stringify(messageContent),
              role: msg.role === 'USER' ? 'user' : 'assistant',
              createdAt: msg.createdAt ? new Date(msg.createdAt) : new Date(),
              citations: firstTextContent?.content?.reference?.reference || [],
              ragReference: ragReference,
              processFlow: processFlow,
              isAgent: msg.content.some((item: any) => item.messageType === 'agent'),
              webContent: webContentForMessage
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
        
        // 设置最新的threadId
        if (latestThreadId) {
          setThreadId(latestThreadId);
        }
        if (loadMore) {
          // 向上滚动加载时，将新消息添加到现有消息的前面，但避免重复
          // 保持API返回的原始顺序
          setMessages(prev => {
            // 创建一个Set存储已存在的消息ID，用于快速去重
            const existingIds = new Set(prev.map(msg => msg.id));
            
            // 过滤掉已存在的消息
            const newUniqueMessages = formattedMessages.filter(msg => !existingIds.has(msg.id));
            
            // 直接合并，保持原始顺序
            return [...newUniqueMessages, ...prev];
          });
          // 保存当前滚动位置
          const currentScrollHeight = messagesContainerRef.current?.scrollHeight || 0;
          // 等待DOM更新后恢复滚动位置
          setTimeout(() => {
            if (messagesContainerRef.current) {
              const newScrollHeight = messagesContainerRef.current.scrollHeight;
              messagesContainerRef.current.scrollTop = newScrollHeight - currentScrollHeight;
            }
          }, 0);
        } else {
          // 初始加载时替换现有消息，保持API返回的原始顺序
          setMessages(formattedMessages);
          // 聊天记录加载完成后滚动到底部
          setTimeout(() => {
            messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
          }, 100);
        }
        
        // 设置聊天标题为第一条用户消息
        // 使用已加载的消息找到第一条用户消息
        const allMessagesForTitle = loadMore 
          ? [...formattedMessages, ...messages] 
          : formattedMessages;
        const firstUserMessage = allMessagesForTitle.find(msg => msg.role === 'user');
        
        if (firstUserMessage) {
          setChatTitle(firstUserMessage.content.substring(0, 20) + (firstUserMessage.content.length > 20 ? '...' : ''));
        }
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
  }, [id, before, hasMore, loadingChat, toast]);

  // 组件加载时获取聊天记录
  useEffect(() => {
    // 初始化只执行一次
    const initChat = async () => {
      try {
        setLoadingChat(true);
        const limit = 50;
        const currentBefore = "";
        
        console.log('调用API获取聊天记录...');
        const data = await getChatHistory({ id, limit, before: currentBefore });
        console.log('API返回数据:', JSON.stringify(data));

        // 更新before参数用于下一次加载，使用before作为下一次查询的cursor
        let hasMoreData = true;
        if (data) {
          if (data.before) {
            setBefore(data.before);
          } else {
            hasMoreData = false;
          }
        } else {
          hasMoreData = false;
        }
        setHasMore(hasMoreData);
        
        // 如果没有数据，直接返回
        if (!data) {
          setLoadingChat(false);
          return;
        }
        
        // 后续处理逻辑与fetchChatHistory相同
        let messagesToProcess = [];
        if (Array.isArray(data.message)) {
          messagesToProcess = data.message;
        } else if (data.data && Array.isArray(data.data.message)) {
          messagesToProcess = data.data.message;
        } else if (data.code === 200 && data.data && Array.isArray(data.data.message)) {
          messagesToProcess = data.data.message;
        } else {
          console.error('API返回数据格式错误，无法解析消息:', data);
          messagesToProcess = [];
        }
        
        const formattedMessages: Message[] = [];
        let latestThreadId = '';
        let textContentBuffer = '';
        let lastMessageType: string = '';
        let hasWebContent = false;
        let webContent = '';
        let newAssistantMessageId = '';
        
        // 处理消息格式化...（与fetchChatHistory相同的逻辑）
        messagesToProcess.forEach((msg: any) => {
          if (msg.content && Array.isArray(msg.content)) {
            let processNodes: ProcessNode[] = [];
            const processEdges: Array<{ from: string; to: string }> = [];
            
            msg.content.forEach((contentItem: any, index: number) => {
              if (contentItem.threadId !== undefined) {
                latestThreadId = contentItem.threadId || '';
              }
              
              // 处理web类型消息
              if (contentItem.messageType === 'web') {
                // Web类型消息处理
                // 清空文本缓冲区
                textContentBuffer = '';

                const webData = contentItem.content || {};
                const webContentValue = webData.web内容 || '';
                const nodeName = webData.nodeName || 'web-output';
                
                // 安全过滤web内容
                const sanitizedWebContent = sanitizeWebContent(webContentValue);
                
                // 创建新的web节点
                // 更新之前节点状态为已完成
                if (processNodes.length > 0) {
                  processNodes = processNodes.map(node => ({
                    ...node,
                    status: 'completed'
                  }));
                }

                // 创建新节点
                const webNode: ProcessNode = {
                  type: 'web',
                  id: nodeName,
                  name: nodeName,
                  displayTitle: '网页内容',
                  status: 'active',
                  content: sanitizedWebContent,
                  order: processNodes.length
                };

                // 添加新节点
                processNodes.push(webNode);

                // 添加边（如果有多个节点）
                if (processNodes.length > 1) {
                  const lastNodeIndex = processNodes.length - 1;
                  processEdges.push({
                    from: processNodes[lastNodeIndex - 1].id,
                    to: processNodes[lastNodeIndex].id
                  });
                }

                // 更新最后消息类型
                lastMessageType = 'web';
                hasWebContent = true;
                webContent = sanitizedWebContent;
                
                // 更新助手消息，包含webContent字段
                const assistantMessage: Message = {
                  id: newAssistantMessageId,
                  content: '', // 留空，避免重复显示
                  role: 'assistant' as const,
                  createdAt: new Date(),
                  processFlow: {
                    nodes: processNodes,
                    edges: processEdges
                  },
                  webContent: {
                    messageType: 'web',
                    content: sanitizedWebContent,
                    nodeName: nodeName
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
              
              if (contentItem.messageType === 'text') {
                const answer = contentItem.content?.answer || '';
                const messageContent = typeof answer === 'string' ? answer : JSON.stringify(answer);
                const references = contentItem.content?.reference?.reference || [];
                
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
                const agentData = contentItem.content || {};
                const nodeName = agentData.nodeName || `agent-${index}`;
                const displayTitle = agentData.displayTitle || nodeName;
                const nodeContent = agentData.content || '';
                const references = agentData.reference?.reference || [];
                
                const agentNode: ProcessNode = {
                  type: 'agent',
                  id: nodeName,
                  name: nodeName,
                  displayTitle: displayTitle,
                  status: 'completed',
                  content: typeof nodeContent === 'string' ? nodeContent : JSON.stringify(nodeContent),
                  description: typeof nodeContent === 'string' ? nodeContent : JSON.stringify(nodeContent),
                  reference: references,
                  order: index
                };
                
                processNodes.push(agentNode);
              } else if (contentItem.messageType === 'web') {
                const webData = contentItem.content || {};
                const webContent = webData.content || webData.web内容 || ''; // 同时支持content和web内容属性
                const nodeName = webData.nodeName || `web-${index}`;
                
                const webNode: ProcessNode = {
                  type: 'web',
                  id: nodeName,
                  name: nodeName,
                  displayTitle: '网页内容',
                  status: 'completed',
                  content: webContent,
                  order: index
                };
                
                processNodes.push(webNode);
                }
            });
            
            for (let i = 1; i < processNodes.length; i++) {
              processEdges.push({
                from: processNodes[i-1].id,
                to: processNodes[i].id
              });
            }
            
            const processFlow: ProcessFlow = {
              nodes: processNodes,
              edges: processEdges
            };
            
            const firstTextContent = msg.content.find((item: any) => item.messageType === 'text');
            const messageContent = firstTextContent?.content?.answer || '';
            const ragReference = firstTextContent?.content?.reference;
            
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
        
        if (latestThreadId) {
          setThreadId(latestThreadId);
        }
        
        // 保持API返回的原始顺序
        setMessages(formattedMessages);
        
        // 设置聊天标题为第一条用户消息
        const firstUserMessage = formattedMessages.find(msg => msg.role === 'user');
        if (firstUserMessage) {
          setChatTitle(firstUserMessage.content.substring(0, 20) + (firstUserMessage.content.length > 20 ? '...' : ''));
        }
      } catch (error) {
        console.error('获取聊天记录错误:', error);
        toast({
          title: "错误",
          description: "获取聊天记录失败",
          variant: "destructive"
        });
      } finally {
        setLoadingChat(false);
        
        // 聊天记录加载完成后滚动到底部
        setTimeout(() => {
          messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        }, 100);
      }
    };
    
    // 只在组件挂载时执行一次
    initChat();
  }, [id, toast]);

  // 滚动监听实现加载更多
  useEffect(() => {
    const container = messagesContainerRef.current;
    if (!container) return;

    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = container;
      if (scrollTop === 0 && !loadingChat && hasMore) {
        // 滚动到顶部且有更多记录时加载
        fetchChatHistory(true);
      }
    };

    container.addEventListener('scroll', handleScroll);
    return () => container.removeEventListener('scroll', handleScroll);
  }, [loadingChat, hasMore, fetchChatHistory]);

  /**
     * 自动滚动到底部效果
     * 只在聊天初始化完成后自动滚动到底部
     */
    useEffect(() => {
      // 只有在初始加载完成且不是加载更多历史记录时才滚动到底部
      if (messages.length > 0 && !loadingChat) {
        setTimeout(() => {
          messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        }, 100);
      }
    }, []); // 空依赖数组，只在组件挂载后执行一次

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

      // 保存当前标签状态，用于请求参数
      const currentPptTag = showPptTag;
      const currentDeepResearchTag = showDeepResearchTag;
      const inputText = input.trim();

      // 准备请求参数
      const requestData = {
        query: inputText,
        conversationId: id,
        threadId: threadId,
        agentId: currentPptTag ? "PPTGenerate" : currentDeepResearchTag ? "DeepResearch" : ""
      };

      // 清空输入框
      // 根据需求，PPT标签和深度检索标签现在都只有在用户点击移除按钮时才移除
      setInput('');
      
      // 确保深度检索标签不会被自动移除
      // 记录当前状态用于调试
      console.log('发送消息前深度检索标签状态:', showDeepResearchTag);

      console.log("发送请求:", requestData);

      // 调用新的API端点
      const response = await fetch('http://localhost:8086/chat/stream/msg', {
        method: 'POST',
        headers: {
          'accept': 'text/event-stream',
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${getToken()}`
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
      let processNodes: ProcessNode[] = []; // 用于存储所有流程节点（包括agent、llm和web类型）
      let processEdges: Array<{ from: string; to: string }> = [];
      let lastMessageType: 'text' | 'agent' | 'web' | null = null; // 上一条消息类型
      let lastNodeName: string | null = null; // 上一个节点名称
      let hasWebContent = false;
      let webContent = '';

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
            
            // 提取threadId
            setThreadId(data.threadId || '');

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
              const assistantMessage: Message = {
                id: newAssistantMessageId,
                content: '', // 留空，避免重复显示
                role: 'assistant' as const,
                createdAt: new Date(),
                citations: references,
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
                  displayTitle: agentContent.displayTitle || nodeName,
                  status: 'active',
                  description: nodeDescription,
                  reference: agentContent.reference?.reference || [],
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
      processNodes = processNodes.map(node => ({
        ...node,
        status: 'completed'
      }));

      // 更新最终状态到UI
      const finalAssistantMessage: Message = {
        id: newAssistantMessageId,
        content: '',
        role: 'assistant' as const,
        createdAt: new Date(),
        ragReference: undefined,
        processFlow: {
          nodes: processNodes,
          edges: processEdges
        }
      };
      
      // 如果有web内容，添加到消息中
      if (hasWebContent && webContent) {
        finalAssistantMessage.webContent = {
          messageType: 'web',
          content: webContent
        };
      }

      setMessages(prev => {
        const existingIndex = prev.findIndex(msg => msg.id === newAssistantMessageId);
        if (existingIndex >= 0) {
          const updatedMessages = [...prev];
          updatedMessages[existingIndex] = finalAssistantMessage;
          return updatedMessages;
        }
        return prev;
      });

    } catch (error) {
      setIsLoading(false);
      console.error("发送消息失败:", error);
      toast({
        variant: "destructive",
        title: "错误",
        description: `发送消息失败: ${error instanceof Error ? error.message : '未知错误'}`
      });
    } finally {
      setIsLoading(false);
      // 确保释放reader资源
      // 这个在循环里已经处理了
      
      // 再次确认深度检索标签状态
      console.log('消息处理完成后深度检索标签状态:', showDeepResearchTag);
      
      // 确保深度检索标签保持其状态（只有用户点击移除按钮时才移除）
      // 这里不应该有任何重置showDeepResearchTag的代码
    }
  };

  // 返回聊天列表
  const handleBack = () => {
    router.push('/dashboard/chat');
  };

  return (
    <DashboardLayout>
      <div className="min-h-[calc(100vh-4rem)] flex flex-col space-y-6">
        {/* 页面头部 */}
        <div className="bg-card rounded-lg shadow-sm p-4 sm:p-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <Button variant="ghost" size="icon" onClick={handleBack}>
                <ArrowLeft className="h-5 w-5" />
              </Button>
              <div>
                <h2 className="text-2xl font-bold">{chatTitle}</h2>
                <p className="text-sm text-muted-foreground">
                  对话ID: {id}
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* 聊天消息区域 */}
        <div ref={messagesContainerRef} className="flex-1 overflow-y-auto">
          {/* 加载更多历史记录指示器 */}
          {loadingChat && messages.length > 0 && (
            <div className="flex justify-center py-4 animate-fadeIn">
              <div className="flex items-center space-x-2">
                <Loader2 className="h-4 w-4 animate-spin text-primary" />
                <span className="text-sm text-muted-foreground">正在加载更多历史记录...</span>
              </div>
            </div>
          )}
          
          {messages.map((message) => (
            <div key={message.id} className="flex items-start mb-4">
              {message.role === 'user' ? (
                /* 用户消息 - 右对齐 */
                <div className="flex-1 flex justify-end">
                  <div className="max-w-[85%]">
                    <div className="bg-primary rounded-lg shadow-sm p-4 text-right text-primary-foreground hover:bg-primary/95 transition-all duration-200 word-break:break-word overflow-wrap:anywhere">
                      <p>{message.content}</p>
                    </div>
                    <div className="text-xs text-muted-foreground mt-1 text-right">
                      {new Date(message.createdAt).toLocaleTimeString()}
                    </div>
                  </div>
                  <div className="ml-3 flex-shrink-0 h-10 w-10 rounded-full bg-primary/10 p-2 text-primary flex items-center justify-center shadow-sm transition-all duration-200 hover:scale-105">
                    <User className="h-5 w-5" />
                  </div>
                </div>
              ) : (
                /* AI助手消息 - 左对齐 */
                <div className="flex-1">
                  <div className="flex items-start">
                    <div className="h-10 w-10 flex-shrink-0 rounded-full bg-secondary/10 p-2 text-secondary flex items-center justify-center shadow-sm transition-all duration-200 hover:scale-105">
                      <Bot className="h-5 w-5" />
                    </div>
                    <div className="ml-3 max-w-[85%] w-full">
                      <div className="bg-card rounded-lg shadow-sm p-4 hover:bg-card/95 transition-all duration-200 word-break:break-word overflow-wrap:anywhere">
                        {message.webContent && message.webContent.messageType === 'web' ? (
                          <>
                            {message.content && (
                              <div className="mb-4">
                                <Answer content={message.content} citations={message.citations} ragReference={message.ragReference} />
                              </div>
                            )}
                            {renderWebContent(message.webContent.content)}
                          </>
                        ) : message.processFlow ? (
                          <AgentAnswer 
                            processFlow={message.processFlow as any} 
                            content={message.content} 
                            citations={message.citations} 
                            ragReference={message.ragReference} 
                          />
                        ) : (
                          <Answer content={message.content} citations={message.citations} ragReference={message.ragReference} />
                        )}
                      </div>
                      <div className="text-xs text-muted-foreground mt-1">
                        {new Date(message.createdAt).toLocaleTimeString()}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
          {/* 正在输入指示器 */}
          {isLoading && (
            <div className="flex justify-start gap-3 py-2 animate-fadeIn">
              <div className="h-10 w-10 flex-shrink-0 rounded-full bg-secondary/10 p-2 text-secondary flex items-center justify-center shadow-sm">
                <Bot className="h-5 w-5" />
              </div>
              <div className="max-w-[85%] bg-muted/30 rounded-lg px-4 py-3 text-accent-foreground">
                <div className="flex items-center space-x-1.5">
                  <div className="w-2 h-2 rounded-full bg-primary animate-bounce" />
                  <div className="w-2 h-2 rounded-full bg-primary animate-bounce [animation-delay:0.2s]" />
                  <div className="w-2 h-2 rounded-full bg-primary animate-bounce [animation-delay:0.4s]" />
                  <span className="text-xs text-muted-foreground ml-2">AI助手正在处理...</span>
                </div>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* 输入区域 */}
        <div className="border-t border-input p-4 bg-background shadow-[0_-2px_10px_rgba(0,0,0,0.05)]">
          <form
            onSubmit={handleChatSubmit}
            className="flex items-center space-x-3 w-full"
          >
            <div className="relative flex-1">
              <input
                ref={inputRef}
                type="text"
                placeholder={showPptTag || showDeepResearchTag ? "" : "输入您的问题..."}
                value={input}
                onChange={handleInputChange}
                disabled={isLoading || loadingChat}
                className={`w-full min-w-0 h-12 rounded-md border border-input bg-background px-4 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 transition-all duration-200 hover:border-primary/50 ${showPptTag || showDeepResearchTag ? 'pl-32' : 'pl-4'}`}
              />
              {showPptTag && (
                <button
                  type="button"
                  onClick={() => setShowPptTag(!showPptTag)}
                  className="absolute left-1 top-1/2 transform -translate-y-1/2 h-8 px-3 rounded-md bg-primary text-primary-foreground text-xs font-medium flex items-center hover:bg-primary/90 transition-colors"
                >
                  PPT生成工具
                  <span className="ml-1 flex items-center justify-center w-3 h-3 rounded-full bg-white/20 hover:bg-white/30 transition-colors">×</span>
                </button>
              )}
              {showDeepResearchTag && (
                <div className="absolute left-1 top-1/2 transform -translate-y-1/2 h-8 px-3 rounded-md bg-primary text-primary-foreground text-xs font-medium flex items-center hover:bg-primary/90 transition-colors">
                  深度检索
                  <button
                    type="button"
                    onClick={() => setShowDeepResearchTag(false)}
                    className="ml-1 flex items-center justify-center w-3 h-3 rounded-full bg-white/20 hover:bg-white/30 transition-colors"
                  >
                    ×
                  </button>
                </div>
              )}
            </div>
            <Button
              type="submit"
              disabled={isLoading || loadingChat || !input.trim()}
              className="h-12 px-6 rounded-md transition-all duration-200 group bg-primary hover:bg-primary/90 hover:shadow-md disabled:opacity-50 disabled:hover:shadow-none"
            >
              {isLoading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Send className="h-4 w-4 group-hover:translate-x-0.5 transition-transform duration-200" />
              )}
            </Button>
          </form>
          <div className="flex items-center justify-between mt-2">
            {input.trim() && (
              <p className="text-xs text-muted-foreground ml-1 animate-fadeIn">按 Enter 发送消息</p>
            )}
            <div className="flex space-x-2">
              <Button
                type="button"
                onClick={() => setShowPptTag(true)}
                disabled={showPptTag || showDeepResearchTag || isLoading || loadingChat}
                className="h-7 px-3 text-xs bg-secondary hover:bg-secondary/90 text-black"
              >
                PPT生成工具
              </Button>
              <Button
                type="button"
                onClick={() => setShowDeepResearchTag(true)}
                disabled={showPptTag || showDeepResearchTag || isLoading || loadingChat}
                className="h-7 px-3 text-xs bg-secondary hover:bg-secondary/90 text-black"
              >
                深度检索
              </Button>
            </div>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}
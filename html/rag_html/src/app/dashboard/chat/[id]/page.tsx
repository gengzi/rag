"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { Send, User, Bot, ArrowLeft, MessageSquare, ThumbsUp, ThumbsDown, Loader2 } from "lucide-react";
import DashboardLayout from "@/components/layout/dashboard-layout";
import { api, ApiError } from '@/lib/api';
import { useToast } from "@/components/ui/use-toast";
import Answer from "@/components/chat/answer";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";

interface Citation {
  id: number;
  text: string;
  metadata: Record<string, any>;
}

interface Message {
  id: string;
  content: string;
  role: 'user' | 'assistant';
  createdAt: Date;
  citations?: Citation[];
  ragReference?: any;
}

export default function ChatPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();
  const [loadingChat, setLoadingChat] = useState(true);
  const [chatTitle, setChatTitle] = useState("新对话");
  const [currentBotMessage, setCurrentBotMessage] = useState<{ content: string; citations?: Citation[] } | null>(null);
  
  // 自定义消息状态管理，替代 useChat
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // 获取token用于Authorization头
  const getToken = () => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('token') || '';
    }
    return '';
  };

  // 自定义提交函数，只发送当前输入的消息
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
      
      // 发送请求到API，但只发送当前消息，不包含整个历史
      const response = await fetch('http://127.0.0.1:8883/chat/rag', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'accept': 'text/event-stream',
          'Authorization': `Bearer ${getToken()}`
        },
        body: JSON.stringify({
          question: input,  // 使用question参数名而不是message
          conversationId: params.id
        })
      });
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      // 清空输入框
      setInput('');
      
      // 滚动到底部
      scrollToBottom();
      
      // 处理流式响应
      const reader = response.body?.getReader();
      if (!reader) return;
      
      const decoder = new TextDecoder();
      let accumulatedResponse = '';
      let assistantMessageId = `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
      let assistantMessageCreated = false;
      let fullAnswer = '';
      
      try {
        while (true) {
      
          const { done, value } = await reader.read();

          console.log("消息响应reader：",value);
          
          // 处理流结束
          if (done || accumulatedResponse.includes('[DONE]')) {
            setIsLoading(false);
            break;
          }
          
          if (value) {
            const chunk = decoder.decode(value, { stream: true });
            accumulatedResponse += chunk;

            console.log("chunk结果拼接",accumulatedResponse)
            
            // 分割SSE事件
            const events = accumulatedResponse.split('\n\n');
            
            // 处理每个完整的事件
            for (let i = 0; i < events.length - 1; i++) { // 只处理前n-1个完整事件
              const event = events[i].trim();
              if (!event) continue;
              
              // 查找data:前缀并提取JSON部分
              const dataPrefixIndex = event.indexOf('data:');
              if (dataPrefixIndex !== -1) {
                const jsonData = event.substring(dataPrefixIndex + 5);
                console.log("解析后json数据",jsonData)
                try {
                  const parsedData = JSON.parse(jsonData);
                  
                  // 检查是否是结束标记
                  if (parsedData.answer === '[DONE]') {
                    // 对于[DONE]标记，只设置加载状态为false，但不立即返回
                    // 继续执行下面的代码，确保引用信息被正确处理
                    setIsLoading(false);
                    // 不返回，继续处理引用信息
                  }
                  
                  // 获取答案内容
                  if (parsedData.answer && parsedData.answer !== '[DONE]') {
                    // 检查当前答案块是否已存在于fullAnswer的结尾（简单去重）
                    if (!fullAnswer.endsWith(parsedData.answer)) {
                      fullAnswer += parsedData.answer;
                    }
                  }
                  
                  // 无论是否是[DONE]标记，都提取引用信息
                  // 添加调试日志，查看接收到的answer内容
                  console.log('Received answer chunk:', parsedData.answer);
                  console.log('Current full answer:', fullAnswer);
                     
                  // 提取引用信息
                  const citations: Citation[] = [];
                  const ragReference = parsedData.reference?.reference || [];
                  
                  // 添加调试日志，检查原始引用数据
                  console.log('Original ragReference data:', ragReference);
                  console.log('References with imageUrl:', ragReference.filter((ref: any) => ref.imageUrl));
                  
                  // 如果有引用信息，转换为所需格式
                  if (Array.isArray(ragReference) && ragReference.length > 0) {
                    ragReference.forEach((ref, index) => {
                      citations.push({
                        id: index + 1,
                        text: ref.text || '',
                        metadata: {
                          title: ref.documentName || `引用文档 ${index + 1}`,
                          source: ref.contentType || '文档',
                          page: ref.pageRange,
                          url: ref.documentUrl,
                          documentId: ref.documentId,
                          // 直接从ref对象中获取imageUrl字段
                          imageUrl: ref.imageUrl,
                          // 保留原始metadata中的其他字段
                          ...(ref.metadata || {})
                        }
                      });
                    });
                  }
                  
                  // 检查是否是流的最后一部分数据
                  const isFinalChunk = done || accumulatedResponse.includes('[DONE]') || parsedData.answer === '[DONE]';
                    
                    // 创建助手消息对象
                    const assistantMessage = {
                      id: assistantMessageId,
                      content: fullAnswer || '...', // 确保内容不为空
                      role: 'assistant' as const,
                      createdAt: new Date(),
                      // 只在最后一次处理流数据时包含引用信息
                      citations: isFinalChunk ? citations : undefined,
                      ragReference: isFinalChunk ? ragReference : undefined
                    };
                    
                    // 添加调试日志，查看助手消息内容
                    console.log('Creating/updating assistant message with content:', fullAnswer);
                    console.log('Is final chunk (show citations):', isFinalChunk);
                    if (!assistantMessageCreated) {
                      // 首次创建助手消息
                      setMessages(prev => {
                        console.log('Adding new assistant message to messages array');
                        const lastMessage = prev[prev.length - 1];
                        if (lastMessage && lastMessage.role === 'user' && lastMessage.content === input) {
                          const newMessages = [...prev, assistantMessage];
                          console.log('New messages array after adding assistant message:', newMessages);
                          return newMessages;
                        } else {
                          // 如果最后一条不是用户刚发送的消息，则查找并更新
                          const userIndex = prev.findIndex(msg => msg.role === 'user' && msg.content === input);
                          if (userIndex !== -1) {
                            const newMessages = [...prev];
                            // 检查用户消息后是否已有助手消息，如果有则更新，否则添加
                            if (newMessages.length > userIndex + 1 && newMessages[userIndex + 1].role === 'assistant') {
                              newMessages[userIndex + 1] = assistantMessage;
                            } else {
                              newMessages.splice(userIndex + 1, 0, assistantMessage);
                            }
                            console.log('New messages array after inserting assistant message:', newMessages);
                            return newMessages;
                          }
                          const newMessages = [...prev, assistantMessage];
                          console.log('New messages array after fallback adding:', newMessages);
                          return newMessages;
                        }
                      });
                        
                      assistantMessageCreated = true;
                    } else {
                      // 更新现有助手消息的内容
                      setMessages(prev => {
                        const updatedMessages = prev.map(msg => 
                          msg.id === assistantMessageId 
                            ? { 
                                ...msg, 
                                content: fullAnswer, 
                                citations: isFinalChunk ? citations : undefined, 
                                ragReference: isFinalChunk ? ragReference : undefined 
                              } 
                            : msg
                        );
                        console.log('Messages array after updating assistant message:', updatedMessages);
                        return updatedMessages;
                      });
                    }
                } catch (error) {
                  console.log('SSE JSON parsing error:', error);
                  // 继续处理，不中断整个流程
                }
              }
            }
            
            // 清理已处理的事件，只保留最后一个可能不完整的事件
            if (events.length > 0) {
              accumulatedResponse = events[events.length - 1];
            }
          }
        }
      } catch (error) {
        console.error('SSE reading error:', error);
        // 即使有错误，也要确保isLoading被设置为false
        setIsLoading(false);
      } finally {
        // 确保释放reader资源
        if (reader) {
          try {
            await reader.cancel();
          } catch (e) {
            // 忽略取消时的错误
          }
        }
        setIsLoading(false);
      }
      
    } catch (error) {
      setIsLoading(false);
      console.error("发送消息失败:", error);
      toast({
        variant: "destructive",
        title: "错误",
        description: `发送消息失败: ${error instanceof Error ? error.message : '未知错误'}`
      });
    }
  };

  // 使用useEffect监听messages变化来处理接收到的消息
  useEffect(() => {
    if (messages.length > 0) {
      const lastMessage = messages[messages.length - 1];
      if (lastMessage.role === 'assistant') {
        setCurrentBotMessage({
          content: lastMessage.content,
          citations: lastMessage.citations
        });
      }
    }
  }, [messages, setCurrentBotMessage]);

  // 自动滚动到底部
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  // 组件挂载时获取聊天历史
  useEffect(() => {
    const fetchChatHistory = async () => {
      try {
        setLoadingChat(true);
        
        // 使用公共API配置获取聊天记录
        const responseData = await api.get('/chat/rag/msg/list', {
          params: {
            conversationId: params.id
          }
        });
        
        // 设置聊天标题
        setChatTitle(responseData?.name || `对话 #${params.id.substring(0, 8)}`);
        
        // 转换聊天历史为自定义Message类型格式
        if (responseData?.message && responseData.message.length > 0) {
          console.log('Processing historical messages:', responseData.message);
          const formattedMessages = responseData.message.map((msg: any) => {
            // 处理citations，确保imageUrl字段被正确包含
            let processedCitations = msg.citations;
            if (msg.citations && Array.isArray(msg.citations)) {
              processedCitations = msg.citations.map((citation: any) => ({
                ...citation,
                metadata: {
                  ...(citation.metadata || {}),
                  // 确保imageUrl字段存在
                  imageUrl: citation.metadata?.imageUrl || citation.imageUrl
                }
              }));
              console.log('Processed citations for historical message:', processedCitations);
            }
            
            return {
              id: msg.id || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
              content: msg.content,
              role: msg.role === 'USER' ? 'user' : 'assistant',
              createdAt: new Date(msg.createdAt),
              citations: processedCitations,
              ragReference: msg.ragReference,
            };
          });
          setMessages(formattedMessages);
        } else {
          // 如果没有历史消息，添加欢迎消息
          setMessages([
            {
              id: 'welcome-msg',
              role: 'assistant' as const,
              content: '您好！我是知识库小助手。请问有什么可以帮助您的？',
              createdAt: new Date()
            }
          ]);
        }
        
      } catch (error) {
        console.error("获取聊天记录失败:", error);
        toast({ 
          variant: "destructive", 
          title: "错误", 
          description: "获取聊天记录失败，请稍后重试"
        });
        
        // 错误时也显示欢迎消息
        setMessages([
          {
            id: 'welcome-msg',
            role: 'assistant' as const,
            content: '您好！我是知识库小助手。请问有什么可以帮助您的？',
            createdAt: new Date()
          }
        ]);
      } finally {
        setLoadingChat(false);
        scrollToBottom();
      }
    };

    fetchChatHistory();
  }, [params.id, toast]);

  // 处理输入变化
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInput(e.target.value);
  };

  // 处理消息反馈
  const handleFeedback = (messageId: string, isHelpful: boolean) => {
    // 实际项目中，这里应该发送反馈到服务器
    console.log(`Message ${messageId} feedback: ${isHelpful ? 'helpful' : 'not helpful'}`);
    
    toast({ 
      title: isHelpful ? "感谢反馈" : "我们会改进", 
      description: isHelpful ? "很高兴能帮到您！" : "感谢您的宝贵意见，我们会努力改进。" 
    });
  };

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, currentBotMessage, isLoading]);

  // 返回上一页
  const handleBack = () => {
    router.push("/dashboard/chat");
  };

  return (
    <DashboardLayout>
      <div className="flex flex-col h-[calc(100vh-4rem)]">
        {/* 聊天标题栏 */}
        <div className="border-b border-input p-4 flex items-center justify-between bg-background">
          <div className="flex items-center gap-3">
            <Button 
              variant="ghost" 
              size="icon" 
              onClick={handleBack} 
              className="h-8 w-8 rounded-full"
            >
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <h1 className="text-xl font-semibold">{chatTitle}</h1>
          </div>
          <div className="text-sm text-muted-foreground">
            对话 ID: {params.id}
          </div>
        </div>

        {/* 聊天内容区域 */}
        <div className="flex-1 p-4 space-y-6 bg-background">
          {loadingChat ? (
            <div className="space-y-4 animate-pulse">
              {Array.from({ length: 3 }).map((_, index) => (
                <div key={index} className="flex items-start gap-3">
                  <div className="h-10 w-10 rounded-full bg-secondary/10 flex-shrink-0" />
                  <div className="space-y-2">
                    <div className="h-4 w-32 bg-secondary/20 rounded" />
                    <div className="space-y-2">
                      <div className="h-4 w-full bg-secondary/20 rounded" />
                      <div className="h-4 w-3/4 bg-secondary/20 rounded" />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-center space-y-4">
              <div className="h-24 w-24 rounded-full bg-secondary/10 flex items-center justify-center mb-4">
                <MessageSquare className="h-10 w-10 text-secondary" />
              </div>
              <h3 className="text-xl font-semibold">开始对话</h3>
              <p className="text-muted-foreground max-w-md">
                请在下方输入您的问题，我们的AI助手将为您提供帮助，并引用相关文档信息。
              </p>
            </div>
          ) : (
            <div className="space-y-6">
              {messages.map((message) => (
                <div key={message.id} className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'} gap-3 py-2`}>
                  {message.role === 'assistant' && (
                    <div className="h-10 w-10 flex-shrink-0 rounded-full bg-secondary/10 p-2 text-secondary flex items-center justify-center shadow-sm transition-all duration-200 hover:scale-105">
                      <Bot className="h-5 w-5" />
                    </div>
                  )}
                  <div className={`max-w-[80%] ${message.role === 'user' ? 'order-1' : 'order-2'}`}>
                    <Card className={`overflow-hidden transition-all duration-200 hover:shadow-md ${message.role === 'user' ? 'bg-primary text-primary-foreground' : 'bg-card hover:bg-card/95'}`}>
                      <CardHeader className="p-4 pb-0">
                        {message.role === 'user' && (
                          <div className="flex justify-between items-center">
                            <CardTitle className="text-sm font-medium">您</CardTitle>
                          </div>
                        )}
                        {message.role === 'assistant' && (
                          <div className="flex justify-between items-center">
                            <CardTitle className="text-sm font-medium">AI助手</CardTitle>
                          </div>
                        )}
                      </CardHeader>
                      <CardContent className="p-4">
                        {message.role === 'user' ? (
                          <p className="text-sm">{message.content}</p>
                        ) : (
                          <Answer 
                            content={message.content} 
                            citations={message.citations} 
                            ragReference={message.ragReference} 
                          />
                        )}
                      </CardContent>
                      {message.role === 'assistant' && (
                        <div className="px-4 pb-4 flex items-center gap-2 justify-end">
                          <Button 
                            variant="ghost" 
                            size="icon" 
                            onClick={() => handleFeedback(message.id, true)} 
                            className="h-7 w-7 rounded-full hover:bg-primary/10"
                          >
                            <ThumbsUp className="h-3.5 w-3.5" />
                          </Button>
                          <Button 
                            variant="ghost" 
                            size="icon" 
                            onClick={() => handleFeedback(message.id, false)} 
                            className="h-7 w-7 rounded-full hover:bg-primary/10"
                          >
                            <ThumbsDown className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      )}
                    </Card>
                  </div>
                  {message.role === 'user' && (
                    <div className="h-10 w-10 flex-shrink-0 rounded-full bg-primary/10 p-2 text-primary flex items-center justify-center shadow-sm transition-all duration-200 hover:scale-105 order-3">
                      <User className="h-5 w-5" />
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
                  <div className="max-w-[80%] bg-muted/30 rounded-lg px-4 py-3 text-accent-foreground">
                    <div className="flex items-center space-x-1.5">
                      <div className="w-2 h-2 rounded-full bg-primary animate-bounce" />
                      <div className="w-2 h-2 rounded-full bg-primary animate-bounce [animation-delay:0.2s]" />
                      <div className="w-2 h-2 rounded-full bg-primary animate-bounce [animation-delay:0.4s]" />
                      <span className="text-xs text-muted-foreground ml-2">AI助手正在输入...</span>
                    </div>
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
            <input
              ref={inputRef}
              id="chat-input"
              value={input}
              onChange={handleInputChange}
              placeholder="输入您的问题..."
              className="flex-1 min-w-0 h-12 rounded-md border border-input bg-background px-4 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 transition-all duration-200 hover:border-primary/50"
              disabled={loadingChat}
              autoComplete="off"
            />
            <Button
              type="submit"
              disabled={isLoading || !input.trim() || loadingChat}
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
          {input.trim() && (
            <p className="text-xs text-muted-foreground mt-2 ml-1 animate-fadeIn">按 Enter 发送消息</p>
          )}
        </div>
      </div>
    </DashboardLayout>
  );
}
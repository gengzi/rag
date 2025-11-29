"use client";

import React, { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { useRouter, useParams } from "next/navigation";
import { Send, User, Bot, ArrowLeft, Loader2 } from "lucide-react";
import DashboardLayout from "@/components/layout/dashboard-layout";
import { useToast } from "@/components/ui/use-toast";
import MemoizedMessage from "./MemoizedMessage";
import {
  MessageSkeleton,
  StreamingIndicator,
  HistoryLoadingIndicator
} from "./MessageSkeleton";
import { Button } from "@/components/ui/button";

import { api, cacheControl } from "@/lib/api";
import { getChatHistory } from '@/lib/services/chatService';
import { parseMessagesFromAPI, processStreamData, StreamDataProcessor, ProcessNode } from '@/utils/messageFormatter';
import { useAccessibility } from '@/hooks/useAccessibility';
import { useResponsive } from '@/hooks/useResponsive';
import { useKeyboardNavigation, CHAT_SHORTCUTS } from '@/hooks/useKeyboardNavigation';

// 导入配置常量
import {
  CHAT_UI_CONFIG,
  CHAT_API_CONFIG,
  MESSAGE_TYPES,
  ERROR_MESSAGES
} from '@/constants/chat';

// 接口定义（从原组件移入）
interface Message {
  id: string;
  content: string;
  role: 'user' | 'assistant';
  createdAt: Date;
  citations?: any[];
  ragReference?: any;
  processFlow?: any;
  isAgent?: boolean;
  webContent?: any;
}

/**
 * 优化后的聊天页面组件
 * 展示所有性能和用户体验优化
 */
export default function OptimizedChatPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const { id } = params;

  // 使用优化的Hooks
  const { toast } = useToast();
  const { announceToScreenReader, scrollToNewMessage } = useAccessibility();
  const {
    getMobileStyles,
    getResponsiveClasses,
    handleVirtualKeyboard
  } = useResponsive();
  const { focusChatInput, activateSendButton } = useKeyboardNavigation(
    CHAT_SHORTCUTS,
    { scope: 'chat' }
  );

  // Refs
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // 状态管理
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [loadingChat, setLoadingChat] = useState(true);
  const [before, setBefore] = useState("");
  const [hasMore, setHasMore] = useState(true);
  const [threadId, setThreadId] = useState<string>('');
  const [chatTitle, setChatTitle] = useState("新对话");

  // 获取移动端优化样式
  const mobileStyles = useMemo(() => getMobileStyles(), [getMobileStyles]);

  // 记忆化的获取token函数
  const getToken = useCallback(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('token') || '';
    }
    return '';
  }, []);

  /**
   * 获取聊天记录 - 使用记忆化和错误处理
   */
  const fetchChatHistory = useCallback(async (loadMore = false) => {
    if (loadingChat) return;

    try {
      setLoadingChat(true);

      // 使用优化后的API和缓存
      const data = await getChatHistory({
        id,
        limit: CHAT_UI_CONFIG.MESSAGE_LIMIT,
        before: loadMore ? before : ""
      });

      if (!data) return;

      // 使用提取的消息格式化函数
      const formattedMessages = parseMessagesFromAPI(data);

      // 更新分页状态
      let hasMoreData = true;
      if (data.before) {
        setBefore(data.before);
      } else {
        hasMoreData = false;
      }
      setHasMore(hasMoreData);

      // 提取threadId
      const latestThreadId = formattedMessages
        .flatMap(msg => msg.processFlow?.nodes || [])
        .find(node => node.threadId)?.threadId || '';

      if (latestThreadId) {
        setThreadId(latestThreadId);
      }

      // 更新消息状态
      if (loadMore) {
        setMessages(prev => {
          const existingIds = new Set(prev.map(msg => msg.id));
          const newUniqueMessages = formattedMessages.filter(msg => !existingIds.has(msg.id));
          return [...newUniqueMessages, ...prev];
        });

        // 保持滚动位置
        const currentScrollHeight = messagesContainerRef.current?.scrollHeight || 0;
        setTimeout(() => {
          if (messagesContainerRef.current) {
            const newScrollHeight = messagesContainerRef.current.scrollHeight;
            messagesContainerRef.current.scrollTop = newScrollHeight - currentScrollHeight;
          }
        }, 0);
      } else {
        setMessages(formattedMessages);

        // 自动滚动到底部
        setTimeout(() => {
          messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        }, CHAT_UI_CONFIG.AUTO_SCROLL_DELAY);
      }

      // 设置聊天标题
      const firstUserMessage = loadMore
        ? [...formattedMessages, ...messages].find(msg => msg.role === 'user')
        : formattedMessages.find(msg => msg.role === 'user');

      if (firstUserMessage) {
        setChatTitle(
          firstUserMessage.content.substring(0, 20) +
          (firstUserMessage.content.length > 20 ? '...' : '')
        );
      }

    } catch (error) {
      console.error('获取聊天记录错误:', error);
      toast({
        title: "错误",
        description: ERROR_MESSAGES.CHAT_LOADING_ERROR,
        variant: "destructive"
      });

      // 屏幕阅读器通知
      announceToScreenReader(ERROR_MESSAGES.CHAT_LOADING_ERROR, 'assertive');
    } finally {
      setLoadingChat(false);
    }
  }, [id, before, loadingChat, toast, announceToScreenReader, messages]);

  /**
   * 处理输入变化
   */
  const handleInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setInput(e.target.value);
  }, []);

  /**
   * 处理发送消息 - 使用流式数据处理器
   */
  const handleChatSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();

    if (!input.trim() || isLoading || loadingChat) return;

    try {
      setIsLoading(true);

      // 创建用户消息
      const userMessage: Message = {
        id: `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        content: input,
        role: 'user',
        createdAt: new Date()
      };

      // 更新本地消息
      setMessages(prev => [...prev, userMessage]);

      // 屏幕阅读器通知
      announceToScreenReader(`正在发送: ${input}`);

      const inputText = input.trim();
      const requestData = {
        query: inputText,
        conversationId: id,
        threadId: threadId,
      };

      // 清空输入框并重新聚焦
      setInput('');
      setTimeout(() => focusChatInput(), 100);

      // 发送请求
      const response = await fetch(CHAT_API_CONFIG.ENDPOINTS.STREAM_CHAT, {
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

      // 处理流式响应
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();

      if (!reader) {
        throw new Error('无法获取响应流');
      }

      // 创建助手消息
      const assistantMessageId = `msg-${Date.now()}-assistant-${Math.random().toString(36).substr(2, 9)}`;

      // 流式数据状态
      let streamState: {
        textContentBuffer: string;
        processNodes: ProcessNode[];
        processEdges: Array<{ from: string; to: string }>;
        lastMessageType: 'text' | 'agent' | 'web' | 'excalidraw' | null;
        lastNodeName: string | null;
        webContentBuffer: string;
        excalidrawData: any;
      } = {
        textContentBuffer: '',
        processNodes: [],
        processEdges: [],
        lastMessageType: null,
        lastNodeName: null,
        webContentBuffer: '',
        excalidrawData: null,
      };

      let hasWebContent = false;
      let webContent = '';

      // 处理流式数据
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        const lines = chunk.split('\n');

        for (const line of lines) {
          if (!line.trim()) continue;

          try {
            let jsonStr = line;
            if (jsonStr.startsWith('data:')) {
              jsonStr = jsonStr.substring(5).trim();
            }

            if (!jsonStr) continue;

            const data = JSON.parse(jsonStr);

            // 更新threadId
            setThreadId(data.threadId || '');

            // 使用优化的流式数据处理器
            const processor: StreamDataProcessor = {
              messageId: assistantMessageId,
              messageType: data.messageType,
              content: data.content,
              threadId: data.threadId
            };

            const result = processStreamData(processor, streamState);
            streamState = {
              textContentBuffer: result.updatedTextContentBuffer,
              processNodes: result.updatedProcessNodes,
              processEdges: result.updatedProcessEdges,
              lastMessageType: result.updatedLastMessageType,
              lastNodeName: result.updatedLastNodeName,
              webContentBuffer: result.updatedWebContentBuffer,
              excalidrawData: result.updatedExcalidrawData,
            };

            if (result.hasWebContent) {
              hasWebContent = true;
              webContent = result.webContent;
            }

            // 更新助手消息
            const assistantMessage: Message = {
              id: assistantMessageId,
              content: '',
              role: 'assistant',
              createdAt: new Date(),
              processFlow: {
                nodes: result.updatedProcessNodes,
                edges: result.updatedProcessEdges
              },
              ...(result.hasWebContent && {
                webContent: {
                  messageType: 'web',
                  content: result.webContent
                }
              })
            };

            setMessages(prev => {
              const existingIndex = prev.findIndex(msg => msg.id === assistantMessageId);
              if (existingIndex >= 0) {
                const updatedMessages = [...prev];
                updatedMessages[existingIndex] = assistantMessage;
                return updatedMessages;
              }
              return [...prev, assistantMessage];
            });

          } catch (parseError) {
            console.error("解析流式数据失败:", parseError);
          }
        }
      }

      // 完成流式响应
      const finalAssistantMessage: Message = {
        id: assistantMessageId,
        content: '',
        role: 'assistant',
        createdAt: new Date(),
        processFlow: {
          nodes: streamState.processNodes.map(node => ({
            ...node,
            status: 'completed'
          })),
          edges: streamState.processEdges
        },
        ...(hasWebContent && {
          webContent: {
            messageType: 'web',
            content: webContent
          }
        })
      };

      setMessages(prev => {
        const existingIndex = prev.findIndex(msg => msg.id === assistantMessageId);
        if (existingIndex >= 0) {
          const updatedMessages = [...prev];
          updatedMessages[existingIndex] = finalAssistantMessage;
          return updatedMessages;
        }
        return prev;
      });

      // 滚动到新消息
      setTimeout(() => {
        scrollToNewMessage(assistantMessageId);
      }, 100);

    } catch (error) {
      console.error("发送消息失败:", error);
      toast({
        variant: "destructive",
        title: "错误",
        description: `${ERROR_MESSAGES.MESSAGE_SEND_ERROR}: ${error instanceof Error ? error.message : '未知错误'}`
      });

      announceToScreenReader(ERROR_MESSAGES.MESSAGE_SEND_ERROR, 'assertive');
    } finally {
      setIsLoading(false);
    }
  }, [input, isLoading, loadingChat, id, threadId, getToken, toast, announceToScreenReader, focusChatInput, scrollToNewMessage]);

  /**
   * 返回聊天列表
   */
  const handleBack = useCallback(() => {
    // 清除相关缓存
    cacheControl.clear();
    router.push('/dashboard/chat');
  }, [router]);

  // 滚动监听实现加载更多
  useEffect(() => {
    const container = messagesContainerRef.current;
    if (!container) return;

    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = container;
      if (scrollTop === 0 && !loadingChat && hasMore) {
        fetchChatHistory(true);
      }
    };

    const debouncedHandleScroll = debounce(handleScroll, CHAT_UI_CONFIG.SCROLL_DEBOUNCE);
    container.addEventListener('scroll', debouncedHandleScroll);

    return () => container.removeEventListener('scroll', debouncedHandleScroll);
  }, [loadingChat, hasMore, fetchChatHistory]);

  // 初始化加载
  useEffect(() => {
    fetchChatHistory();
  }, [fetchChatHistory]);

  // 虚拟键盘处理（移动端）
  useEffect(() => {
    if (typeof window === 'undefined') return;

    return handleVirtualKeyboard((keyboardHeight: number) => {
      if (messagesContainerRef.current) {
        const currentHeight = messagesContainerRef.current.offsetHeight;
        messagesContainerRef.current.style.height = `${currentHeight - keyboardHeight}px`;
      }
    });
  }, [handleVirtualKeyboard]);

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
        <div
          ref={messagesContainerRef}
          className="flex-1 overflow-y-auto"
          style={mobileStyles.chatContainer}
          aria-label="聊天消息区域"
          role="log"
          aria-live="polite"
        >
          {/* 加载更多历史记录指示器 */}
          {loadingChat && messages.length > 0 && (
            <HistoryLoadingIndicator />
          )}

          {/* 使用记忆化的消息组件 */}
          {messages.map((message) => (
            <MemoizedMessage
              key={message.id}
              message={message}
              data-message="true"
              data-message-id={message.id}
              aria-label={`${message.role === 'user' ? '用户' : 'AI助手'}消息`}
            />
          ))}

          {/* 正在输入指示器 */}
          {isLoading && (
            <StreamingIndicator />
          )}

          {/* 滚动锚点 */}
          <div ref={messagesEndRef} />
        </div>

        {/* 输入区域 */}
        <div className={getResponsiveClasses(
          "border-t border-input p-4 bg-background shadow-[0_-2px_10px_rgba(0,0,0,0.05)]",
          "p-3", // 移动端
          "p-4", // 平板
          "p-6"  // 桌面
        )}>
          <form
            onSubmit={handleChatSubmit}
            className="flex items-center space-x-3 w-full"
          >
            <div className="relative flex-1">
              <input
                ref={inputRef}
                type="text"
                placeholder={CHAT_UI_CONFIG.INPUT_PLACEHOLDER}
                value={input}
                onChange={handleInputChange}
                disabled={isLoading || loadingChat}
                data-input="chat"
                className={getResponsiveClasses(
                  "w-full min-w-0 rounded-md border border-input bg-background px-4 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 transition-all duration-200 hover:border-primary/50",
                  "h-12 px-3 text-base", // 移动端：更大的字体防止缩放
                  "h-12 px-4 text-sm",   // 平板
                  "h-12 px-4 text-sm"    // 桌面
                )}
                style={mobileStyles.input}
                aria-label="聊天消息输入"
                aria-describedby="chat-hint"
                aria-disabled={isLoading || loadingChat}
                maxLength={1000}
              />
              <div id="chat-hint" className="sr-only">
                输入您的问题，按 Ctrl+Enter 发送
              </div>
            </div>

            <Button
              type="submit"
              data-submit="chat"
              disabled={isLoading || loadingChat || !input.trim()}
              className={getResponsiveClasses(
                "rounded-md transition-all duration-200 group bg-primary hover:bg-primary/90 hover:shadow-md disabled:opacity-50 disabled:hover:shadow-none",
                "h-12 w-12", // 移动端
                "h-12 px-6", // 平板
                "h-12 px-6"  // 桌面
              )}
              style={mobileStyles.button}
              aria-label="发送消息"
            >
              {isLoading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Send className="h-4 w-4 group-hover:translate-x-0.5 transition-transform duration-200" />
              )}
            </Button>
          </form>

          {/* 快捷键提示 */}
          {input.trim() && (
            <p className="text-xs text-muted-foreground ml-1 mt-2 animate-fadeIn">
              按 Ctrl+Enter 发送消息
            </p>
          )}
        </div>
      </div>
    </DashboardLayout>
  );
}

/**
 * 防抖函数
 */
function debounce<T extends (...args: any[]) => void>(
  func: T,
  wait: number
): (...args: Parameters<T>) => void {
  let timeout: NodeJS.Timeout;
  return (...args: Parameters<T>) => {
    clearTimeout(timeout);
    timeout = setTimeout(() => func(...args), wait);
  };
}
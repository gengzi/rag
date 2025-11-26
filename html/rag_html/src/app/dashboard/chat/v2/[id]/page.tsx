"use client";

import { useState, useCallback, useRef } from "react";
import { useRouter, useParams } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import DashboardLayout from "@/components/layout/dashboard-layout";
import { useToast } from "@/components/ui/use-toast";
import { Button } from "@/components/ui/button";
import MessageList from "@/components/chat/MessageList";
import ChatInputFixed from "@/components/chat/ChatInputFixed";
import MessageItemFixed from "@/components/chat/MessageItemFixed";

import { useChatHistory } from "@/hooks/useChatHistory";
import { useChatSubmission } from "@/hooks/useChatSubmission";

import { Message, processStreamData } from '@/utils/messageFormatter';

export default function NewChatPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const { id } = params;
  const { toast } = useToast();
  const inputRef = useRef<HTMLInputElement>(null);

  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [threadId, setThreadId] = useState<string>('');
  const [chatTitle, setChatTitle] = useState("新对话");
  const [showPptTag, setShowPptTag] = useState(false);
  const [showDeepResearchTag, setShowDeepResearchTag] = useState(false);

  // 获取token函数
  const getToken = useCallback(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('token') || '';
    }
    return '';
  }, []);

  // 聊天历史Hook
  const {
    loadingChat,
    hasMore,
    messagesContainerRef,
    messagesEndRef,
    loadMoreHistory,
    before,
  } = useChatHistory({
    conversationId: id,
    onMessagesUpdate: (newMessages, isLoadMore) => {
      console.log('更新消息数组:', {
        isLoadMore,
        newMessagesCount: newMessages.length,
        firstMessageRole: newMessages[0]?.role,
        lastMessageRole: newMessages[newMessages.length - 1]?.role,
        roles: newMessages.map(m => m.role)
      }); // 调试日志

    if (isLoadMore) {
      // 加载更多时，将新消息添加到前面
      setMessages(prev => {
        console.log('加载更多 - 合并前:', {
          existingCount: prev.length,
          newCount: newMessages.length,
          firstPrevRole: prev[0]?.role,
          firstNewRole: newMessages[0]?.role
        }); // 调试日志

        const existingIds = new Set(prev.map(msg => msg.id));
        const newUniqueMessages = newMessages.filter(msg => !existingIds.has(msg.id));
        const merged = [...newUniqueMessages, ...prev];

        console.log('加载更多 - 合并后:', {
          totalCount: merged.length,
          firstRole: merged[0]?.role,
          lastRole: merged[merged.length - 1]?.role,
          roleOrder: merged.map(m => m.role)
        }); // 调试日志

        return merged;
      });
    } else {
      // 初始加载时替换所有消息
      console.log('初始加载 - 替换所有消息:', {
        messagesCount: newMessages.length,
        roleOrder: newMessages.map(m => m.role)
      }); // 调试日志

      setMessages(newMessages);
    }
    },
    onThreadIdUpdate: setThreadId,
    onTitleUpdate: setChatTitle,
  });

  // 聊天提交Hook
  const { sendMessage, isLoading } = useChatSubmission({
    conversationId: id,
    threadId,
    onThreadIdUpdate: setThreadId,
    onMessageUpdate: (updatedMessage) => {
      // 更新现有消息，但只在真正需要时才更新
      setMessages(prev => {
        const index = prev.findIndex(msg => msg.id === updatedMessage.id);
        if (index >= 0) {
          // 检查是否真的需要更新
          const prevMessage = prev[index];
          const prevProcessFlowStr = JSON.stringify(prevMessage.processFlow);
          const newProcessFlowStr = JSON.stringify(updatedMessage.processFlow);
          const prevWebContentStr = JSON.stringify(prevMessage.webContent);
          const newWebContentStr = JSON.stringify(updatedMessage.webContent);

          if (
            prevProcessFlowStr === newProcessFlowStr &&
            prevWebContentStr === newWebContentStr &&
            prevMessage.content === updatedMessage.content
          ) {
            return prev; // 如果没有变化，返回原数组避免重新渲染
          }

          const updatedMessages = [...prev];
          updatedMessages[index] = updatedMessage;
          return updatedMessages;
        }
        return prev;
      });
    },
    onNewMessage: (newMessage) => {
      // 添加新消息
      setMessages(prev => [...prev, newMessage]);

      // 如果是助手消息，滚动到底部
      if (newMessage.role === 'assistant') {
        setTimeout(() => {
          messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        }, 100);
      }
    },
    onRemoveMessage: (messageId) => {
      // 移除指定ID的消息
      setMessages(prev => prev.filter(msg => msg.id !== messageId));
    },
    showNotification: (content) => {
      // 使用toast组件显示优雅的提示信息
      toast({
        title: "提示",
        description: content,
        duration: 3000,
        variant: "default"
      });
    },
    getToken,
  });

  // 事件处理函数
  const handleChatSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();

    if (!input.trim() || isLoading) return;

    try {
      const agentId = showPptTag ? "PPTGenerate" : showDeepResearchTag ? "DeepResearch" : "";
      await sendMessage(input, agentId);

      // 清空输入框
      setInput('');

      // 重新聚焦到输入框
      setTimeout(() => {
        inputRef.current?.focus();
      }, 100);

    } catch (error) {
      console.error("发送消息失败:", error);
      toast({
        variant: "destructive",
        title: "错误",
        description: `发送消息失败: ${error instanceof Error ? error.message : '未知错误'}`
      });
    }
  }, [input, isLoading, sendMessage, showPptTag, showDeepResearchTag, toast]);

  const handleInputChange = useCallback((value: string) => {
    setInput(value);
  }, []);

  const handleBack = useCallback(() => {
    router.push('/dashboard/chat');
  }, [router]);

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
            {/* 调试信息 */}
            <div className="text-xs text-gray-500 space-y-1">
              <div>消息数: {messages.length}</div>
              <div>加载中: {loadingChat ? '是' : '否'}</div>
              <div>有更多: {hasMore ? '是' : '否'}</div>
              <div>NextCursor: {before || 'null'}</div>
              <button
                onClick={() => {
                  console.log('手动触发加载更多');
                  loadMoreHistory();
                }}
                disabled={loadingChat || !hasMore}
                className="text-xs bg-blue-500 text-white px-2 py-1 rounded disabled:opacity-50 mr-2"
              >
                手动加载更多
              </button>
              <button
                onClick={() => {
                  console.log('滚动到顶部');
                  messagesContainerRef.current?.scrollTo({ top: 0, behavior: 'smooth' });
                }}
                className="text-xs bg-green-500 text-white px-2 py-1 rounded"
              >
                滚动到顶部
              </button>
            </div>
          </div>
        </div>

        {/* 聊天消息区域 */}
        <MessageList
          messages={messages}
          isLoading={isLoading}
          loadingChat={loadingChat}
          hasMore={hasMore}
          containerRef={messagesContainerRef}
          messagesEndRef={messagesEndRef}
        />

        {/* 输入区域 */}
        <ChatInputFixed
          ref={inputRef}
          input={input}
          onInputChange={handleInputChange}
          onSubmit={handleChatSubmit}
          isLoading={isLoading}
          loadingChat={loadingChat}
          showPptTag={showPptTag}
          showDeepResearchTag={showDeepResearchTag}
          onPptTagToggle={() => setShowPptTag(!showPptTag)}
          onDeepResearchTagToggle={() => setShowDeepResearchTag(!showDeepResearchTag)}
        />
      </div>
    </DashboardLayout>
  );
}
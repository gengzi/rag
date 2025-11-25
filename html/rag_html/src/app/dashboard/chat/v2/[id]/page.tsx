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

import { Message } from '@/utils/messageFormatter';

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
  } = useChatHistory({
    conversationId: id,
    onMessagesUpdate: (newMessages, isLoadMore) => {
      if (isLoadMore) {
        // 加载更多时，将新消息添加到前面
        setMessages(prev => {
          const existingIds = new Set(prev.map(msg => msg.id));
          const newUniqueMessages = newMessages.filter(msg => !existingIds.has(msg.id));
          return [...newUniqueMessages, ...prev];
        });
      } else {
        // 初始加载时替换所有消息
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
      // 更新现有消息
      setMessages(prev => {
        const updatedMessages = [...prev];
        const index = updatedMessages.findIndex(msg => msg.id === updatedMessage.id);
        if (index >= 0) {
          updatedMessages[index] = updatedMessage;
        }
        return updatedMessages;
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
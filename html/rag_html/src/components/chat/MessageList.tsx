import React, { forwardRef } from 'react';
import { Bot, Loader2 } from 'lucide-react';
import MessageItemFixed from './MessageItemFixed';
import { HistoryLoadingIndicator, StreamingIndicator } from './MessageSkeleton';

import { Message } from '@/utils/messageFormatter';

interface MessageListProps {
  messages: Message[];
  isLoading: boolean;
  loadingChat: boolean;
  hasMore: boolean;
  containerRef: React.RefObject<HTMLDivElement>;
  messagesEndRef: React.RefObject<HTMLDivElement>;
  className?: string;
}

/**
 * 消息列表组件
 * 显示所有聊天消息和加载状态
 */
const MessageList = forwardRef<HTMLDivElement, MessageListProps>(({
  messages,
  isLoading,
  loadingChat,
  hasMore,
  containerRef,
  messagesEndRef,
  className = ""
}, ref) => {
  return (
    <div
      ref={containerRef}
      className={`flex-1 overflow-y-auto ${className}`}
      aria-label="聊天消息区域"
      role="log"
      aria-live="polite"
    >
      {/* 加载更多历史记录指示器 - 显示在顶部 */}
      {loadingChat && messages.length > 0 && (
        <div className="sticky top-0 z-10 bg-white/90 backdrop-blur-sm border-b">
          <HistoryLoadingIndicator />
        </div>
      )}

      {/* 消息列表 */}
      {messages.map((message) => (
        <MessageItemFixed
          key={message.id}
          message={message}
          data-message="true"
          data-message-id={message.id}
          tabIndex={0}
          role="article"
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
  );
});

MessageList.displayName = 'MessageList';

export default MessageList;
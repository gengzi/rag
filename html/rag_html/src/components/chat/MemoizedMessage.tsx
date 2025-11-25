import React, { memo, useMemo } from 'react';
import { User, Bot } from 'lucide-react';
import Answer from './answer';
import AgentAnswer from './agent-answer';
import { Message } from '@/utils/messageFormatter';

interface MemoizedMessageProps {
  message: Message;
  isTyping?: boolean;
}

/**
 * 渲染Web内容
 */
const WebContentRenderer = memo<{ content: string }>(({ content }) => {
  const sanitizedContent = useMemo(() => {
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
      return content;
    }
  }, [content]);

  return (
    <div
      className="mt-4 p-4 border border-input rounded-lg bg-muted/50 overflow-auto"
      style={{ maxHeight: '500px' }}
      dangerouslySetInnerHTML={{ __html: sanitizedContent }}
    />
  );
});

WebContentRenderer.displayName = 'WebContentRenderer';

/**
 * 用户消息组件
 */
const UserMessage = memo<{ content: string; timestamp: string }>(({ content, timestamp }) => {
  const messageContent = useMemo(() => content, [content]);
  const formattedTime = useMemo(() => {
    return new Date(timestamp).toLocaleTimeString();
  }, [timestamp]);

  return (
    <div className="flex-1 flex justify-end">
      <div className="max-w-[85%]">
        <div className="bg-primary rounded-lg shadow-sm p-4 text-right text-primary-foreground hover:bg-primary/95 transition-all duration-200 word-break:break-word overflow-wrap:anywhere">
          <p>{messageContent}</p>
        </div>
        <div className="text-xs text-muted-foreground mt-1 text-right">
          {formattedTime}
        </div>
      </div>
      <div className="ml-3 flex-shrink-0 h-10 w-10 rounded-full bg-primary/10 p-2 text-primary flex items-center justify-center shadow-sm transition-all duration-200 hover:scale-105">
        <User className="h-5 w-5" />
      </div>
    </div>
  );
});

UserMessage.displayName = 'UserMessage';

/**
 * AI助手消息组件
 */
const AssistantMessage = memo<{
  content: string;
  timestamp: string;
  citations?: any[];
  ragReference?: any;
  processFlow?: any;
  webContent?: any;
}>(({ content, timestamp, citations, ragReference, processFlow, webContent }) => {
  const formattedTime = useMemo(() => {
    return new Date(timestamp).toLocaleTimeString();
  }, [timestamp]);

  const messageContent = useMemo(() => {
    if (webContent && webContent.messageType === 'web') {
      return (
        <>
          {content && (
            <div className="mb-4">
              <Answer content={content} citations={citations} ragReference={ragReference} />
            </div>
          )}
          <WebContentRenderer content={webContent.content} />
        </>
      );
    } else if (processFlow) {
      return (
        <AgentAnswer
          processFlow={processFlow}
          content={content}
          citations={citations}
          ragReference={ragReference}
        />
      );
    } else {
      return <Answer content={content} citations={citations} ragReference={ragReference} />;
    }
  }, [content, citations, ragReference, processFlow, webContent]);

  return (
    <div className="flex-1">
      <div className="flex items-start">
        <div className="h-10 w-10 flex-shrink-0 rounded-full bg-secondary/10 p-2 text-secondary flex items-center justify-center shadow-sm transition-all duration-200 hover:scale-105">
          <Bot className="h-5 w-5" />
        </div>
        <div className="ml-3 max-w-[85%] w-full">
          <div className="bg-card rounded-lg shadow-sm p-4 hover:bg-card/95 transition-all duration-200 word-break:break-word overflow-wrap:anywhere">
            {messageContent}
          </div>
          <div className="text-xs text-muted-foreground mt-1">
            {formattedTime}
          </div>
        </div>
      </div>
    </div>
  );
});

AssistantMessage.displayName = 'AssistantMessage';

/**
 * 记忆化的消息组件
 * 使用React.memo避免不必要的重渲染
 * 使用useMemo缓存计算结果
 */
const MemoizedMessage: React.FC<MemoizedMessageProps> = memo(({ message, isTyping = false }) => {
  const messageKey = useMemo(() => `${message.id}-${message.createdAt.getTime()}`, [message.id, message.createdAt]);

  if (message.role === 'user') {
    return (
      <div key={messageKey} className="flex items-start mb-4">
        <UserMessage content={message.content} timestamp={message.createdAt.toISOString()} />
      </div>
    );
  } else {
    return (
      <div key={messageKey} className="flex items-start mb-4">
        <AssistantMessage
          content={message.content}
          timestamp={message.createdAt.toISOString()}
          citations={message.citations}
          ragReference={message.ragReference}
          processFlow={message.processFlow}
          webContent={message.webContent}
        />
      </div>
    );
  }
});

MemoizedMessage.displayName = 'MemoizedMessage';

export default MemoizedMessage;
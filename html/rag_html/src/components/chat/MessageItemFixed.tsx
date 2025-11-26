import React, { memo, useMemo } from 'react';
import { User, Bot } from 'lucide-react';
import Answer from './answer';
import AgentAnswer from './agent-answer';
import { Message } from '@/utils/messageFormatter';

interface MessageItemProps {
  message: Message;
  isTyping?: boolean;
}

/**
 * Web内容渲染器
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
    <div className="mt-4 p-4 border border-gray-200 rounded-lg bg-gray-50 overflow-auto" style={{ maxHeight: '500px' }}>
      {/* 显示为可点击的链接而不是直接渲染HTML */}
      {sanitizedContent.includes('http') ? (
        <div className="space-y-2">
          <p className="text-sm text-gray-600">📄 网页内容已获取:</p>
          <a
            href={sanitizedContent.match(/https?:\/\/[^\s]+/)?.[0]}
            target="_blank"
            rel="noopener noreferrer"
            className="text-blue-600 hover:text-blue-800 underline text-sm break-all"
          >
            🔗 点击查看网页内容
          </a>
        </div>
      ) : (
        <div className="text-sm text-gray-700 whitespace-pre-wrap">{sanitizedContent}</div>
      )}
    </div>
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
        <div className="bg-blue-600 text-white rounded-lg shadow-sm p-4 text-right hover:bg-blue-700 transition-all duration-200">
          <p className="text-white">{messageContent}</p>
        </div>
        <div className="text-xs text-gray-500 mt-1 text-right">
          {formattedTime}
        </div>
      </div>
      <div className="ml-3 flex-shrink-0 h-10 w-10 rounded-full bg-blue-100 p-2 text-blue-600 flex items-center justify-center shadow-sm">
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
    // 如果有processFlow（包含agent、text、web节点），优先使用AgentAnswer显示完整流程
    if (processFlow && processFlow.nodes && processFlow.nodes.length > 0) {
      return (
        <AgentAnswer
          processFlow={processFlow}
          content={content}
          citations={citations}
          ragReference={ragReference}
        />
      );
    }
    // 如果有独立的web内容（没有processFlow），显示web内容
    else if (webContent && webContent.messageType === 'web') {
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
    }
    // 普通文本消息
    else {
      return <Answer content={content} citations={citations} ragReference={ragReference} />;
    }
  }, [content, citations, ragReference, processFlow, webContent]);

  return (
    <div className="flex-1">
      <div className="flex items-start">
        <div className="h-10 w-10 flex-shrink-0 rounded-full bg-gray-100 p-2 text-gray-600 flex items-center justify-center shadow-sm">
          <Bot className="h-5 w-5" />
        </div>
        <div className="ml-3 max-w-[85%] w-full">
          <div className="bg-white border border-gray-200 rounded-lg shadow-sm p-4 hover:shadow-md transition-all duration-200">
            {messageContent}
          </div>
          <div className="text-xs text-gray-500 mt-1">
            {formattedTime}
          </div>
        </div>
      </div>
    </div>
  );
});

AssistantMessage.displayName = 'AssistantMessage';

/**
 * 修复版消息组件
 */
const MessageItemFixed: React.FC<MessageItemProps> = memo(({ message }) => {
  // 使用更稳定的key，避免时间戳变化导致的重新渲染
  const messageKey = useMemo(() => message.id, [message.id]);

  // 调试日志：显示每条消息的信息
  console.log('渲染消息组件:', {
    id: message.id,
    role: message.role,
    content: message.content.substring(0, 50) + (message.content.length > 50 ? '...' : ''),
    hasProcessFlow: !!message.processFlow,
    processFlowNodes: message.processFlow?.nodes?.length,
    isUser: message.role === 'user'
  });

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

MessageItemFixed.displayName = 'MessageItemFixed';

/**
 * 自定义比较函数，优化MessageItemFixed的重渲染
 */
const areMessageEqual = (prevProps: MessageItemProps, nextProps: MessageItemProps) => {
  const prevMsg = prevProps.message;
  const nextMsg = nextProps.message;

  // 基础属性比较
  if (prevMsg.id !== nextMsg.id ||
      prevMsg.role !== nextMsg.role ||
      prevMsg.content !== nextMsg.content) {
    return false;
  }

  // 深度比较processFlow
  const prevProcessFlowStr = JSON.stringify(prevMsg.processFlow);
  const nextProcessFlowStr = JSON.stringify(nextMsg.processFlow);
  if (prevProcessFlowStr !== nextProcessFlowStr) {
    return false;
  }

  // 深度比较webContent
  const prevWebContentStr = JSON.stringify(prevMsg.webContent);
  const nextWebContentStr = JSON.stringify(nextMsg.webContent);
  if (prevWebContentStr !== nextWebContentStr) {
    return false;
  }

  // 比较isTyping
  if (prevProps.isTyping !== nextProps.isTyping) {
    return false;
  }

  return true;
};

export default React.memo(MessageItemFixed, areMessageEqual);
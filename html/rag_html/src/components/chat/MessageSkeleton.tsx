import React from 'react';
import { Skeleton } from '@/components/ui/skeleton';
import { Bot, User } from 'lucide-react';

/**
 * 消息骨架屏组件
 * 用于在消息加载时提供更好的用户体验
 */
export const MessageSkeleton = React.memo(() => {
  return (
    <div className="flex items-start mb-4 animate-fadeIn">
      <div className="flex-1">
        <div className="flex items-start">
          <div className="h-10 w-10 flex-shrink-0 rounded-full bg-secondary/10 p-2 text-secondary flex items-center justify-center">
            <Bot className="h-5 w-5" />
          </div>
          <div className="ml-3 max-w-[85%] w-full space-y-3">
            <div className="bg-card rounded-lg shadow-sm p-4">
              <div className="space-y-2">
                <Skeleton className="h-4 bg-muted/50 w-3/4" />
                <Skeleton className="h-4 bg-muted/50 w-1/2" />
                <Skeleton className="h-4 bg-muted/50 w-5/6" />
              </div>
            </div>
            <div className="h-2">
              <Skeleton className="h-4 w-20 bg-muted/30" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
});

MessageSkeleton.displayName = 'MessageSkeleton';

/**
 * 用户消息骨架屏组件
 */
export const UserMessageSkeleton = React.memo(() => {
  return (
    <div className="flex items-start mb-4 animate-fadeIn">
      <div className="flex-1 flex justify-end">
        <div className="max-w-[85%] space-y-2">
          <div className="bg-primary/20 rounded-lg shadow-sm p-4">
            <Skeleton className="h-4 bg-primary/30 w-full" />
            <Skeleton className="h-4 bg-primary/30 w-2/3 mt-2" />
          </div>
          <Skeleton className="h-3 w-16 bg-muted/30 ml-auto" />
        </div>
        <div className="ml-3 flex-shrink-0 h-10 w-10 rounded-full bg-primary/10 p-2 text-primary flex items-center justify-center">
          <User className="h-5 w-5" />
        </div>
      </div>
    </div>
  );
});

UserMessageSkeleton.displayName = 'UserMessageSkeleton';

/**
 * 流式响应加载指示器
 */
export const StreamingIndicator = React.memo(() => {
  return (
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
  );
});

StreamingIndicator.displayName = 'StreamingIndicator';

/**
 * 历史记录加载指示器
 */
export const HistoryLoadingIndicator = React.memo(() => {
  return (
    <div className="flex justify-center py-4 animate-fadeIn">
      <div className="flex items-center space-x-2">
        <div className="h-4 w-4 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <span className="text-sm text-muted-foreground">正在加载更多历史记录...</span>
      </div>
    </div>
  );
});

HistoryLoadingIndicator.displayName = 'HistoryLoadingIndicator';
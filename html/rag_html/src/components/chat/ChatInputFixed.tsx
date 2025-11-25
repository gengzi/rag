import React, { forwardRef, useState, useCallback } from 'react';
import { Send, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface ChatInputProps {
  input: string;
  onInputChange: (value: string) => void;
  onSubmit: (e: React.FormEvent) => void;
  isLoading: boolean;
  loadingChat: boolean;
  disabled?: boolean;
  showPptTag?: boolean;
  showDeepResearchTag?: boolean;
  onPptTagToggle?: () => void;
  onDeepResearchTagToggle?: () => void;
  placeholder?: string;
  className?: string;
}

/**
 * 修复版聊天输入组件
 */
const ChatInputFixed = forwardRef<HTMLInputElement, ChatInputProps>(({
  input,
  onInputChange,
  onSubmit,
  isLoading,
  loadingChat,
  disabled = false,
  showPptTag = false,
  showDeepResearchTag = false,
  onPptTagToggle,
  onDeepResearchTagToggle,
  placeholder = "输入您的问题...",
  className = ""
}, ref) => {
  const [focused, setFocused] = useState(false);

  const handleInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    onInputChange(e.target.value);
  }, [onInputChange]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    // Ctrl+Enter 发送消息
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      if (input.trim() && !isLoading && !loadingChat) {
        onSubmit(e as any);
      }
    }

    // Escape 清空输入框
    if (e.key === 'Escape' && focused) {
      e.preventDefault();
      onInputChange('');
    }
  }, [input, isLoading, loadingChat, focused, onInputChange, onSubmit]);

  const isDisabled = disabled || isLoading || loadingChat;
  const showPlaceholder = !showPptTag && !showDeepResearchTag;

  return (
    <div className={`border-t border-input p-4 bg-background ${className}`}>
      <form onSubmit={onSubmit} className="flex items-center space-x-3 w-full">
        <div className="relative flex-1">
          <input
            ref={ref}
            type="text"
            placeholder={showPlaceholder ? placeholder : ""}
            value={input}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            disabled={isDisabled}
            data-input="chat"
            className={`w-full min-w-0 h-12 rounded-md border border-input bg-background px-4 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 transition-all duration-200 hover:border-primary/50 disabled:opacity-50 ${showPptTag || showDeepResearchTag ? 'pl-32' : 'pl-4'}`}
            aria-label="聊天消息输入"
            aria-describedby="chat-hint"
            aria-disabled={isDisabled}
            maxLength={1000}
          />

          <div id="chat-hint" className="sr-only">
            输入您的问题，按 Ctrl+Enter 发送
          </div>

          {/* PPT标签 */}
          {showPptTag && onPptTagToggle && (
            <button
              type="button"
              onClick={() => onPptTagToggle()}
              className="absolute left-1 top-1/2 transform -translate-y-1/2 h-8 px-3 rounded-md bg-blue-600 text-white text-xs font-medium flex items-center hover:bg-blue-700 transition-colors"
            >
              PPT生成工具
              <span className="ml-1 flex items-center justify-center w-3 h-3 rounded-full bg-white/20 hover:bg-white/30 transition-colors">×</span>
            </button>
          )}

          {/* 深度检索标签 */}
          {showDeepResearchTag && onDeepResearchTagToggle && (
            <div className="absolute left-1 top-1/2 transform -translate-y-1/2 h-8 px-3 rounded-md bg-blue-600 text-white text-xs font-medium flex items-center hover:bg-blue-700 transition-colors">
              深度检索
              <button
                type="button"
                onClick={() => onDeepResearchTagToggle()}
                className="ml-1 flex items-center justify-center w-3 h-3 rounded-full bg-white/20 hover:bg-white/30 transition-colors"
              >
                ×
              </button>
            </div>
          )}
        </div>

        {/* 发送按钮 - 使用明确的蓝色背景 */}
        <button
          type="submit"
          data-submit="chat"
          disabled={isDisabled || !input.trim()}
          className="h-12 w-12 rounded-md bg-blue-600 hover:bg-blue-700 text-white transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center"
          aria-label="发送消息"
        >
          {isLoading ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Send className="h-4 w-4" />
          )}
        </button>
      </form>

      {/* 快捷键提示和工具按钮 */}
      <div className="flex items-center justify-between mt-2">
        {input.trim() && (
          <p className="text-xs text-muted-foreground ml-1 animate-fadeIn">
            按 Ctrl+Enter 发送消息
          </p>
        )}

        <div className="flex space-x-2">
          {onPptTagToggle && (
            <button
              type="button"
              onClick={() => onPptTagToggle()}
              disabled={showPptTag || showDeepResearchTag || isDisabled}
              className="h-7 px-3 text-xs bg-gray-200 hover:bg-gray-300 text-gray-800 rounded disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              PPT生成工具
            </button>
          )}

          {onDeepResearchTagToggle && (
            <button
              type="button"
              onClick={() => onDeepResearchTagToggle()}
              disabled={showPptTag || showDeepResearchTag || isDisabled}
              className="h-7 px-3 text-xs bg-gray-200 hover:bg-gray-300 text-gray-800 rounded disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              深度检索
            </button>
          )}
        </div>
      </div>
    </div>
  );
});

ChatInputFixed.displayName = 'ChatInputFixed';

export default ChatInputFixed;
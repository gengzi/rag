import { useEffect, useCallback } from 'react';

/**
 * 可访问性相关的Hook
 * 提供键盘导航、屏幕阅读器支持等可访问性功能
 */

interface AccessibilityOptions {
  enableKeyboardNavigation?: boolean;
  enableScreenReader?: boolean;
  enableFocusManagement?: boolean;
}

export const useAccessibility = (options: AccessibilityOptions = {}) => {
  const {
    enableKeyboardNavigation = true,
    enableScreenReader = true,
    enableFocusManagement = true,
  } = options;

  /**
   * 键盘快捷键处理
   */
  const handleKeyboardShortcuts = useCallback((event: KeyboardEvent) => {
    if (!enableKeyboardNavigation) return;

    // Ctrl/Cmd + Enter 发送消息
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      const submitButton = document.querySelector('[data-submit="chat"]') as HTMLButtonElement;
      if (submitButton && !submitButton.disabled) {
        submitButton.click();
      }
    }

    // Escape 清空输入框
    if (event.key === 'Escape') {
      const input = document.querySelector('[data-input="chat"]') as HTMLInputElement;
      if (input && document.activeElement === input) {
        input.value = '';
        input.dispatchEvent(new Event('input', { bubbles: true }));
      }
    }

    // Ctrl/Cmd + K 聚焦到搜索/输入框
    if ((event.ctrlKey || event.metaKey) && event.key === 'k') {
      event.preventDefault();
      const input = document.querySelector('[data-input="chat"]') as HTMLInputElement;
      if (input) {
        input.focus();
      }
    }

    // Tab键在聊天消息之间导航
    if (event.key === 'Tab' && !event.shiftKey) {
      const focusedElement = document.activeElement;
      const messages = document.querySelectorAll('[data-message]');
      const currentIndex = Array.from(messages).indexOf(focusedElement as Element);

      if (currentIndex >= 0 && currentIndex < messages.length - 1) {
        event.preventDefault();
        (messages[currentIndex + 1] as HTMLElement).focus();
      }
    }

    // Shift + Tab 向前导航
    if (event.key === 'Tab' && event.shiftKey) {
      const focusedElement = document.activeElement;
      const messages = document.querySelectorAll('[data-message]');
      const currentIndex = Array.from(messages).indexOf(focusedElement as Element);

      if (currentIndex > 0) {
        event.preventDefault();
        (messages[currentIndex - 1] as HTMLElement).focus();
      }
    }
  }, [enableKeyboardNavigation]);

  /**
   * 屏幕阅读器公告
   */
  const announceToScreenReader = useCallback((message: string, priority: 'polite' | 'assertive' = 'polite') => {
    if (!enableScreenReader) return;

    const announcement = document.createElement('div');
    announcement.setAttribute('aria-live', priority);
    announcement.setAttribute('aria-atomic', 'true');
    announcement.style.position = 'absolute';
    announcement.style.left = '-10000px';
    announcement.style.width = '1px';
    announcement.style.height = '1px';
    announcement.style.overflow = 'hidden';

    document.body.appendChild(announcement);
    announcement.textContent = message;

    // 清理DOM
    setTimeout(() => {
      document.body.removeChild(announcement);
    }, 1000);
  }, [enableScreenReader]);

  /**
   * 焦点管理
   */
  const trapFocus = useCallback((containerElement: HTMLElement) => {
    if (!enableFocusManagement) return;

    const focusableElements = containerElement.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    ) as NodeListOf<HTMLElement>;

    const firstElement = focusableElements[0];
    const lastElement = focusableElements[focusableElements.length - 1];

    const handleTabKey = (e: KeyboardEvent) => {
      if (e.key === 'Tab') {
        if (e.shiftKey) {
          if (document.activeElement === firstElement) {
            lastElement.focus();
            e.preventDefault();
          }
        } else {
          if (document.activeElement === lastElement) {
            firstElement.focus();
            e.preventDefault();
          }
        }
      }
    };

    containerElement.addEventListener('keydown', handleTabKey);

    return () => {
      containerElement.removeEventListener('keydown', handleTabKey);
    };
  }, [enableFocusManagement]);

  /**
   * 自动滚动到新消息（可访问性友好）
   */
  const scrollToNewMessage = useCallback((messageId: string) => {
    const messageElement = document.querySelector(`[data-message-id="${messageId}"]`);
    if (messageElement) {
      messageElement.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
        inline: 'nearest'
      });

      // 聚焦到新消息以便屏幕阅读器读取
      (messageElement as HTMLElement).focus();

      // 公告新消息
      announceToScreenReader('收到新消息');
    }
  }, [announceToScreenReader]);

  /**
   * 高对比度模式检测
   */
  const checkHighContrastMode = useCallback(() => {
    if (typeof window === 'undefined') return false;

    const testElement = document.createElement('div');
    testElement.style.color = 'rgb(1, 1, 1)';
    testElement.style.position = 'absolute';
    testElement.style.left = '-9999px';
    document.body.appendChild(testElement);

    const computedColor = window.getComputedStyle(testElement).color;
    document.body.removeChild(testElement);

    // 如果系统修改了颜色，说明启用了高对比度模式
    return computedColor !== 'rgb(1, 1, 1)';
  }, []);

  /**
   * 检测系统主题偏好
   */
  const getSystemTheme = useCallback(() => {
    if (typeof window === 'undefined') return 'light';

    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }

    return 'light';
  }, []);

  /**
   * 检测减少动画偏好
   */
  const prefersReducedMotion = useCallback(() => {
    if (typeof window === 'undefined') return false;

    return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }, []);

  // 初始化事件监听器
  useEffect(() => {
    document.addEventListener('keydown', handleKeyboardShortcuts);

    return () => {
      document.removeEventListener('keydown', handleKeyboardShortcuts);
    };
  }, [handleKeyboardShortcuts]);

  return {
    // 键盘导航
    handleKeyboardShortcuts,

    // 屏幕阅读器
    announceToScreenReader,

    // 焦点管理
    trapFocus,
    scrollToNewMessage,

    // 可访问性检测
    checkHighContrastMode,
    getSystemTheme,
    prefersReducedMotion,

    // 工具函数
    announceMessage: (message: string, isUser: boolean) => {
      const announcement = isUser ? `您发送了: ${message}` : `AI回复: ${message}`;
      announceToScreenReader(announcement);
    },

    announceError: (error: string) => {
      announceToScreenReader(`错误: ${error}`, 'assertive');
    },

    announceStatus: (status: string) => {
      announceToScreenReader(`状态: ${status}`);
    },
  };
};
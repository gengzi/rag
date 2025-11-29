import { useEffect, useCallback, useRef } from 'react';

/**
 * 键盘导航相关的Hook
 * 提供键盘快捷键和导航功能
 */

interface KeyboardShortcut {
  key: string;
  ctrlKey?: boolean;
  shiftKey?: boolean;
  altKey?: boolean;
  metaKey?: boolean;
  action: () => void;
  description?: string;
  preventDefault?: boolean;
}

interface KeyboardNavigationOptions {
  enableShortcuts?: boolean;
  enableNavigation?: boolean;
  scope?: 'global' | 'chat' | 'input';
}

export const useKeyboardNavigation = (
  shortcuts: KeyboardShortcut[] = [],
  options: KeyboardNavigationOptions = {}
) => {
  const {
    enableShortcuts = true,
    enableNavigation = true,
    scope = 'global'
  } = options;

  // 用于追踪当前焦点索引
  const focusableElementsRef = useRef<HTMLElement[]>([]);
  const currentFocusIndexRef = useRef<number>(-1);

  /**
   * 获取当前作用域内的可聚焦元素
   */
  const getFocusableElements = useCallback(() => {
    const selector = scope === 'input'
      ? 'input, textarea, button[data-submit]'
      : scope === 'chat'
      ? '[data-message], button, input, textarea, [tabindex]:not([tabindex="-1"])'
      : 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

    const elements = document.querySelectorAll(selector) as NodeListOf<HTMLElement>;
    focusableElementsRef.current = Array.from(elements).filter(
      el => el.offsetParent !== null && !(el as HTMLInputElement).disabled
    );

    return focusableElementsRef.current;
  }, [scope]);

  /**
   * 处理键盘快捷键
   */
  const handleKeyboardShortcuts = useCallback((event: KeyboardEvent) => {
    if (!enableShortcuts) return;

    for (const shortcut of shortcuts) {
      const keyMatch = event.key.toLowerCase() === shortcut.key.toLowerCase();
      const ctrlMatch = !!shortcut.ctrlKey === event.ctrlKey;
      const shiftMatch = !!shortcut.shiftKey === event.shiftKey;
      const altMatch = !!shortcut.altKey === event.altKey;
      const metaMatch = !!shortcut.metaKey === event.metaKey;

      if (keyMatch && ctrlMatch && shiftMatch && altMatch && metaMatch) {
        if (shortcut.preventDefault !== false) {
          event.preventDefault();
        }
        shortcut.action();
        break;
      }
    }
  }, [shortcuts, enableShortcuts]);

  /**
   * 焦点导航
   */
  const navigateFocus = useCallback((direction: 'next' | 'previous') => {
    if (!enableNavigation) return;

    const elements = getFocusableElements();
    if (elements.length === 0) return;

    const currentIndex = currentFocusIndexRef.current;
    let nextIndex: number;

    if (direction === 'next') {
      nextIndex = currentIndex < elements.length - 1 ? currentIndex + 1 : 0;
    } else {
      nextIndex = currentIndex > 0 ? currentIndex - 1 : elements.length - 1;
    }

    currentFocusIndexRef.current = nextIndex;
    elements[nextIndex].focus();
  }, [enableNavigation, getFocusableElements]);

  /**
   * Tab键导航处理
   */
  const handleTabNavigation = useCallback((event: KeyboardEvent) => {
    if (!enableNavigation || !['Tab'].includes(event.key)) return;

    const elements = getFocusableElements();
    if (elements.length === 0) return;

    const currentIndex = elements.indexOf(event.target as HTMLElement);
    if (currentIndex === -1) return;

    if (event.key === 'Tab') {
      event.preventDefault();

      if (event.shiftKey) {
        // Shift + Tab: 向前导航
        const prevIndex = currentIndex > 0 ? currentIndex - 1 : elements.length - 1;
        elements[prevIndex].focus();
        currentFocusIndexRef.current = prevIndex;
      } else {
        // Tab: 向后导航
        const nextIndex = currentIndex < elements.length - 1 ? currentIndex + 1 : 0;
        elements[nextIndex].focus();
        currentFocusIndexRef.current = nextIndex;
      }
    }
  }, [enableNavigation, getFocusableElements]);

  /**
   * 方向键导航（在聊天消息中）
   */
  const handleArrowNavigation = useCallback((event: KeyboardEvent) => {
    if (!enableNavigation || !scope.includes('chat')) return;

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      const messages = document.querySelectorAll('[data-message]');
      if (messages.length === 0) return;

      const currentIndex = Array.from(messages).indexOf(event.target as HTMLElement);
      if (currentIndex === -1) return;

      event.preventDefault();

      if (event.key === 'ArrowDown') {
        // 向下导航到下一条消息
        const nextIndex = currentIndex < messages.length - 1 ? currentIndex + 1 : currentIndex;
        (messages[nextIndex] as HTMLElement).focus();
      } else {
        // 向上导航到上一条消息
        const prevIndex = currentIndex > 0 ? currentIndex - 1 : 0;
        (messages[prevIndex] as HTMLElement).focus();
      }
    }
  }, [enableNavigation, scope]);

  /**
   * 聚焦到第一个可聚焦元素
   */
  const focusFirst = useCallback(() => {
    const elements = getFocusableElements();
    if (elements.length > 0) {
      currentFocusIndexRef.current = 0;
      elements[0].focus();
    }
  }, [getFocusableElements]);

  /**
   * 聚焦到最后一个可聚焦元素
   */
  const focusLast = useCallback(() => {
    const elements = getFocusableElements();
    if (elements.length > 0) {
      currentFocusIndexRef.current = elements.length - 1;
      elements[elements.length - 1].focus();
    }
  }, [getFocusableElements]);

  /**
   * 聚焦到特定元素
   */
  const focusElement = useCallback((selector: string) => {
    const element = document.querySelector(selector) as HTMLElement;
    if (element && element.offsetParent !== null && !(element as HTMLInputElement).disabled) {
      const elements = getFocusableElements();
      const index = elements.indexOf(element);
      currentFocusIndexRef.current = index >= 0 ? index : -1;
      element.focus();
      return true;
    }
    return false;
  }, [getFocusableElements]);

  /**
   * 聚焦到聊天输入框
   */
  const focusChatInput = useCallback(() => {
    return focusElement('[data-input="chat"]');
  }, [focusElement]);

  /**
   * 激活发送按钮
   */
  const activateSendButton = useCallback(() => {
    const sendButton = document.querySelector('[data-submit="chat"]') as HTMLButtonElement;
    if (sendButton && !sendButton.disabled) {
      sendButton.click();
      return true;
    }
    return false;
  }, []);

  /**
   * 清空输入框
   */
  const clearInput = useCallback(() => {
    const input = document.querySelector('[data-input="chat"]') as HTMLInputElement;
    if (input) {
      input.value = '';
      input.dispatchEvent(new Event('input', { bubbles: true }));
      return true;
    }
    return false;
  }, []);

  // 初始化事件监听器
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      handleKeyboardShortcuts(event);
      handleTabNavigation(event);
      handleArrowNavigation(event);
    };

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [handleKeyboardShortcuts, handleTabNavigation, handleArrowNavigation]);

  // 监听焦点变化，更新当前焦点索引
  useEffect(() => {
    const handleFocusIn = (event: FocusEvent) => {
      const elements = focusableElementsRef.current;
      const index = elements.indexOf(event.target as HTMLElement);
      if (index >= 0) {
        currentFocusIndexRef.current = index;
      }
    };

    document.addEventListener('focusin', handleFocusIn);

    return () => {
      document.removeEventListener('focusin', handleFocusIn);
    };
  }, []);

  return {
    // 基础导航
    navigateFocus,
    focusFirst,
    focusLast,
    focusElement,

    // 聊天特定功能
    focusChatInput,
    activateSendButton,
    clearInput,

    // 状态
    currentFocusIndex: currentFocusIndexRef.current,
    focusableElements: focusableElementsRef.current,

    // 工具函数
    getFocusableElements,
  };
};

/**
 * 预定义的聊天快捷键
 */
export const CHAT_SHORTCUTS: KeyboardShortcut[] = [
  {
    key: 'Enter',
    ctrlKey: true,
    action: () => {
      const sendButton = document.querySelector('[data-submit="chat"]') as HTMLButtonElement;
      if (sendButton && !sendButton.disabled) {
        sendButton.click();
      }
    },
    description: '发送消息',
    preventDefault: true,
  },
  {
    key: 'k',
    ctrlKey: true,
    action: () => {
      const input = document.querySelector('[data-input="chat"]') as HTMLInputElement;
      if (input) {
        input.focus();
      }
    },
    description: '聚焦输入框',
    preventDefault: true,
  },
  {
    key: 'Escape',
    action: () => {
      const input = document.querySelector('[data-input="chat"]') as HTMLInputElement;
      if (input && document.activeElement === input) {
        input.value = '';
        input.dispatchEvent(new Event('input', { bubbles: true }));
      }
    },
    description: '清空输入框',
    preventDefault: false,
  },
  {
    key: 'ArrowUp',
    altKey: true,
    action: () => {
      // 导航到上一条消息
      const messages = document.querySelectorAll('[data-message]');
      if (messages.length > 0) {
        (messages[messages.length - 1] as HTMLElement).focus();
      }
    },
    description: '导航到上一条消息',
    preventDefault: true,
  },
  {
    key: 'ArrowDown',
    altKey: true,
    action: () => {
      // 导航到下一条消息
      const messages = document.querySelectorAll('[data-message]');
      if (messages.length > 0) {
        (messages[0] as HTMLElement).focus();
      }
    },
    description: '导航到下一条消息',
    preventDefault: true,
  },
];
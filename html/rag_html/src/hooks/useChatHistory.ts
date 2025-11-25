import { useState, useCallback, useRef, useEffect } from 'react';
import { useToast } from '@/components/ui/use-toast';
import { parseMessagesFromAPI, Message } from '@/utils/messageFormatter';
import { getChatHistory, ChatHistoryParams } from '@/lib/services/chatService';


interface UseChatHistoryOptions {
  conversationId: string;
  onMessagesUpdate: (messages: Message[], isLoadMore: boolean) => void;
  onThreadIdUpdate: (threadId: string) => void;
  onTitleUpdate: (title: string) => void;
}

/**
 * 聊天历史加载Hook
 * 处理聊天记录的加载、分页和滚动
 */
export const useChatHistory = ({
  conversationId,
  onMessagesUpdate,
  onThreadIdUpdate,
  onTitleUpdate
}: UseChatHistoryOptions) => {
  const { toast } = useToast();
  const [loadingChat, setLoadingChat] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [before, setBefore] = useState("");
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 加载更多历史记录
  const loadMoreHistory = useCallback(async () => {
    console.log('loadMoreHistory 被调用', { loadingChat, hasMore, before }); // 调试日志

    if (loadingChat || !hasMore) {
      console.log('跳过加载更多:', { loadingChat, hasMore });
      return;
    }

    try {
      setLoadingChat(true);
      console.log('开始加载更多历史记录，before:', before);

      const params: ChatHistoryParams = {
        id: conversationId,
        limit: 50,
        before: before
      };

      const data = await getChatHistory(params);
      console.log('获取到历史记录数据:', data);

      if (!data) {
        console.log('没有获取到数据');
        return;
      }

      // 解析消息
      const formattedMessages = parseMessagesFromAPI(data);
      console.log('解析后的消息数量:', formattedMessages.length);

      // 更新分页状态
      if (data.before) {
        console.log('更新before为:', data.before);
        setBefore(data.before);
      } else {
        console.log('没有更多历史记录了');
        setHasMore(false);
      }

      // 更新消息（加载更多模式）
      onMessagesUpdate(formattedMessages, true);

      // 保持滚动位置
      if (messagesContainerRef.current) {
        const currentScrollHeight = messagesContainerRef.current.scrollHeight;
        setTimeout(() => {
          if (messagesContainerRef.current) {
            const newScrollHeight = messagesContainerRef.current.scrollHeight;
            messagesContainerRef.current.scrollTop = newScrollHeight - currentScrollHeight;
          }
        }, 0);
      }

    } catch (error) {
      console.error('加载更多历史记录错误:', error);
    } finally {
      setLoadingChat(false);
    }
  }, [conversationId, before, loadingChat, hasMore, onMessagesUpdate]);

  // 滚动监听实现加载更多
  useEffect(() => {
    const container = messagesContainerRef.current;
    if (!container) return;

    let scrollTimeout: NodeJS.Timeout;

    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = container;

      // 当滚动到顶部附近时触发加载更多
      if (scrollTop <= 50 && !loadingChat && hasMore) {
        // 防抖处理
        clearTimeout(scrollTimeout);
        scrollTimeout = setTimeout(() => {
          console.log('触发加载更多历史记录'); // 调试日志
          loadMoreHistory();
        }, 300);
      }
    };

    container.addEventListener('scroll', handleScroll);
    return () => {
      container.removeEventListener('scroll', handleScroll);
      clearTimeout(scrollTimeout);
    };
  }, [loadingChat, hasMore, loadMoreHistory]);

  // 初始加载 - 只在conversationId变化时执行
  useEffect(() => {
    const initialLoad = async () => {
      try {
        setLoadingChat(true);

        const params: ChatHistoryParams = {
          id: conversationId,
          limit: 50,
          before: ""
        };

        const data = await getChatHistory(params);
        console.log('初始加载历史记录数据:', data);

        if (!data) {
          console.log('初始加载没有获取到数据');
          setLoadingChat(false);
          return;
        }

        // 解析消息
        const formattedMessages = parseMessagesFromAPI(data);
        console.log('初始加载解析后的消息数量:', formattedMessages.length);

        // 更新分页状态
        if (data.before) {
          setBefore(data.before);
        } else {
          setHasMore(false);
        }

        // 提取threadId
        const latestThreadId = formattedMessages
          .flatMap(msg => msg.processFlow?.nodes || [])
          .find(() => true) // 查找任何节点来获取threadId
          ? formattedMessages[0]?.processFlow?.nodes?.[0]?.threadId || ''
          : '';

        if (latestThreadId) {
          onThreadIdUpdate(latestThreadId);
        }

        // 更新消息
        onMessagesUpdate(formattedMessages, false);

        // 设置聊天标题
        const firstUserMessage = formattedMessages.find(msg => msg.role === 'user');
        if (firstUserMessage) {
          const title = firstUserMessage.content.substring(0, 20) +
                       (firstUserMessage.content.length > 20 ? '...' : '');
          onTitleUpdate(title);
        }

        // 滚动到底部
        setTimeout(() => {
          messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        }, 100);

      } catch (error) {
        console.error('获取聊天记录错误:', error);
        // 这里不使用toast，避免重复错误提示
      } finally {
        setLoadingChat(false);
      }
    };

    if (conversationId) {
      initialLoad();
    }
  }, [conversationId]); // 只依赖conversationId

  // 滚动到底部的函数
  const scrollToBottom = useCallback(() => {
    setTimeout(() => {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, 100);
  }, []);

  return {
    loadingChat,
    hasMore,
    messagesContainerRef,
    messagesEndRef,
    loadMoreHistory,
    scrollToBottom,
  };
};
import { useState, useCallback, useRef, useEffect } from 'react';
import { useToast } from '@/components/ui/use-toast';
import { parseMessagesFromAPI, Message } from '@/utils/messageFormatter';
import { getChatHistory, ChatHistoryParams } from '@/lib/services/chatService';


interface UseChatHistoryOptions {
  conversationId: string;
  onMessagesUpdate: (messages: Message[], isLoadMore: boolean) => void;
  onThreadIdUpdate: (threadId: string) => void;
  onTitleUpdate: (title: string) => void;
  onRunMessageIdUpdate?: (runMessageId: string) => void; // 新增回调处理runMessageId
}

/**
 * 聊天历史加载Hook
 * 处理聊天记录的加载、分页和滚动
 */
export const useChatHistory = ({
  conversationId,
  onMessagesUpdate,
  onThreadIdUpdate,
  onTitleUpdate,
  onRunMessageIdUpdate
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
      const nextCursor = null; // 暂时设为null，因为API响应中不存在该字段
      console.log('加载更多更新分页状态:', {
        nextCursor,
        before: data.before,
        data,
        messageCount: formattedMessages.length
      }); // 调试日志

      if (nextCursor !== null && nextCursor !== undefined && nextCursor !== '') {
        console.log('加载更多更新before为:', nextCursor);
        setBefore(nextCursor);
        setHasMore(true);
      } else {
        console.log('加载更多完成，没有更多历史记录了');
        setHasMore(false);
      }

      // 保持滚动位置（在更新消息前记录）
      if (messagesContainerRef.current) {
        const currentScrollHeight = messagesContainerRef.current.scrollHeight;
        const currentScrollTop = messagesContainerRef.current.scrollTop;

        console.log('加载更多前的滚动位置:', {
          scrollHeight: currentScrollHeight,
          scrollTop: currentScrollTop
        });

        // 更新消息
        onMessagesUpdate(formattedMessages, true);

        // 在DOM更新后保持滚动位置
        setTimeout(() => {
          if (messagesContainerRef.current) {
            const newScrollHeight = messagesContainerRef.current.scrollHeight;
            // 计算新的滚动位置：原滚动位置 + 新增加的高度
            const newScrollTop = newScrollHeight - currentScrollHeight;

            console.log('加载更多后的滚动位置:', {
              oldScrollHeight: currentScrollHeight,
              newScrollHeight,
              newScrollTop
            });

            messagesContainerRef.current.scrollTop = Math.max(0, newScrollTop);
          }
        }, 50); // 增加延迟时间，确保DOM已更新
      } else {
        // 如果没有容器，直接更新消息
        onMessagesUpdate(formattedMessages, true);
      }

    } catch (error) {
      console.error('加载更多历史记录错误:', error);
    } finally {
      setLoadingChat(false);
    }
  }, [conversationId, before, loadingChat, hasMore, onMessagesUpdate]);

  // 滚动监听实现加载更多
  useEffect(() => {
    console.log('设置滚动监听器', { hasMore, loadingChat }); // 调试日志

    const container = messagesContainerRef.current;
    if (!container) {
      console.log('容器不存在，跳过滚动监听设置');
      return;
    }

    let scrollTimeout: NodeJS.Timeout;

    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = container;

      console.log('滚动事件触发', {
        scrollTop,
        hasMore,
        loadingChat,
        nextCursor: before,
        scrollHeight,
        clientHeight
      }); // 调试日志

      // 当滚动到顶部附近时触发加载更多
      if (scrollTop <= 20 && !loadingChat && hasMore) {
        console.log('满足加载条件，准备加载更多'); // 调试日志

        // 防抖处理
        clearTimeout(scrollTimeout);
        scrollTimeout = setTimeout(() => {
          console.log('执行加载更多历史记录'); // 调试日志
          loadMoreHistory();
        }, 300); // 减少防抖时间，让加载更快响应
      }
    };

    container.addEventListener('scroll', handleScroll, { passive: true });
    console.log('滚动监听器已设置'); // 调试日志

    return () => {
      container.removeEventListener('scroll', handleScroll);
      clearTimeout(scrollTimeout);
      console.log('滚动监听器已清理'); // 调试日志
    };
  }, [loadingChat, hasMore, loadMoreHistory, before]); // 添加before依赖

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

        // 更新分页状态 - API响应中不存在nextCursor字段
        const nextCursor = null;
        console.log('更新分页状态:', {
          hasData: !!data,
          nextCursor,
          before: data.before,
          data,
          messageCount: formattedMessages.length
        }); // 调试日志

        if (nextCursor !== null && nextCursor !== undefined && nextCursor !== '') {
          console.log('设置before为:', nextCursor);
          setBefore(nextCursor);
          setHasMore(true); // 有nextCursor表示还有更多数据
        } else {
          console.log('没有更多历史记录了，nextCursor为空');
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

        // 处理runMessageId，如果有则调用回调
        if (data.runMessageId && onRunMessageIdUpdate) {
          console.log('检测到正在输出的消息ID:', data.runMessageId);
          onRunMessageIdUpdate(data.runMessageId);
        }

        // 设置聊天标题
        const firstUserMessage = formattedMessages.find(msg => msg.role === 'user');
        if (firstUserMessage) {
          const title = firstUserMessage.content.substring(0, 20) +
                       (firstUserMessage.content.length > 20 ? '...' : '');
          onTitleUpdate(title);
        }

        // 滚动到底部
        setTimeout(() => {
          console.log('初始加载完成，滚动到底部');
          if (messagesEndRef.current) {
            messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
          }
        }, 200); // 增加延迟确保DOM已完全更新

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
    before, // 暴露调试信息
  };
};
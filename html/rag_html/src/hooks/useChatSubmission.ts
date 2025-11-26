import { useState, useCallback, useEffect, useRef } from 'react';
import { processStreamData, StreamDataProcessor, Message, ProcessNode } from '@/utils/messageFormatter';
import { streamingCache, ResumeParams } from '@/utils/streamingCache';

/**
 * 比较两个消息是否需要更新
 */
const shouldUpdateMessage = (prevMessage: Message | undefined, newMessage: Message): boolean => {
  if (!prevMessage) return true;

  // 比较processFlow
  const prevProcessFlowStr = JSON.stringify(prevMessage.processFlow);
  const newProcessFlowStr = JSON.stringify(newMessage.processFlow);
  if (prevProcessFlowStr !== newProcessFlowStr) return true;

  // 比较webContent
  const prevWebContentStr = JSON.stringify(prevMessage.webContent);
  const newWebContentStr = JSON.stringify(newMessage.webContent);
  if (prevWebContentStr !== newWebContentStr) return true;

  // 比较其他属性
  return (
    prevMessage.id !== newMessage.id ||
    prevMessage.content !== newMessage.content ||
    prevMessage.role !== newMessage.role
  );
};

interface UseChatSubmissionOptions {
  conversationId: string;
  threadId: string;
  onThreadIdUpdate: (threadId: string) => void;
  onMessageUpdate: (message: Message) => void;
  onNewMessage: (message: Message) => void;
  onRenderCachedMessage: (streamData: any) => void; // 新增：用于渲染缓存消息
  getToken: () => string;
}

/**
 * 聊天提交逻辑Hook
 * 处理消息发送和流式响应，支持断点续读
 */
export const useChatSubmission = ({
  conversationId,
  threadId,
  onThreadIdUpdate,
  onMessageUpdate,
  onNewMessage,
  onRenderCachedMessage,
  getToken
}: UseChatSubmissionOptions) => {
  const [isLoading, setIsLoading] = useState(false);
  const [isResuming, setIsResuming] = useState(false);
  const currentStreamRef = useRef<string | null>(null);

  // 用于减少更新频率的refs
  const updateTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const pendingUpdateRef = useRef<Message | null>(null);

  // 组件初始化时检查是否有可恢复的会话
  useEffect(() => {
    const hasResumeSession = streamingCache.hasResumeSession(conversationId);
    if (hasResumeSession) {
      console.log('检测到可恢复的会话:', conversationId);
      // 这里可以触发UI提示用户是否恢复会话
    }
  }, [conversationId]);

  /**
   * 渲染缓存的消息
   */
  const renderCachedMessages = useCallback(() => {
    console.log('开始渲染缓存消息...');
    const renderedCount = streamingCache.renderCachedMessages(conversationId, (streamData) => {
      console.log('渲染缓存消息:', streamData);
      onRenderCachedMessage(streamData);
    });
    console.log(`缓存消息渲染完成，共 ${renderedCount} 条`);
    return renderedCount;
  }, [conversationId, onRenderCachedMessage]);

  /**
   * 处理流式响应的统一函数
   */
  const handleStreamResponse = useCallback(async (
    response: Response,
    assistantMessageId: string,
    isResume: boolean = false
  ): Promise<string> => {
    const reader = response.body?.getReader();
    const decoder = new TextDecoder();

    if (!reader) {
      throw new Error('无法获取响应流');
    }

    // 创建初始助手消息
    const initialAssistantMessage: Message = {
      id: assistantMessageId,
      content: '',
      role: 'assistant',
      createdAt: new Date(),
      processFlow: {
        nodes: [],
        edges: []
      }
    };

    onNewMessage(initialAssistantMessage);

    // 如果是恢复模式，设置缓存消息的起始seqNum
    let seqNumCounter = isResume ? (streamingCache.getResumeParams(conversationId)?.seqNum || 0) + 1 : 1;

    // 流式数据状态
    let streamState = {
      textContentBuffer: '',
      processNodes: [] as ProcessNode[],
      processEdges: [] as { from: string; to: string }[],
      lastMessageType: null as 'text' | 'agent' | 'web' | null,
      lastNodeName: null as string | null,
      webContentBuffer: '', // 新增：web内容缓冲区
    };

    let hasWebContent = false;
    let webContent = '';

    // 开始缓存会话
    if (!isResume) {
      streamingCache.startSession(conversationId, assistantMessageId, 0);
    }

    try {
      // 处理流式数据
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        const lines = chunk.split('\n');

        for (const line of lines) {
          if (!line.trim()) continue;

          try {
            let jsonStr = line;
            if (jsonStr.startsWith('data:')) {
              jsonStr = jsonStr.substring(5).trim();
            }

            if (!jsonStr) continue;

            const data = JSON.parse(jsonStr);

            // 更新threadId
            if (data.threadId) {
              onThreadIdUpdate(data.threadId);
            }

            // 缓存流式数据
            streamingCache.addStreamData(data, seqNumCounter, assistantMessageId);
            seqNumCounter++;

            // 使用流式数据处理器
            const processor: StreamDataProcessor = {
              messageId: assistantMessageId,
              messageType: data.messageType,
              content: data.content,
              threadId: data.threadId
            };

            const result = processStreamData(processor, streamState);
            streamState = {
              textContentBuffer: result.updatedTextContentBuffer,
              processNodes: result.updatedProcessNodes,
              processEdges: result.updatedProcessEdges,
              lastMessageType: result.updatedLastMessageType,
              lastNodeName: result.updatedLastNodeName,
              webContentBuffer: result.updatedWebContentBuffer,
            };

            if (result.hasWebContent) {
              hasWebContent = true;
              webContent = result.webContent;
            }

            // 更新助手消息 - 使用节流机制减少更新频率
            const assistantMessage: Message = {
              id: assistantMessageId,
              content: '',
              role: 'assistant',
              createdAt: new Date(),
              processFlow: {
                nodes: result.updatedProcessNodes,
                edges: result.updatedProcessEdges
              },
              ...(result.hasWebContent && {
                webContent: {
                  messageType: 'web',
                  content: result.webContent,
                  nodeName: result.updatedProcessNodes.find(n => n.type === 'web')?.name || 'web'
                }
              })
            };

            // 使用节流机制更新消息
            pendingUpdateRef.current = assistantMessage;
            if (updateTimeoutRef.current) {
              clearTimeout(updateTimeoutRef.current);
            }
            updateTimeoutRef.current = setTimeout(() => {
              if (pendingUpdateRef.current) {
                onMessageUpdate(pendingUpdateRef.current);
                pendingUpdateRef.current = null;
              }
            }, 50); // 50ms节流延迟

          } catch (parseError) {
            console.error("解析流式数据失败:", parseError);
          }
        }
      }

      // 完成流式响应，设置所有节点为完成状态
      const finalAssistantMessage: Message = {
        id: assistantMessageId,
        content: '',
        role: 'assistant',
        createdAt: new Date(),
        processFlow: {
          nodes: streamState.processNodes.map(node => ({
            ...node,
            status: 'completed' as const
          })),
          edges: streamState.processEdges
        },
        ...(hasWebContent && {
          webContent: {
            messageType: 'web',
            content: webContent
          }
        })
      };

      // 清除节流定时器并立即更新最终状态
      if (updateTimeoutRef.current) {
        clearTimeout(updateTimeoutRef.current);
        updateTimeoutRef.current = null;
      }
      pendingUpdateRef.current = null;
      onMessageUpdate(finalAssistantMessage);

      return assistantMessageId;

    } finally {
      // 流式响应完成后清除缓存和清理节流相关
      if (!isResume) {
        streamingCache.endSession();
      }
      if (updateTimeoutRef.current) {
        clearTimeout(updateTimeoutRef.current);
        updateTimeoutRef.current = null;
      }
      pendingUpdateRef.current = null;
    }
  }, [conversationId, onThreadIdUpdate, onMessageUpdate, onNewMessage]);

  const sendMessage = useCallback(async (input: string, agentId?: string) => {
    if (!input.trim() || isLoading) return null;

    try {
      setIsLoading(true);

      // 检查是否恢复模式
      const resumeParams = streamingCache.getResumeParams(conversationId);
      const isResume = resumeParams !== null;

      console.log('发送消息，模式:', isResume ? '恢复模式' : '新对话模式');

      if (isResume && resumeParams.messageId && resumeParams.seqNum !== undefined) {
        // 恢复模式：先渲染缓存的消息，然后续读
        setIsResuming(true);

        console.log('恢复模式 - 渲染缓存消息');
        renderCachedMessages();

        // 创建用户消息（恢复模式的用户消息）
        const userMessage: Message = {
          id: `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
          content: input, // 用户输入的是恢复请求
          role: 'user',
          createdAt: new Date()
        };

        // 发送用户消息到父组件
        onNewMessage(userMessage);

        // 准备恢复请求数据
        const resumeRequestData = {
          query: input.trim(),
          conversationId,
          threadId,
          messageId: resumeParams.messageId,
          seqNum: resumeParams.seqNum + 1, // 从下一个seqNum开始
          agentId: agentId || ''
        };

        console.log('发送恢复请求:', resumeRequestData);

        // 调用恢复接口
        const response = await fetch('http://localhost:8086/chat/stream/resume', {
          method: 'POST',
          headers: {
            'accept': 'text/event-stream',
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${getToken()}`
          },
          body: JSON.stringify(resumeRequestData)
        });

        if (!response.ok) {
          throw new Error(`恢复请求失败! status: ${response.status}`);
        }

        // 创建助手消息ID用于恢复模式
        const assistantMessageId = `msg-${Date.now()}-assistant-${Math.random().toString(36).substr(2, 9)}`;

        // 处理恢复的流式响应
        await handleStreamResponse(response, assistantMessageId, true);

      } else {
        // 新对话模式
        // 创建用户消息
        const userMessage: Message = {
          id: `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
          content: input,
          role: 'user',
          createdAt: new Date()
        };

        // 发送用户消息到父组件
        onNewMessage(userMessage);

        // 准备请求数据
        const requestData = {
          query: input.trim(),
          conversationId,
          threadId,
          agentId: agentId || ''
        };

        console.log('发送新对话请求:', requestData);

        // 调用新的API端点
        const response = await fetch('http://localhost:8086/chat/stream/msg', {
          method: 'POST',
          headers: {
            'accept': 'text/event-stream',
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${getToken()}`
          },
          body: JSON.stringify(requestData)
        });

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        // 创建助手消息ID用于新对话
        const assistantMessageId = `msg-${Date.now()}-assistant-${Math.random().toString(36).substr(2, 9)}`;

        // 处理新对话的流式响应
        await handleStreamResponse(response, assistantMessageId, false);

        return assistantMessageId;
      }

    } catch (error) {
      console.error("发送消息失败:", error);
      throw error;
    } finally {
      setIsLoading(false);
      setIsResuming(false);
    }
  }, [conversationId, threadId, isLoading, onThreadIdUpdate, onMessageUpdate, onNewMessage, getToken, renderCachedMessages, handleStreamResponse]);

  return {
    sendMessage,
    isLoading,
  };
};
import { useState, useCallback, useRef } from 'react';
import { processStreamData, StreamDataProcessor, Message, ProcessNode } from '@/utils/messageFormatter';

interface UseChatSubmissionOptions {
  conversationId: string;
  threadId: string;
  onThreadIdUpdate: (threadId: string) => void;
  onMessageUpdate: (message: Message) => void;
  onNewMessage: (message: Message) => void;
  onRemoveMessage: (messageId: string) => void;
  showNotification: (content: string) => void;
  getToken: () => string;
}

/**
 * 聊天提交逻辑Hook
 * 处理消息发送和流式响应
 */
export const useChatSubmission = ({
  conversationId,
  threadId,
  onThreadIdUpdate,
  onMessageUpdate,
  onNewMessage,
  onRemoveMessage,
  showNotification,
  getToken
}: UseChatSubmissionOptions) => {
  const [isLoading, setIsLoading] = useState(false);
  const lastUserMessageIdRef = useRef<string | null>(null);
  
  // 用于减少更新频率的refs
  const updateTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const pendingUpdateRef = useRef<Message | null>(null);

  /**
   * 处理流式响应的统一函数
   */
  const handleStreamResponse = useCallback(async (
    response: Response,
    assistantMessageId: string
  ): Promise<string> => {
    const reader = response.body?.getReader();
    const decoder = new TextDecoder();

    if (!reader) {
      throw new Error('无法获取响应流');
    }

    // 初始助手消息，暂不创建，等确认不是rlock类型后再创建

    // 流式数据状态
    let streamState = {
      textContentBuffer: '',
      processNodes: [] as ProcessNode[],
      processEdges: [] as { from: string; to: string }[],
      lastMessageType: null as 'text' | 'agent' | 'web' | 'excalidraw' | null,
      lastNodeName: null as string | null,
      webContentBuffer: '',
      excalidrawData: null,
    };

    // 标记是否已创建助手消息
    let initialAssistantMessageCreated = false;

    let hasWebContent = false;
    let webContent = '';

    // JSON缓冲区，用于处理跨多行的JSON数据
    let jsonBuffer = '';

    try {
      // 处理流式数据
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        const lines = chunk.split('\n');

        for (const line of lines) {
          // 跳过空行
          if (!line.trim()) {
            continue;
          }

          // 添加到缓冲区
          if (jsonBuffer) {
            jsonBuffer += '\n' + line; // 保留换行符
          } else {
            jsonBuffer = line;
          }

          let jsonStr = jsonBuffer.trim();
          if (jsonStr.startsWith('data:')) {
            jsonStr = jsonStr.substring(5).trim();
          }

          if (!jsonStr) {
            continue;
          }

          // 尝试解析JSON
          try {
            console.log('尝试解析JSON:', jsonStr);
            const data = JSON.parse(jsonStr);

            // 成功解析后清空缓冲区
            jsonBuffer = '';

            // 检查是否为rlock类型
              if (data.messageType === 'rlock') {
                // 使用更优雅的提示方式
                showNotification(data.content || '正在对话中，请刷新页面');
                
                // 移除刚才添加的用户消息
                if (lastUserMessageIdRef.current) {
                  onRemoveMessage(lastUserMessageIdRef.current);
                  lastUserMessageIdRef.current = null;
                }
                
                // 取消读取流
                await reader.cancel();
                return 'rlock-handled';
              }
              
              // 如果不是rlock类型，且还没有创建助手消息，则创建
              if (!initialAssistantMessageCreated) {
                console.log('创建初始助手消息，ID:', assistantMessageId);
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
                initialAssistantMessageCreated = true;
              }

            // 更新threadId
            if (data.threadId) {
              onThreadIdUpdate(data.threadId);
            }

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
              excalidrawData: null,
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
                console.log('更新助手消息，ID:', assistantMessageId, '节点数:', assistantMessage.processFlow?.nodes?.length);
                onMessageUpdate(pendingUpdateRef.current);
                pendingUpdateRef.current = null;
              }
            }, 50); // 50ms节流延迟

          } catch (parseError) {
            console.error("JSON解析失败，尝试累积更多数据:", parseError);
            console.error("当前缓冲区内容:", jsonBuffer);

            // 如果解析失败，可能是JSON不完整，继续累积下一行的数据
            // 但如果缓冲区太大，可能是格式错误，需要清空
            if (jsonBuffer.length > 10000) {
              console.error("缓冲区过大，清空重置");
              jsonBuffer = '';
            }
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
      // 清理节流相关
      if (updateTimeoutRef.current) {
        clearTimeout(updateTimeoutRef.current);
        updateTimeoutRef.current = null;
      }
      pendingUpdateRef.current = null;
    }
  }, [onThreadIdUpdate, onMessageUpdate, onNewMessage]);

  const sendMessage = useCallback(async (input: string, agentId?: string) => {
    if (!input.trim() || isLoading) return null;

    try {
      setIsLoading(true);

      // 创建用户消息
      const userMessage: Message = {
        id: `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        content: input,
        role: 'user',
        createdAt: new Date()
      };

      // 保存用户消息ID，用于在需要时移除
      lastUserMessageIdRef.current = userMessage.id;
      
      // 发送用户消息到父组件
      onNewMessage(userMessage);

      // 准备请求数据
      const requestData = {
        query: input.trim(),
        conversationId,
        threadId,
        agentId: agentId || ''
      };

      console.log('发送对话请求:', requestData);

      // 调用API端点
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

      // 创建助手消息ID
      const assistantMessageId = `msg-${Date.now()}-assistant-${Math.random().toString(36).substr(2, 9)}`;

      // 处理流式响应
      const result = await handleStreamResponse(response, assistantMessageId);
      
      // 如果是rlock类型，不会创建AI回复，返回null
      if (result === 'rlock-handled') {
        return null;
      }

      return assistantMessageId;
    } catch (error) {
      console.error("发送消息失败:", error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, [conversationId, threadId, isLoading, onThreadIdUpdate, onMessageUpdate, onNewMessage, onRemoveMessage, showNotification, getToken, handleStreamResponse]);

  /**
   * 续读正在输出的流
   * @param messageId 正在输出的消息ID
   * @param seqNum 序列号，默认为0
   */
  const continueReading = useCallback(async (messageId: string, seqNum: number = 0) => {
    if (isLoading) return null;

    try {
      setIsLoading(true);

      // 准备请求数据
      const requestData = {
        conversationId,
        threadId,
        messageId,
        seqNum
      };

      console.log('续读对话请求:', requestData);

      // 调用API端点
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

      console.log('开始续读流数据，消息ID:', messageId);

      // 处理流式响应
      const result = await handleStreamResponse(response, messageId);

      // 如果是rlock类型，不会创建AI回复，返回null
      if (result === 'rlock-handled') {
        return null;
      }

      return messageId;
    } catch (error) {
      console.error("续读消息失败:", error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, [conversationId, threadId, isLoading, onThreadIdUpdate, onMessageUpdate, onNewMessage, onRemoveMessage, showNotification, getToken, handleStreamResponse]);

  return {
    sendMessage,
    continueReading,
    isLoading,
  };
};
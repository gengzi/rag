import { useState, useCallback } from 'react';
import { processStreamData, StreamDataProcessor, Message, ProcessNode } from '@/utils/messageFormatter';

interface UseChatSubmissionOptions {
  conversationId: string;
  threadId: string;
  onThreadIdUpdate: (threadId: string) => void;
  onMessageUpdate: (message: Message) => void;
  onNewMessage: (message: Message) => void;
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
  getToken
}: UseChatSubmissionOptions) => {
  const [isLoading, setIsLoading] = useState(false);

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

      // 发送用户消息到父组件
      onNewMessage(userMessage);

      // 准备请求数据
      const requestData = {
        query: input.trim(),
        conversationId,
        threadId,
        agentId: agentId || ''
      };

      // 发送请求
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

      // 处理流式响应
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();

      if (!reader) {
        throw new Error('无法获取响应流');
      }

      // 创建助手消息
      const assistantMessageId = `msg-${Date.now()}-assistant-${Math.random().toString(36).substr(2, 9)}`;

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

      // 流式数据状态
      let streamState = {
        textContentBuffer: '',
        processNodes: [] as ProcessNode[],
        processEdges: [] as { from: string; to: string }[],
        lastMessageType: null as 'text' | 'agent' | 'web' | null,
        lastNodeName: null as string | null,
      };

      let hasWebContent = false;
      let webContent = '';

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
            };

            if (result.hasWebContent) {
              hasWebContent = true;
              webContent = result.webContent;
            }

            // 更新助手消息
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

            onMessageUpdate(assistantMessage);

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

      onMessageUpdate(finalAssistantMessage);

      return assistantMessageId;

    } catch (error) {
      console.error("发送消息失败:", error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, [conversationId, threadId, isLoading, onThreadIdUpdate, onMessageUpdate, onNewMessage, getToken]);

  return {
    sendMessage,
    isLoading,
  };
};
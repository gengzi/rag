/**
 * 消息处理相关的类型定义
 */
interface ProcessNode {
  type: 'agent' | 'llm' | 'web';
  id: string;
  name: string;
  status: 'active' | 'completed' | 'pending';
  content?: string;
  description?: string;
  reference?: any[];
  order?: number;
  displayTitle?: string;
  threadId?: string;
}

interface ProcessFlow {
  nodes: ProcessNode[];
  edges: Array<{ from: string; to: string }>;
}

interface WebContent {
  messageType: 'web';
  content: string;
  nodeName?: string;
}

interface Message {
  id: string;
  content: string;
  role: 'user' | 'assistant';
  createdAt: Date;
  citations?: any[];
  ragReference?: any;
  processFlow?: ProcessFlow;
  isAgent?: boolean;
  webContent?: WebContent;
}

interface Citation {
  id: number;
  text: string;
  metadata: Record<string, any>;
}

/**
 * 消息处理相关的工具函数
 * 将复杂的消息格式化逻辑从主组件中提取出来，提高代码可维护性
 */

/**
 * 安全过滤HTML、JavaScript和CSS内容
 */
export const sanitizeWebContent = (content: string): string => {
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
};

/**
 * 解析API响应中的消息数据
 */
export const parseMessagesFromAPI = (data: any): Message[] => {
  let messagesToProcess: any[] = [];

  // 兼容多种API响应格式
  if (Array.isArray(data.message)) {
    messagesToProcess = data.message;
  } else if (data.data && Array.isArray(data.data.message)) {
    messagesToProcess = data.data.message;
  } else if (data.code === 200 && data.data && Array.isArray(data.data.message)) {
    messagesToProcess = data.data.message;
  } else {
    console.error('API返回数据格式错误，无法解析消息:', data);
    return [];
  }

  const formattedMessages: Message[] = [];
  let latestThreadId = '';

  messagesToProcess.forEach((msg: any) => {
    if (msg.content && Array.isArray(msg.content)) {
      // 处理复杂消息（包含多个contentItem）
      const processFlow = createProcessFlow(msg.content);

      // 提取threadId
      msg.content.forEach((contentItem: any) => {
        if (contentItem.threadId !== undefined) {
          latestThreadId = contentItem.threadId || '';
        }
      });

      // 获取第一条文本内容作为消息主内容
      const firstTextContent = msg.content.find((item: any) => item.messageType === 'text');
      const messageContent = firstTextContent?.content?.answer || '';
      const ragReference = firstTextContent?.content?.reference;

      // 检查是否有web内容
      const webContentForMessage = extractWebContent(msg.content);

      formattedMessages.push({
        id: msg.id || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        content: typeof messageContent === 'string' ? messageContent : JSON.stringify(messageContent),
        role: msg.role === 'USER' ? 'user' : 'assistant',
        createdAt: msg.createdAt ? new Date(msg.createdAt) : new Date(),
        citations: firstTextContent?.content?.reference?.reference || [],
        ragReference: ragReference,
        processFlow: processFlow,
        isAgent: msg.content.some((item: any) => item.messageType === 'agent'),
        webContent: webContentForMessage
      });
    } else if (msg.content && typeof msg.content === 'string') {
      // 处理简单文本消息
      formattedMessages.push({
        id: msg.id || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        content: msg.content,
        role: msg.role === 'USER' ? 'user' : 'assistant',
        createdAt: msg.createdAt ? new Date(msg.createdAt) : new Date(),
        citations: [],
        ragReference: undefined,
        processFlow: undefined,
        isAgent: false
      });
    }
  });

  return formattedMessages;
};

/**
 * 从content数组中创建ProcessFlow
 */
export const createProcessFlow = (contentArray: any[]): ProcessFlow => {
  const processNodes: ProcessNode[] = [];
  const processEdges: Array<{ from: string; to: string }> = [];

  contentArray.forEach((contentItem: any, index: number) => {
    if (contentItem.messageType === 'text') {
      const answer = contentItem.content?.answer || '';
      const messageContent = typeof answer === 'string' ? answer : JSON.stringify(answer);
      const references = contentItem.content?.reference?.reference || [];

      const llmNode: ProcessNode = {
        type: 'llm',
        id: `llm-${index}`,
        name: '回答生成',
        status: 'completed',
        content: messageContent,
        reference: references,
        order: index
      };

      processNodes.push(llmNode);
    } else if (contentItem.messageType === 'agent') {
      const agentData = contentItem.content || {};
      const nodeName = agentData.nodeName || `agent-${index}`;
      const displayTitle = agentData.displayTitle || nodeName;
      const nodeContent = agentData.content || '';
      const references = agentData.reference?.reference || [];

      const agentNode: ProcessNode = {
        type: 'agent',
        id: nodeName,
        name: nodeName,
        displayTitle: displayTitle,
        status: 'completed',
        content: typeof nodeContent === 'string' ? nodeContent : JSON.stringify(nodeContent),
        description: typeof nodeContent === 'string' ? nodeContent : JSON.stringify(nodeContent),
        reference: references,
        order: index
      };

      processNodes.push(agentNode);
    } else if (contentItem.messageType === 'web') {
      const webData = contentItem.content || {};
      const nodeName = webData.nodeName || `web-${index}`;
      const webContent = webData.content || '';

      const webNode: ProcessNode = {
        type: 'web',
        id: nodeName,
        name: nodeName,
        status: 'completed',
        content: typeof webContent === 'string' ? webContent : JSON.stringify(webContent),
        order: index
      };

      processNodes.push(webNode);
    }
  });

  // 构建边的关系
  for (let i = 1; i < processNodes.length; i++) {
    processEdges.push({
      from: processNodes[i-1].id,
      to: processNodes[i].id
    });
  }

  return {
    nodes: processNodes,
    edges: processEdges
  };
};

/**
 * 从content数组中提取Web内容
 */
export const extractWebContent = (contentArray: any[]): WebContent | undefined => {
  const webContentItem = contentArray.find((item: any) => item.messageType === 'web');

  if (!webContentItem) return undefined;

  if (typeof webContentItem.content === 'object' && webContentItem.content !== null) {
    return {
      messageType: 'web',
      content: webContentItem.content.content || JSON.stringify(webContentItem.content),
      nodeName: webContentItem.content.nodeName || webContentItem.nodeName || 'web-content'
    };
  } else if (typeof webContentItem.content === 'string') {
    return {
      messageType: 'web',
      content: webContentItem.content,
      nodeName: webContentItem.nodeName || 'web-content'
    };
  }

  return undefined;
};

/**
 * 处理流式数据更新
 */
export interface StreamDataProcessor {
  messageId: string;
  messageType: 'text' | 'agent' | 'web';
  content: any;
  threadId?: string;
}

export const processStreamData = (
  data: StreamDataProcessor,
  currentState: {
    textContentBuffer: string;
    processNodes: ProcessNode[];
    processEdges: Array<{ from: string; to: string }>;
    lastMessageType: 'text' | 'agent' | 'web' | null;
    lastNodeName: string | null;
  }
): {
  updatedTextContentBuffer: string;
  updatedProcessNodes: ProcessNode[];
  updatedProcessEdges: Array<{ from: string; to: string }>;
  updatedLastMessageType: 'text' | 'agent' | 'web' | null;
  updatedLastNodeName: string | null;
  hasWebContent: boolean;
  webContent: string;
} => {
  const { textContentBuffer, processNodes, processEdges, lastMessageType, lastNodeName } = currentState;
  let updatedTextContentBuffer = textContentBuffer;
  let updatedProcessNodes = [...processNodes];
  let updatedProcessEdges = [...processEdges];
  let updatedLastMessageType = lastMessageType;
  let updatedLastNodeName = lastNodeName;
  let hasWebContent = false;
  let webContent = '';

  switch (data.messageType) {
    case 'text':
      const textContent = data.content;
      const newContent = textContent.answer || '';

      updatedTextContentBuffer += newContent;

      if (lastMessageType === 'text') {
        // 更新现有LLM节点
        const existingLlmNodeIndex = processNodes.findIndex(node => node.type === 'llm');
        if (existingLlmNodeIndex >= 0) {
          updatedProcessNodes = updatedProcessNodes.map((node, index) =>
            index === existingLlmNodeIndex
              ? { ...node, content: updatedTextContentBuffer, reference: textContent.reference?.reference || [] }
              : node
          );
        }
      } else {
        // 创建新的LLM节点
        if (updatedProcessNodes.length > 0) {
          updatedProcessNodes = updatedProcessNodes.map(node => ({
            ...node,
            status: 'completed' as const
          }));
        }

        const llmNode: ProcessNode = {
          type: 'llm',
          id: 'llm-output',
          name: '回答生成',
          status: 'active',
          content: updatedTextContentBuffer,
          reference: textContent.reference?.reference || [],
          order: updatedProcessNodes.length
        };

        updatedProcessNodes.push(llmNode);

        if (updatedProcessNodes.length > 1) {
          const lastNodeIndex = updatedProcessNodes.length - 1;
          updatedProcessEdges.push({
            from: updatedProcessNodes[lastNodeIndex - 1].id,
            to: updatedProcessNodes[lastNodeIndex].id
          });
        }
      }

      updatedLastMessageType = 'text';
      break;

    case 'web':
      updatedTextContentBuffer = '';
      const webData = data.content || {};
      const webContentValue = webData.content || webData.web内容 || '';
      const nodeName = webData.nodeName || 'web-output';

      const sanitizedWebContent = sanitizeWebContent(webContentValue);

      if (updatedProcessNodes.length > 0) {
        updatedProcessNodes = updatedProcessNodes.map(node => ({
          ...node,
          status: 'completed' as const
        }));
      }

      const webNode: ProcessNode = {
        type: 'web',
        id: nodeName,
        name: nodeName,
        displayTitle: '网页内容',
        status: 'active',
        content: sanitizedWebContent,
        order: updatedProcessNodes.length
      };

      updatedProcessNodes.push(webNode);

      if (updatedProcessNodes.length > 1) {
        const lastNodeIndex = updatedProcessNodes.length - 1;
        updatedProcessEdges.push({
          from: updatedProcessNodes[lastNodeIndex - 1].id,
          to: updatedProcessNodes[lastNodeIndex].id
        });
      }

      updatedLastMessageType = 'web';
      hasWebContent = true;
      webContent = sanitizedWebContent;
      break;

    case 'agent':
      updatedTextContentBuffer = '';
      const agentContent = data.content;
      const agentNodeName = agentContent.nodeName;
      const nodeDescription = agentContent.content || '';

      const existingNodeIndex = processNodes.findIndex(node =>
        node.type === 'agent' && node.id === agentNodeName
      );

      if (existingNodeIndex >= 0 &&
          lastMessageType === 'agent' &&
          lastNodeName === agentNodeName) {
        // 追加到现有agent节点
        const existingNode = processNodes[existingNodeIndex];
        const updatedDescription = (existingNode.description || '') + nodeDescription;

        updatedProcessNodes = updatedProcessNodes.map((node, index) =>
          index === existingNodeIndex
            ? { ...node, description: updatedDescription }
            : node
        );
      } else {
        // 创建新的agent节点
        if (updatedProcessNodes.length > 0) {
          updatedProcessNodes = updatedProcessNodes.map(node => ({
            ...node,
            status: 'completed' as const
          }));
        }

        const newNode: ProcessNode = {
          type: 'agent',
          id: agentNodeName,
          name: agentNodeName,
          displayTitle: agentContent.displayTitle || agentNodeName,
          status: 'active',
          description: nodeDescription,
          reference: agentContent.reference?.reference || [],
          order: updatedProcessNodes.length
        };

        updatedProcessNodes.push(newNode);

        if (updatedProcessNodes.length > 1) {
          const lastNodeIndex = updatedProcessNodes.length - 1;
          updatedProcessEdges.push({
            from: updatedProcessNodes[lastNodeIndex - 1].id,
            to: updatedProcessNodes[lastNodeIndex].id
          });
        }
      }

      updatedLastMessageType = 'agent';
      updatedLastNodeName = agentNodeName;
      break;
  }

  return {
    updatedTextContentBuffer,
    updatedProcessNodes,
    updatedProcessEdges,
    updatedLastMessageType,
    updatedLastNodeName,
    hasWebContent,
    webContent
  };
};

// 导出类型供其他模块使用
export type { ProcessNode, ProcessFlow, Message, WebContent, Citation };

export interface StreamDataProcessor {
  messageId: string;
  messageType: 'text' | 'agent' | 'web';
  content: any;
  threadId?: string;
}
/**
 * 消息处理相关的类型定义
 */
interface ProcessNode {
  type: 'agent' | 'llm' | 'web' | 'excalidraw';
  id: string;
  name: string;
  status: 'active' | 'completed' | 'pending';
  content?: string;
  description?: string;
  reference?: any[];
  order?: number;
  displayTitle?: string;
  threadId?: string;
  data?: any; // 用于存储Excalidraw JSON数据
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

interface ExcalidrawContent {
  messageType: 'excalidraw';
  data: any; // Excalidraw JSON格式数据
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
  excalidrawContent?: ExcalidrawContent;
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
    console.log('处理消息:', {
      id: msg.id,
      role: msg.role,
      hasContentArray: Array.isArray(msg.content),
      contentLength: msg.content?.length
    }); // 调试日志

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
      
      // 检查是否有excalidraw内容
      const excalidrawContentForMessage = extractExcalidrawContent(msg.content);

      // 改进角色判断逻辑，支持多种可能的格式
      let messageRole = 'assistant'; // 默认为assistant
      if (msg.role === 'USER' || msg.role === 'user' || msg.role === 1) {
        messageRole = 'user';
      }

      console.log('消息角色判断:', {
        originalRole: msg.role,
        finalRole: messageRole,
        isUser: messageRole === 'user',
        roleType: typeof msg.role
      }); // 调试日志

      formattedMessages.push({
        id: msg.id || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        content: typeof messageContent === 'string' ? messageContent : JSON.stringify(messageContent),
        role: messageRole as 'user' | 'assistant',
        createdAt: msg.createdAt ? new Date(msg.createdAt) : new Date(),
        citations: firstTextContent?.content?.reference?.reference || [],
        ragReference: ragReference,
        processFlow: processFlow,
        isAgent: msg.content.some((item: any) => item.messageType === 'agent'),
        webContent: webContentForMessage,
        excalidrawContent: excalidrawContentForMessage
      });
    } else if (msg.content && typeof msg.content === 'string') {
      // 改进角色判断逻辑，支持多种可能的格式
      let messageRole = 'assistant'; // 默认为assistant
      if (msg.role === 'USER' || msg.role === 'user' || msg.role === 1) {
        messageRole = 'user';
      }

      console.log('简单文本消息角色判断:', {
        originalRole: msg.role,
        finalRole: messageRole,
        isUser: messageRole === 'user',
        roleType: typeof msg.role,
        content: msg.content.substring(0, 50) + '...'
      }); // 调试日志

      formattedMessages.push({
        id: msg.id || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        content: msg.content,
        role: messageRole as 'user' | 'assistant',
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
    } else if (contentItem.messageType === 'excalidraw') {
      const excalidrawData = contentItem.content || {};
      const nodeName = excalidrawData.nodeName || `excalidraw-${index}`;
      const displayTitle = excalidrawData.displayTitle || '绘图内容';
      const nodeData = excalidrawData.data || {};

      const excalidrawNode: ProcessNode = {
        type: 'excalidraw',
        id: nodeName,
        name: nodeName,
        displayTitle: displayTitle,
        status: 'completed',
        data: nodeData,
        order: index
      };

      processNodes.push(excalidrawNode);
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
 * 从content数组中提取Excalidraw内容
 */
export const extractExcalidrawContent = (contentArray: any[]): ExcalidrawContent | undefined => {
  const excalidrawContentItem = contentArray.find((item: any) => item.messageType === 'excalidraw');

  if (!excalidrawContentItem) return undefined;

  if (typeof excalidrawContentItem.content === 'object' && excalidrawContentItem.content !== null) {
    return {
      messageType: 'excalidraw',
      data: excalidrawContentItem.content.data || excalidrawContentItem.content,
      nodeName: excalidrawContentItem.content.nodeName || excalidrawContentItem.nodeName || 'excalidraw-content'
    };
  } else if (typeof excalidrawContentItem.content === 'object') {
    return {
      messageType: 'excalidraw',
      data: excalidrawContentItem.content,
      nodeName: excalidrawContentItem.nodeName || 'excalidraw-content'
    };
  }

  return undefined;
};

/**
 * 处理流式数据更新
 */
export interface StreamDataProcessor {
  messageId: string;
  messageType: 'text' | 'agent' | 'web' | 'excalidraw';
  content: any;
  threadId?: string;
}

export const processStreamData = (
  data: StreamDataProcessor,
  currentState: {
    textContentBuffer: string;
    processNodes: ProcessNode[];
    processEdges: Array<{ from: string; to: string }>;
    lastMessageType: 'text' | 'agent' | 'web' | 'excalidraw' | null;
    lastNodeName: string | null;
    webContentBuffer: string; // web内容缓冲区
    excalidrawData: any; // Excalidraw数据缓冲区
  }
): {
  updatedTextContentBuffer: string;
  updatedProcessNodes: ProcessNode[];
  updatedProcessEdges: Array<{ from: string; to: string }>;
  updatedLastMessageType: 'text' | 'agent' | 'web' | 'excalidraw' | null;
  updatedLastNodeName: string | null;
  hasWebContent: boolean;
  webContent: string;
  hasExcalidrawContent: boolean;
  excalidrawContent: any;
  updatedWebContentBuffer: string;
  updatedExcalidrawData: any;
} => {
  const { textContentBuffer, processNodes, processEdges, lastMessageType, lastNodeName, webContentBuffer, excalidrawData } = currentState;
  let updatedTextContentBuffer = textContentBuffer;
  let updatedProcessNodes = [...processNodes];
  let updatedProcessEdges = [...processEdges];
  let updatedLastMessageType = lastMessageType;
  let updatedLastNodeName = lastNodeName;
  let updatedWebContentBuffer = webContentBuffer;
  let updatedExcalidrawData = excalidrawData;
  let hasWebContent = false;
  let webContent = '';
  let hasExcalidrawContent = false;
  let excalidrawContent = {};

  switch (data.messageType) {
    case 'text':
      const textContent = data.content;
      const newContent = textContent.answer || '';

      updatedTextContentBuffer += newContent;
      updatedWebContentBuffer = ''; // 切换到文本模式时清空web缓冲区

      const existingLlmNodeIndex = processNodes.findIndex(node => node.type === 'llm');

      if (existingLlmNodeIndex >= 0) {
        // 更新现有LLM节点，只拼接内容，不重新创建
        updatedProcessNodes = updatedProcessNodes.map((node, index) =>
          index === existingLlmNodeIndex
            ? { ...node, content: updatedTextContentBuffer, reference: textContent.reference?.reference || [] }
            : node
        );
      } else {
        // 创建新的LLM节点（仅在第一次时）
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

      // 累积web内容
      updatedWebContentBuffer += sanitizedWebContent;

      // 查找是否已存在相同ID的web节点
      const existingWebNodeIndex = processNodes.findIndex(node =>
        node.type === 'web' && node.id === nodeName
      );

      if (existingWebNodeIndex >= 0) {
        // 更新现有web节点的内容
        updatedProcessNodes = updatedProcessNodes.map((node, index) =>
          index === existingWebNodeIndex
            ? { ...node, content: updatedWebContentBuffer }
            : node
        );
      } else {
        // 创建新的web节点（仅在第一次时）
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
          content: updatedWebContentBuffer,
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
      }

      updatedLastMessageType = 'web';
      hasWebContent = true;
      webContent = updatedWebContentBuffer;
      break;
      
    case 'excalidraw':
      updatedTextContentBuffer = '';
      updatedWebContentBuffer = ''; // 切换到excalidraw模式时清空web缓冲区
      const excalidrawContentData = data.content || {};
      const excalidrawNodeName = excalidrawContentData.nodeName || 'excalidraw-output';
      const nodeExcalidrawData = excalidrawContentData.data || excalidrawContentData;
      
      // 累积excalidraw数据
      updatedExcalidrawData = nodeExcalidrawData;
      
      // 查找是否已存在相同ID的excalidraw节点
      const existingExcalidrawNodeIndex = processNodes.findIndex(node =>
        node.type === 'excalidraw' && node.id === excalidrawNodeName
      );
      
      if (existingExcalidrawNodeIndex >= 0) {
        // 更新现有excalidraw节点的内容
        updatedProcessNodes = updatedProcessNodes.map((node, index) =>
          index === existingExcalidrawNodeIndex
            ? { ...node, data: updatedExcalidrawData }
            : node
        );
      } else {
        // 创建新的excalidraw节点（仅在第一次时）
        if (updatedProcessNodes.length > 0) {
          updatedProcessNodes = updatedProcessNodes.map(node => ({
            ...node,
            status: 'completed' as const
          }));
        }
        
        const excalidrawNode: ProcessNode = {
          type: 'excalidraw',
          id: excalidrawNodeName,
          name: excalidrawNodeName,
          displayTitle: '绘图内容',
          status: 'active',
          data: updatedExcalidrawData,
          order: updatedProcessNodes.length
        };
        
        updatedProcessNodes.push(excalidrawNode);
        
        if (updatedProcessNodes.length > 1) {
          const lastNodeIndex = updatedProcessNodes.length - 1;
          updatedProcessEdges.push({
            from: updatedProcessNodes[lastNodeIndex - 1].id,
            to: updatedProcessNodes[lastNodeIndex].id
          });
        }
      }
      
      updatedLastMessageType = 'excalidraw';
      hasExcalidrawContent = true;
      excalidrawContent = updatedExcalidrawData;
      break;

    case 'agent':
      updatedTextContentBuffer = '';
      updatedWebContentBuffer = ''; // 切换到agent模式时清空web缓冲区
      const agentContent = data.content;
      const agentNodeName = agentContent.nodeName;
      const nodeDescription = agentContent.content || '';

      const existingNodeIndex = processNodes.findIndex(node =>
        node.type === 'agent' && node.id === agentNodeName
      );

      if (existingNodeIndex >= 0) {
        // 追加到现有agent节点，避免重复创建
        const existingNode = processNodes[existingNodeIndex];
        const updatedDescription = (existingNode.description || '') + nodeDescription;

        updatedProcessNodes = updatedProcessNodes.map((node, index) =>
          index === existingNodeIndex
            ? { ...node, description: updatedDescription }
            : node
        );
      } else {
        // 创建新的agent节点（仅在第一次时）
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
    webContent,
    hasExcalidrawContent,
    excalidrawContent,
    updatedWebContentBuffer,
    updatedExcalidrawData
  };
  }

  // 导出类型供其他模块使用
export type { ProcessNode, ProcessFlow, Message, WebContent, ExcalidrawContent, Citation };
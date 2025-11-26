import { api } from '../api';
import API_CONFIG from '../config';

export interface ChatHistoryParams {
  id: string;
  limit?: number;
  before?: string;
}



export interface DeleteChatParams {
  id: string;
}

export interface Chat {
  id: string;
  name: string;
  createDate: string;
  knowledgebaseId: string;
  dialogId: string;
}

export interface ChatHistoryResponse {
  message: any[];
  before?: string;
  runMessageId?: string; // 用于标识当前对话正在输出的消息id
  data?: {
    message: any[];
  };
  code?: number;
}

const CHAT_API_BASE_URL = API_CONFIG.BASE_URL;

// Chat History API (v2)
export const getChatHistory = async (params: ChatHistoryParams): Promise<ChatHistoryResponse> => {
  const { id, limit = 50, before = '' } = params;
  // Using the v2 API endpoint with different base URL
  return api.post(`http://localhost:8086/chat/msg/${id}/list`, {
    limit,
    before
  });
};

// Get all chats
export const getAllChats = async (): Promise<Chat[]> => {
  return api.get('/chat/rag/all');
};

// Delete chat
export const deleteChat = async (params: DeleteChatParams): Promise<void> => {
  const { id } = params;
  return api.delete(`/chat/rag/delete/${id}`);
};

// Create chat
export const createChat = async (params: { chatName?: string; kbId: string }): Promise<string> => {
  return api.post('/chat/rag/create', params);
};

// Get knowledge base
export const getKnowledgeBase = async (): Promise<any> => {
  return api.get('/api/knowledge-base');
};

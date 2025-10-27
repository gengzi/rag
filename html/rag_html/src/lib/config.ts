// API配置
const API_CONFIG = {
  // 后端API基础URL
  BASE_URL: 'http://localhost:8883',
  
  // API路径前缀
  API_PREFIX: '/api',
  
  // 认证相关配置
  AUTH: {
    // token存储的localStorage键名
    TOKEN_KEY: 'token',
    
    // 认证头部前缀
    AUTH_HEADER_PREFIX: 'Bearer',
    
    // 未授权时的重定向URL
    UNAUTHORIZED_REDIRECT_URL: '/login'
  },
  
  // 常用API端点
  ENDPOINTS: {
    // 文档相关API
    DOCUMENT: {
      // 文档嵌入API
      EMBEDDING: '/document/embedding',
      
      // 知识库文档API
      KNOWLEDGE_BASE_DOCUMENTS: '/knowledge-base/documents',
      
      // 文档搜索API
      SEARCH: '/document/search'
    }
  },
  
  // 请求超时设置（毫秒）
  TIMEOUT: 60000,
  
  // 默认请求头
  DEFAULT_HEADERS: {
    'accept': '*/*'
  }
};

export default API_CONFIG;
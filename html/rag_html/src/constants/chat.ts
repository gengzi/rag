/**
 * 聊天相关配置常量
 * 将硬编码值集中管理，便于维护和修改
 */

/**
 * 聊天界面配置
 */
export const CHAT_UI_CONFIG = {
  // 消息相关
  MESSAGE_LIMIT: 50, // 每次加载的消息数量
  MESSAGE_MAX_WIDTH: '85%', // 消息最大宽度

  // 动画和过渡
  SCROLL_DEBOUNCE: 100, // 滚动防抖时间（毫秒）
  ANIMATION_DURATION: 200, // 动画持续时间（毫秒）
  AUTO_SCROLL_DELAY: 100, // 自动滚动延迟（毫秒）

  // 输入框配置
  INPUT_PLACEHOLDER: '输入您的问题...',
  INPUT_HEIGHT: 'h-12', // 输入框高度

  // 响应式断点
  BREAKPOINTS: {
    MOBILE: 768,
    TABLET: 1024,
    DESKTOP: 1280,
  },

  // 标签配置
  TAG_HEIGHT: 'h-8', // 标签高度
  TAG_PADDING: 'px-3', // 标签内边距

  // 图标配置
  ICON_SIZE: {
    SMALL: 'h-4 w-4',
    MEDIUM: 'h-5 w-5',
    LARGE: 'h-10 w-10',
  },

  // 时间戳格式
  TIMESTAMP_FORMAT: {
    TIME_ONLY: 'toLocaleTimeString',
    DATE_ONLY: 'toLocaleDateString',
    FULL: 'toLocaleString',
  },
} as const;

/**
 * API配置常量
 */
export const CHAT_API_CONFIG = {
  // 端点配置
  ENDPOINTS: {
    STREAM_CHAT: 'http://localhost:8086/chat/stream/msg',
    UPLOAD_PPT: '/api/upload/ppt',
    DEEP_RESEARCH: '/api/research/deep',
  },

  // 超时配置
  TIMEOUTS: {
    CONNECTION: 10000, // 连接超时（毫秒）
    RESPONSE: 60000, // 响应超时（毫秒）
    HEARTBEAT: 30000, // 心跳间隔（毫秒）
  },

  // 重试配置
  RETRY: {
    MAX_ATTEMPTS: 3, // 最大重试次数
    DELAY: 1000, // 重试延迟（毫秒）
    BACKOFF_MULTIPLIER: 2, // 退避倍数
  },

  // 缓存配置
  CACHE: {
    TTL: 5 * 60 * 1000, // 缓存生存时间（5分钟）
    MAX_SIZE: 100, // 最大缓存条目数
    MAX_AGE: 10 * 60 * 1000, // 最大缓存时间（10分钟）
  },
} as const;

/**
 * 消息类型配置
 */
export const MESSAGE_TYPES = {
  USER: 'user',
  ASSISTANT: 'assistant',
  SYSTEM: 'system',

  // 内容类型
  CONTENT_TYPES: {
    TEXT: 'text',
    AGENT: 'agent',
    WEB: 'web',
    IMAGE: 'image',
    FILE: 'file',
  },

  // 状态类型
  STATUS: {
    PENDING: 'pending',
    ACTIVE: 'active',
    COMPLETED: 'completed',
    ERROR: 'error',
  },
} as const;

/**
 * 流程节点配置
 */
export const PROCESS_NODE_CONFIG = {
  // 默认节点配置
  DEFAULT_NODES: {
    LLM: {
      type: 'llm',
      name: '回答生成',
      id: 'llm-output',
    },
    WEB: {
      type: 'web',
      name: '网页内容',
      id: 'web-output',
    },
  },

  // 节点图标配置
  ICONS: {
    LLM: '🤖',
    AGENT: '🔧',
    WEB: '🌐',
    SEARCH: '🔍',
    ANALYZE: '📊',
  },

  // 节点颜色配置
  COLORS: {
    ACTIVE: 'text-green-600',
    COMPLETED: 'text-blue-600',
    PENDING: 'text-gray-400',
    ERROR: 'text-red-600',
  },
} as const;

/**
 * 安全配置
 */
export const SECURITY_CONFIG = {
  // HTML标签白名单
  ALLOWED_HTML_TAGS: [
    'p', 'br', 'strong', 'em', 'u', 'ol', 'ul', 'li',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'blockquote',
    'code', 'pre', 'a', 'span', 'div'
  ],

  // HTML属性白名单
  ALLOWED_ATTRIBUTES: [
    'href', 'target', 'rel', 'class', 'id', 'style'
  ],

  // 危险标签黑名单
  FORBIDDEN_TAGS: [
    'script', 'iframe', 'object', 'embed', 'form', 'input',
    'button', 'select', 'textarea', 'link', 'meta'
  ],

  // 危险属性黑名单
  FORBIDDEN_ATTRIBUTES: [
    'onload', 'onerror', 'onclick', 'onmouseover', 'onfocus',
    'onblur', 'onchange', 'onsubmit', 'javascript:', 'data:'
  ],

  // 内容长度限制
  MAX_CONTENT_LENGTH: 100000, // 最大内容长度（字符）
  MAX_WEB_CONTENT_LENGTH: 50000, // 最大Web内容长度
} as const;

/**
 * 本地存储键名
 */
export const STORAGE_KEYS = {
  TOKEN: 'token',
  USER_PREFERENCES: 'user_preferences',
  CHAT_HISTORY: 'chat_history',
  THEME: 'theme',
  LANGUAGE: 'language',
} as const;

/**
 * 错误消息配置
 */
export const ERROR_MESSAGES = {
  NETWORK_ERROR: '网络连接错误，请检查网络设置',
  SERVER_ERROR: '服务器错误，请稍后重试',
  AUTHENTICATION_ERROR: '身份验证失败，请重新登录',
  PERMISSION_ERROR: '权限不足，无法访问该资源',
  TIMEOUT_ERROR: '请求超时，请重试',
  PARSE_ERROR: '数据解析错误',
  UNKNOWN_ERROR: '未知错误，请联系技术支持',

  // 聊天特定错误
  CHAT_LOADING_ERROR: '加载聊天记录失败',
  MESSAGE_SEND_ERROR: '发送消息失败',
  STREAM_ERROR: '流式响应中断',
} as const;

/**
 * 成功消息配置
 */
export const SUCCESS_MESSAGES = {
  MESSAGE_SENT: '消息发送成功',
  FILE_UPLOADED: '文件上传成功',
  CHAT_SAVED: '对话保存成功',
  CHAT_DELETED: '对话删除成功',
} as const;

/**
 * 工具类型定义
 */
export type MessageType = typeof MESSAGE_TYPES.CONTENT_TYPES[keyof typeof MESSAGE_TYPES.CONTENT_TYPES];
export type MessageStatus = typeof MESSAGE_TYPES.STATUS[keyof typeof MESSAGE_TYPES.STATUS];
export type ProcessNodeStatus = typeof MESSAGE_TYPES.STATUS[keyof typeof MESSAGE_TYPES.STATUS];

/**
 * 默认配置导出
 */
export const DEFAULT_CONFIG = {
  CHAT_UI_CONFIG,
  CHAT_API_CONFIG,
  MESSAGE_TYPES,
  PROCESS_NODE_CONFIG,
  SECURITY_CONFIG,
  STORAGE_KEYS,
  ERROR_MESSAGES,
  SUCCESS_MESSAGES,
} as const;
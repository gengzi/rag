interface FetchOptions extends Omit<RequestInit, 'body' | 'headers'> {  
  data?: any;  
  headers?: Record<string, string>;  
  params?: Record<string, any>;  // 添加params参数支持
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

// 导入API配置
import API_CONFIG from './config';

export async function fetchApi(url: string, options: FetchOptions = {}) {  
  const { data, headers: customHeaders = {}, params, ...restOptions } = options;  // 解构出params

  // Get token from localStorage
  let token = '';
  if (typeof window !== 'undefined') {
    token = localStorage.getItem(API_CONFIG.AUTH.TOKEN_KEY) || '';
  }

  const headers: Record<string, string> = {
    ...(token && { Authorization: `${API_CONFIG.AUTH.AUTH_HEADER_PREFIX} ${token}` }),
    ...customHeaders,
  };

  // If no content type is specified and we have data, default to JSON
  if (!headers['Content-Type'] && data && !(data instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const config: RequestInit = {
    ...restOptions,
    headers,
  };

  // Handle body based on Content-Type
  if (data) {
    if (data instanceof FormData) {
      config.body = data;
    } else if (headers['Content-Type'] === 'application/json') {
      config.body = JSON.stringify(data);
    } else if (headers['Content-Type'] === 'application/x-www-form-urlencoded') {
      config.body = typeof data === 'string' ? data : new URLSearchParams(data).toString();
    } else {
      config.body = data;
    }
  }

  try {
    // 构建完整的API请求URL
    let fullUrl = url.startsWith('http') ? url : `${API_CONFIG.BASE_URL}${url}`;
    
    // 处理params参数，构建查询字符串
    if (params) {
      const queryParams = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          queryParams.append(key, String(value));
        }
      });
      const queryString = queryParams.toString();
      if (queryString) {
        fullUrl += (fullUrl.includes('?') ? '&' : '?') + queryString;
      }
    }
    
    console.log(`Making API request to: ${fullUrl}`);
    console.log(`Request headers:`, headers);
    
    const response = await fetch(fullUrl, config);
    
    console.log(`API response status: ${response.status}`);
    console.log(`API response headers:`, response.headers);

    // 401和403状态码都需要跳转到登录页
    if (response.status === 401 || response.status === 403) {
      if (typeof window !== 'undefined') {
        localStorage.removeItem(API_CONFIG.AUTH.TOKEN_KEY);
        window.location.href = API_CONFIG.AUTH.UNAUTHORIZED_REDIRECT_URL;
      }
      const errorMessage = response.status === 401 ? '未授权 - 请重新登录' : '权限不足 - 请重新登录';
      throw new ApiError(response.status, errorMessage);
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      console.error(`API request failed with status: ${response.status}`, errorData);
      throw new ApiError(
        response.status,
        errorData.message || errorData.detail || '发生错误'
      );
    }

    // 处理统一的响应格式
    const responseData = await response.json();
    
    // 检查后端统一的响应格式
    if ('code' in responseData && 'success' in responseData) {
      // 根据后端定义，code为200表示成功
      if (responseData.code !== 200 || !responseData.success) {
        throw new ApiError(
          responseData.code || 400,
          responseData.message || 'API请求失败'
        );
      }
      // 返回data字段的内容
      return responseData.data;
    }
    
    // 如果不是统一格式，则直接返回原始响应
    return responseData;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(500, '网络错误或服务器不可达');
  }
}

// Helper methods for common HTTP methods
export const api = {
  get: (url: string, options?: Omit<FetchOptions, 'method'>) =>
    fetchApi(url, { ...options, method: 'GET' }),

  post: (url: string, data?: any, options?: Omit<FetchOptions, 'method'>) =>
    fetchApi(url, { ...options, method: 'POST', data }),

  put: (url: string, data?: any, options?: Omit<FetchOptions, 'method'>) =>
    fetchApi(url, { ...options, method: 'PUT', data }),

  delete: (url: string, options?: Omit<FetchOptions, 'method'>) =>
    fetchApi(url, { ...options, method: 'DELETE' }),

  patch: (url: string, data?: any, options?: Omit<FetchOptions, 'method'>) =>
    fetchApi(url, { ...options, method: 'PATCH', data }),
};

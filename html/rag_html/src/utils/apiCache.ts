/**
 * API缓存工具
 * 提供智能缓存机制，减少重复的网络请求
 */

interface CacheItem<T> {
  data: T;
  timestamp: number;
  expiresAt: number;
}

interface CacheConfig {
  ttl?: number; // 生存时间（毫秒）
  maxSize?: number; // 最大缓存条目数
  enableMemoryCache?: boolean; // 是否启用内存缓存
}

class APICache {
  private cache = new Map<string, CacheItem<any>>();
  private config: Required<CacheConfig>;

  constructor(config: CacheConfig = {}) {
    this.config = {
      ttl: config.ttl || 5 * 60 * 1000, // 默认5分钟
      maxSize: config.maxSize || 100, // 默认最多100个缓存项
      enableMemoryCache: config.enableMemoryCache !== false, // 默认启用
    };

    // 定期清理过期缓存
    if (typeof window !== 'undefined') {
      setInterval(() => this.clearExpiredItems(), 60 * 1000); // 每分钟清理一次
    }
  }

  /**
   * 生成缓存键
   */
  private generateKey(url: string, options?: RequestInit): string {
    const method = options?.method || 'GET';
    const body = options?.body ? JSON.stringify(options.body) : '';
    const headers = options?.headers ? JSON.stringify(options.headers) : '';

    return `${method}:${url}:${body}:${headers}`;
  }

  /**
   * 检查缓存项是否有效
   */
  private isItemValid(item: CacheItem<any>): boolean {
    return Date.now() < item.expiresAt;
  }

  /**
   * 清理过期缓存项
   */
  private clearExpiredItems(): void {
    const now = Date.now();
    for (const [key, item] of this.cache.entries()) {
      if (now >= item.expiresAt) {
        this.cache.delete(key);
      }
    }
  }

  /**
   * 维护缓存大小
   */
  private maintainCacheSize(): void {
    if (this.cache.size <= this.config.maxSize) {
      return;
    }

    // 删除最旧的缓存项（LRU策略）
    const entries = Array.from(this.cache.entries())
      .sort(([, a], [, b]) => a.timestamp - b.timestamp);

    const toDelete = entries.slice(0, this.cache.size - this.config.maxSize);
    toDelete.forEach(([key]) => this.cache.delete(key));
  }

  /**
   * 获取缓存数据
   */
  get<T>(url: string, options?: RequestInit): T | null {
    if (!this.config.enableMemoryCache) {
      return null;
    }

    const key = this.generateKey(url, options);
    const item = this.cache.get(key);

    if (!item) {
      return null;
    }

    if (!this.isItemValid(item)) {
      this.cache.delete(key);
      return null;
    }

    // 更新访问时间（用于LRU）
    item.timestamp = Date.now();
    return item.data as T;
  }

  /**
   * 设置缓存数据
   */
  set<T>(url: string, data: T, options?: RequestInit, customTtl?: number): void {
    if (!this.config.enableMemoryCache) {
      return;
    }

    const key = this.generateKey(url, options);
    const now = Date.now();
    const ttl = customTtl || this.config.ttl;

    const item: CacheItem<T> = {
      data,
      timestamp: now,
      expiresAt: now + ttl,
    };

    this.cache.set(key, item);
    this.maintainCacheSize();
  }

  /**
   * 删除特定缓存项
   */
  delete(url: string, options?: RequestInit): boolean {
    const key = this.generateKey(url, options);
    return this.cache.delete(key);
  }

  /**
   * 清空所有缓存
   */
  clear(): void {
    this.cache.clear();
  }

  /**
   * 获取缓存统计信息
   */
  getStats(): {
    size: number;
    maxSize: number;
    hitRate?: number;
  } {
    return {
      size: this.cache.size,
      maxSize: this.config.maxSize,
    };
  }

  /**
   * 检查是否存在缓存项
   */
  has(url: string, options?: RequestInit): boolean {
    const key = this.generateKey(url, options);
    const item = this.cache.get(key);
    return item !== undefined && this.isItemValid(item);
  }
}

// 创建默认缓存实例
export const defaultCache = new APICache({
  ttl: 5 * 60 * 1000, // 5分钟
  maxSize: 100,
  enableMemoryCache: true,
});

/**
 * 创建带缓存的fetch函数
 */
export function createCachedFetch(cache: APICache = defaultCache) {
  return async function cachedFetch<T = any>(
    url: string,
    options: RequestInit & { cacheTtl?: number; enableCache?: boolean } = {}
  ): Promise<T> {
    const { enableCache = true, cacheTtl, ...fetchOptions } = options;

    // 只缓存GET请求
    if (!enableCache || (!fetchOptions.method || fetchOptions.method.toUpperCase() !== 'GET')) {
      const response = await fetch(url, fetchOptions);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return response.json();
    }

    // 尝试从缓存获取
    const cachedData = cache.get<T>(url, fetchOptions);
    if (cachedData !== null) {
      console.log(`Cache hit for ${url}`);
      return cachedData;
    }

    console.log(`Cache miss for ${url}, fetching from network`);

    // 从网络获取数据
    const response = await fetch(url, {
      method: fetchOptions.method || 'GET',
      headers: fetchOptions.headers,
      body: fetchOptions.body,
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();

    // 存入缓存
    cache.set(url, data, fetchOptions, cacheTtl);

    return data;
  };
}

/**
 * 带去重功能的fetch包装器
 */
export function createDeduplicatedFetch() {
  const pendingRequests = new Map<string, Promise<any>>();

  return async function deduplicatedFetch<T = any>(
    url: string,
    options: RequestInit = {}
  ): Promise<T> {
    const key = JSON.stringify({ url, options });

    // 如果相同的请求正在进行，返回同一个Promise
    if (pendingRequests.has(key)) {
      console.log(`Request deduplication for ${url}`);
      return pendingRequests.get(key) as Promise<T>;
    }

    // 创建新请求
    const requestPromise = fetch(url, options)
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
      })
      .finally(() => {
        // 请求完成后清理
        pendingRequests.delete(key);
      });

    pendingRequests.set(key, requestPromise);
    return requestPromise as Promise<T>;
  };
}

// 创建去重fetch实例
export const deduplicatedFetch = createDeduplicatedFetch();

// 导出缓存类供其他地方使用
export { APICache };
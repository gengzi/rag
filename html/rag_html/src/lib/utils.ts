import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"
import API_CONFIG from "./config"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * 格式化文档URL，确保相对路径转换为完整URL
 * @param url 原始URL（可能是相对路径或完整URL）
 * @returns 格式化后的完整URL
 */
export function formatDocumentUrl(url?: string): string {
  if (!url) return '#';
  
  // 检查是否已经是完整URL（以http://或https://开头）
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }
  
  // 确保基础URL末尾没有斜杠，而相对URL开头有斜杠
  const baseUrl = API_CONFIG.BASE_URL.endsWith('/') 
    ? API_CONFIG.BASE_URL.slice(0, -1) 
    : API_CONFIG.BASE_URL;
  
  const relativeUrl = url.startsWith('/') ? url : `/${url}`;
  
  return `${baseUrl}${relativeUrl}`;
}
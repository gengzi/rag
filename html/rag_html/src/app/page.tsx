"use client";

import Link from "next/link";
import { useState, useEffect, useCallback, useRef } from "react";
import { useRouter } from 'next/navigation';
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Divider } from "@/components/ui/divider";

// 导入图标
import { Search, Grid, FileText, Image, Database, MessageSquare, Filter, Sun, Moon, Loader2 } from "lucide-react";

// 导入API工具
import { api } from "@/lib/api";
import API_CONFIG from "@/lib/config";

// 导入Markdown解析库
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeHighlight from 'rehype-highlight';
import rehypeRaw from 'rehype-raw';
import rehypeKatex from 'rehype-katex';
import { Copy, Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import 'highlight.js/styles/github.css';
import 'katex/dist/katex.min.css';

// 自定义表格组件来确保正确的边框样式
const CustomTable = ({ children, ...props }: any) => (
  <table 
    {...props} 
    className="border border-gray-300 border-collapse w-full"
  >
    {children}
  </table>
);

const CustomThead = ({ children, ...props }: any) => (
  <thead {...props}>
    {children}
  </thead>
);

const CustomTr = ({ children, ...props }: any) => (
  <tr {...props}>
    {children}
  </tr>
);

const CustomTh = ({ children, ...props }: any) => (
  <th 
    {...props} 
    className="border border-gray-300 px-4 py-2 bg-gray-50 font-semibold text-left"
  >
    {children}
  </th>
);

const CustomTd = ({ children, ...props }: any) => (
  <td 
    {...props} 
    className="border border-gray-300 px-4 py-2"
  >
    {children}
  </td>
);

// 代码组件实现，兼容ReactMarkdown
const Code = ({ className, children, ...props }: any) => {
  // 检查是否是代码块（有language-前缀的类名）
  const isCodeBlock = className && className.includes('language-');
  
  if (isCodeBlock) {
    const [copied, setCopied] = useState(false);
    const codeRef = useRef<HTMLPreElement>(null);
    
    // 提取语言
    const language = className.replace(/language-/, '') || 'code';
    
    // 复制代码
    const handleCopy = () => {
      if (codeRef.current) {
        const text = codeRef.current.textContent || '';
        navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    };
    
    // 创建行号
    const text = String(children).trim();
    const lines = text.split('\n').length;
    const lineNumbers = Array.from({ length: lines }, (_, i) => i + 1);
    
    return (
      <div className="my-4 rounded-md overflow-hidden border border-gray-200 dark:border-gray-700 shadow-sm">
        {/* 代码块头部 */}
        <div className="bg-gray-50 dark:bg-gray-800 px-4 py-2 flex justify-between items-center">
          <div className="flex items-center space-x-2">
            <div className="w-3 h-3 rounded-full bg-red-500"></div>
            <div className="w-3 h-3 rounded-full bg-yellow-500"></div>
            <div className="w-3 h-3 rounded-full bg-green-500"></div>
            <span className="ml-2 text-xs font-medium text-gray-600 dark:text-gray-300">{language}</span>
          </div>
          <button 
            onClick={handleCopy}
            className="p-1.5 rounded-md text-gray-500 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
            aria-label="复制代码"
          >
            {copied ? <Check size={16} className="text-green-500" /> : <Copy size={16} />}
          </button>
        </div>
        {/* 代码内容和行号 */}
        <div className="bg-white dark:bg-gray-900 flex">
          {/* 行号列 */}
          <div className="bg-gray-50 dark:bg-gray-800 text-right pr-1.5 pl-1.5 py-1 border-r border-gray-200 dark:border-gray-700 select-none">
            {lineNumbers.map(num => (
              <div key={num} className="text-gray-500 dark:text-gray-400 font-mono leading-none" style={{ fontSize: '14px',padding:'1px' }}>
                {num}
              </div>
            ))}
          </div>
          {/* 代码内容 */}
          <pre 
            ref={codeRef}
            className="flex-1 p-1 overflow-x-auto font-mono text-gray-800 dark:text-gray-300"
            style={{ lineHeight: '1.1', margin: 0, padding: '4px', fontSize: '14px' }}
          >
            <code className={className} style={{ lineHeight: '1.1', margin: 0, padding: 0, fontSize: '14px' }}>{children}</code>
          </pre>
        </div>
      </div>
    );
  }
  
  // 内联代码样式
  return (
    <code 
      className="bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded-md text-sm font-mono text-gray-800 dark:text-gray-200"
      {...props}
    >
      {children}
    </code>
  );
};

export default function Home() {
  const [searchQuery, setSearchQuery] = useState("");
  const [isDarkMode, setIsDarkMode] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);
  const [showSearchResults, setShowSearchResults] = useState(false);
  // API相关状态
  const [aiSummary, setAiSummary] = useState<string>("");
  const [documents, setDocuments] = useState<any[]>([]);
  const [totalDocuments, setTotalDocuments] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(2);
  // 加载状态
  const [isSummaryLoading, setIsSummaryLoading] = useState(false);
  const [isDocumentsLoading, setIsDocumentsLoading] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  // 搜索状态
  const [isSearching, setIsSearching] = useState(false);
  // 路由导航
  const router = useRouter();

  // 检测页面滚动，用于添加滚动时的玻璃效果变化
  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 10);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // 使用useEffect来检测初始主题并只在客户端执行
  useEffect(() => {
    if (typeof window !== 'undefined') {
      setIsDarkMode(document.documentElement.classList.contains('dark'));
    }
  }, []);

  // 切换主题的函数
  const toggleTheme = () => {
    if (typeof window !== 'undefined') {
      document.documentElement.classList.toggle('dark');
      setIsDarkMode(document.documentElement.classList.contains('dark'));
    }
  };

  // 调用第一个接口：流式获取AI总结
  const fetchAiSummary = useCallback(async () => {
    setIsSummaryLoading(true);
    setHasError(false);
    setErrorMessage("");
    setAiSummary("");

    try {
      // 构建请求URL
      const url = '/chat/search';
      
      console.log("fetchAiSummary - 请求URL:", url);
      console.log("fetchAiSummary - 请求参数:", { query: searchQuery });
      
      // 使用原生fetch调用流式API，因为需要处理SSE
      const response = await fetch(`${API_CONFIG.BASE_URL}${url}`, {
        method: 'POST',
        headers: {
          'accept': 'text/event-stream',
          'Content-Type': 'application/json',
          'Authorization': `${API_CONFIG.AUTH.AUTH_HEADER_PREFIX} ${localStorage.getItem(API_CONFIG.AUTH.TOKEN_KEY) || ''}`
        },
        body: JSON.stringify({ query: searchQuery })
      });

      if (!response.ok) {
        throw new Error(`API请求失败: ${response.status}`);
      }

      // 获取响应体的ReadableStream
      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('无法获取响应流');
      }

      const decoder = new TextDecoder();
      let accumulatedSummary = '';
      let accumulatedResponse = '';

      try {
        while (true) {
          const { done, value } = await reader.read();
          
          console.log("消息响应reader:", value);
          
          // 处理流结束
          if (done || accumulatedResponse.includes('[DONE]')) {
            break;
          }
          
          if (value) {
            const chunk = decoder.decode(value, { stream: true });
            accumulatedResponse += chunk;
            
            console.log("chunk结果拼接", accumulatedResponse);
            
            // 分割SSE事件
            const events = accumulatedResponse.split('\n\n');
            
            // 处理每个完整的事件
            for (let i = 0; i < events.length - 1; i++) { // 只处理前n-1个完整事件
              const event = events[i].trim();
              if (!event) continue;
              
              // 查找data:前缀并提取JSON部分
              const dataPrefixIndex = event.indexOf('data:');
              if (dataPrefixIndex !== -1) {
                const jsonData = event.substring(dataPrefixIndex + 5);
                console.log("解析后json数据", jsonData);
                try {
                  const parsedData = JSON.parse(jsonData);
                  
                  // 检查是否是结束标记
                  if (parsedData.answer === '[DONE]') {
                    // 对于[DONE]标记，只设置加载状态为false，但不立即返回
                    // 继续执行下面的代码，确保引用信息被正确处理
                    // 不返回，继续处理引用信息
                  }
                  
                  // 获取答案内容
                  if (parsedData.answer && parsedData.answer !== '[DONE]') {
                    // 检查当前答案块是否已存在于fullAnswer的结尾（简单去重）
                    if (!accumulatedSummary.endsWith(parsedData.answer)) {
                      accumulatedSummary += parsedData.answer;
                      setAiSummary(accumulatedSummary);
                    }
                  }
                  
                  // 添加调试日志，查看接收到的answer内容
                  console.log('Received answer chunk:', parsedData.answer);
                  console.log('Current full answer:', accumulatedSummary);
                } catch (error) {
                  console.log('SSE JSON parsing error:', error);
                  // 尝试直接添加内容作为后备方案
                  const content = event.substring(dataPrefixIndex + 5).trim();
                  if (content && content !== '[DONE]' && !accumulatedSummary.endsWith(content)) {
                    accumulatedSummary += content;
                    setAiSummary(accumulatedSummary);
                  }
                }
              } else {
                // 如果不是标准的SSE格式，尝试直接处理
                if (event && event !== '[DONE]' && !accumulatedSummary.endsWith(event)) {
                  accumulatedSummary += event;
                  setAiSummary(accumulatedSummary);
                }
              }
            }
            
            // 清理已处理的事件，只保留最后一个可能不完整的事件
            if (events.length > 0) {
              accumulatedResponse = events[events.length - 1];
            }
          }
        }
      } catch (error) {
        console.error('SSE reading error:', error);
        // 即使有错误，也要确保isLoading被设置为false
      } finally {
        // 确保释放reader资源
        if (reader) {
          try {
            await reader.cancel();
          } catch (e) {
            // 忽略取消时的错误
          }
        }
      }
    } catch (error) {
      console.error('获取AI总结失败:', error);
      setHasError(true);
      setErrorMessage(`获取AI总结失败: ${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setIsSummaryLoading(false);
    }
  }, [searchQuery]);

  // 调用第二个接口：获取分页文档
  const fetchDocuments = useCallback(async (page = 1, size = 10) => {
    setIsDocumentsLoading(true);
    setHasError(false);
    setErrorMessage("");

    try {
      console.log("fetchDocuments - 开始请求文档搜索接口");
      console.log("fetchDocuments - 请求参数:", { query: searchQuery, page, pageSize: size });
      
      // 直接使用完整的URL路径
      const response = await api.post(
        '/document/search',
        {
          query: searchQuery,
          page: page,
          pageSize: size
        },
        {
          headers: {
            'accept': '*/*'
          }
        }
      );
      
      console.log("fetchDocuments - 响应数据:", response);

      // 解析新的响应格式，确保正确获取数据和分页信息
      const responseData = response?.data || [];
      const totalCount = response?.total || responseData.length;
      const totalPages = response?.totalPages || Math.ceil(totalCount / size) || 1;
      const currentPageNum = response?.page || page;
      const responsePageSize = response?.size || size;

      // 转换响应格式为前端展示所需的结构
      const transformedDocs = responseData.map((item: any, index: number) => ({
        name: item.source?.metadata?.documentName || `未命名文档`,
        snippetCount: item.highlightedContent?.length || 0,
        type: item.source?.metadata?.contentType?.split('/')[1]?.toUpperCase() || '未知',
        documentId: item.source?.metadata?.documentId || item.source?.id || `doc_${index}`,
        snippets: (item.highlightedContent || []).map((content: string, index: number) => ({
          id: `${item.source?.id}_${index}`,
          content: content, // 保留原始HTML标签以显示高亮
          rawContent: content.replace(/<[^>]*>/g, '') // 同时保留纯文本内容用于其他用途
        })),
        score: item.score,
        source: item.source
      }));

      setDocuments(transformedDocs);
      setTotalDocuments(totalCount);
      setCurrentPage(currentPageNum);
      setPageSize(responsePageSize);
    } catch (error) {
      console.error('获取文档失败:', error);
      setHasError(true);
      setErrorMessage('获取相关文档失败，请重试');
    } finally {
      setIsDocumentsLoading(false);
    }
  }, [searchQuery]);

  // 搜索函数，同时调用两个接口
  const handleSearch = useCallback(async () => {
    if (!searchQuery.trim()) {
      return;
    }
    setIsSearching(true);
    setShowSearchResults(true);
    setDocuments([]);
    setAiSummary('');
    setTotalDocuments(0);

    try {
      console.log("handleSearch - 开始并行调用接口");
      
      // 并行调用两个接口
      const [summaryResult, documentsResult] = await Promise.all([
        fetchAiSummary().catch(err => {
          console.error("fetchAiSummary 失败:", err);
          // 即使AI总结失败，也继续让文档请求完成
        }),
        fetchDocuments().catch(err => {
          console.error("fetchDocuments 失败:", err);
          throw err; // 文档请求失败需要向上抛出
        })
      ]);
      
      console.log("handleSearch - 接口调用完成");
    } catch (error) {
      console.error('搜索失败:', error);
      setHasError(true);
      setErrorMessage('搜索过程中发生错误，请重试');
    } finally {
      setIsSearching(false);
    }
  }, [searchQuery, fetchAiSummary, fetchDocuments]);

  // 分页处理函数
  const handlePageChange = (page: number) => {
    fetchDocuments(page, pageSize);
  };

  // 每页显示数量变化处理
  const handlePageSizeChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    const newSize = parseInt(event.target.value, 10);
    fetchDocuments(1, newSize);
  };

  return (
    <main className="min-h-screen ios-gradient">
      <div className="max-w-7xl mx-auto px-4 py-8">
        {/* 顶部导航 */}
        <div className="flex justify-between items-center mb-8">
          <div></div>
          <Button 
            className="bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 text-white shadow-lg transition-all duration-300"
            onClick={() => router.push('/dashboard/chat')}
          >
            <MessageSquare className="w-4 h-4 mr-2" />
            进入对话
          </Button>
        </div>

        {/* 页面标题 */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold tracking-tight">RAG-检索增强生成</h1>
        </div>

        {/* 搜索栏 - 应用玻璃效果 */}
        <div className="relative max-w-3xl mx-auto mb-10">
          <div className="absolute inset-y-0 left-0 flex items-center pl-6 pointer-events-none">
            <Filter className="w-4 h-4 text-gray-400" />
          </div>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyPress={(e) => {
              if (e.key === 'Enter') {
                handleSearch();
              }
            }}
            className="w-full py-4 px-16 pr-40 rounded-full border border-white/30 glass glass-hover bg-white/80 dark:bg-gray-800/80 text-gray-800 dark:text-white placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-purple-400 focus:ring-opacity-30 transition-all duration-300"
            placeholder="请输入您的问题"
          />
          <button
            className="absolute inset-y-0 right-0 flex items-center px-6 rounded-r-full bg-gradient-to-r from-purple-600 to-blue-600 text-white hover:from-purple-700 hover:to-blue-700 shadow-lg transition-all duration-300"
            onClick={handleSearch}
            disabled={isSearching}
          >
            {isSearching ? (
              <Loader2 className="w-4 h-4 mr-2 animate-spin" />
            ) : (
              <Search className="w-4 h-4 mr-2" />
            )}
            <span>{isSearching ? '搜索中...' : '搜索'}</span>
          </button>
        </div>

        {/* 分类导航 - 应用玻璃效果 */}
        <Tabs defaultValue="all" className="mb-10">
          
        </Tabs>

        {/* 主体内容 */}
        <div className="grid grid-cols-1 md:grid-cols-12 gap-8 mb-16">
          {showSearchResults && (
            <div className="md:col-span-12">
              {/* 搜索结果分类导航 */}

               
              {/* 错误提示 */}
              {hasError && (
                <div className="mb-8 p-4 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800/50 text-red-600 dark:text-red-400">
                  {errorMessage}
                </div>
              )}

              {/* AI总结卡片 */}
              <div className="mb-8">
                <Card className="glass glass-hover rounded-xl border border-white/30 dark:border-gray-800/50">
                  <CardHeader>
                    <CardTitle className="text-xl font-semibold">AI总结</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="bg-gray-50 dark:bg-gray-800/50 p-4 rounded-lg min-h-[200px]">
                      {isSummaryLoading && !aiSummary && (
                        <div className="flex items-center justify-center h-[160px]">
                          <div className="flex flex-col items-center">
                            <Loader2 className="w-6 h-6 animate-spin text-purple-500 mb-2" />
                            <p className="text-gray-500">AI正在生成总结...</p>
                          </div>
                        </div>
                      )}
                      {aiSummary && (
                        <div className="prose prose-sm max-w-none text-accent-foreground prose-headings:font-medium prose-headings:text-base prose-p:my-2 prose-li:my-1 prose-code:bg-muted/50 prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded prose-code:text-xs prose-ol:pl-5 prose-ul:pl-5 break-words">
                          <ReactMarkdown 
                            remarkPlugins={[remarkGfm, remarkMath]} 
                            rehypePlugins={[rehypeRaw, rehypeHighlight, rehypeKatex]}
                            components={{
                              table: CustomTable,
                              thead: CustomThead,
                              tr: CustomTr,
                              th: CustomTh,
                              td: CustomTd,
                              code: Code
                            }}
                          >
                            {aiSummary}
                          </ReactMarkdown>
                        </div>
                      )}
                    </div>
                  </CardContent>
                </Card>
              </div>
               
              {/* 文件列表 */}
              <div className="mb-8">
                <div className="flex justify-between items-center mb-4">
                  <h2 className="text-lg font-semibold">相关文件 ({totalDocuments})</h2>
                  <div className="flex items-center text-sm text-gray-500">
                    {/* <span className="mr-2">文件类型:</span>
                    <select className="bg-white/70 dark:bg-gray-800/70 backdrop-blur-md border border-white/30 dark:border-gray-700/50 rounded-md px-2 py-1 text-sm">
                      <option>全部</option>
                      <option>PDF</option>
                      <option>Markdown</option>
                      <option>Word</option>
                    </select> */}
                  </div>
                </div>
                
                {/* 文件项目 */}
                <div className="space-y-4">
                  {isDocumentsLoading && documents.length === 0 ? (
                    <div className="flex items-center justify-center h-[100px]">
                      <div className="flex flex-col items-center">
                        <Loader2 className="w-6 h-6 animate-spin text-purple-500 mb-2" />
                        <p className="text-gray-500">正在加载相关文档...</p>
                      </div>
                    </div>
                  ) : documents.length > 0 ? (
                    documents.map((doc, index) => (
                      <Card key={index} className="glass glass-hover rounded-xl border border-white/30 dark:border-gray-800/50 transition-all">
                        <CardContent className="pt-4">
                          <div className="flex justify-between items-start mb-3">
                            <div className="flex items-center">
                              <span className="inline-flex items-center justify-center w-8 h-8 rounded bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400 mr-3">
                                <FileText className="w-4 h-4" />
                              </span>
                              <div>
                                <h3 className="font-medium">{doc.name || `文档 ${index + 1}`}</h3>
                                <p className="text-xs text-gray-500">{doc.snippetCount || 0}个相关片段</p>
                              </div>
                            </div>
                            <span className="text-xs text-gray-500 bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded">{doc.type || '未知'}</span>
                          </div>
                            
                          {/* 文件内容片段 */}
                          {(doc.snippets && doc.snippets.length > 0) ? (
                            <div className="space-y-4 text-sm">
                              {doc.snippets.slice(0, 2).map((snippet: any, snippetIndex: number) => (
                                <div key={snippetIndex} className="bg-gray-50 dark:bg-gray-800/50 p-3 rounded-lg">
                                  <p dangerouslySetInnerHTML={{ __html: snippet.content || '无内容预览' }}></p>
                                  <div className="mt-2 flex justify-between items-center">
                                    <span className="text-xs text-gray-500">{doc.name || `文档 ${index + 1}`}</span>
                                    <button 
                                      className="text-xs text-blue-600 dark:text-blue-400 hover:underline"
                                      onClick={() => {
                                        // 在新窗口打开文件预览页面
                                        window.open(`/file-preview/${doc.documentId}`, '_blank');
                                      }}
                                    >
                                      查看更多
                                    </button>
                                  </div>
                                </div>
                              ))}
                            </div>
                          ) : (
                            <div className="text-gray-500 italic text-sm">无相关内容片段</div>
                          )}
                        </CardContent>
                      </Card>
                    ))
                  ) : (
                    <div className="text-center py-8 text-gray-500">
                      暂无相关文档
                    </div>
                  )}
                </div>
              </div>
               
              {/* 分页控件 */}
              {totalDocuments > 0 && (
                <div className="flex justify-center mt-8">
                  <div className="flex items-center space-x-1 glass glass-hover rounded-full px-4 py-2 border border-white/30 dark:border-gray-800/50">
                    <button 
                      className="w-8 h-8 flex items-center justify-center rounded-full text-gray-500 hover:bg-white/50 dark:hover:bg-gray-800/50 transition-all"
                      onClick={() => handlePageChange(currentPage - 1)}
                      disabled={currentPage === 1 || isDocumentsLoading}
                    >
                      &lt;
                    </button>
                    <button className="w-8 h-8 flex items-center justify-center rounded-full bg-purple-600 text-white">
                      {currentPage}
                    </button>
                    <span className="text-gray-500">/</span>
                    <span className="text-gray-500">{Math.ceil(totalDocuments / pageSize)}</span>
                    <button 
                      className="w-8 h-8 flex items-center justify-center rounded-full text-gray-500 hover:bg-white/50 dark:hover:bg-gray-800/50 transition-all"
                      onClick={() => handlePageChange(currentPage + 1)}
                      disabled={currentPage === Math.ceil(totalDocuments / pageSize) || isDocumentsLoading}
                    >
                      &gt;
                    </button>
                    <div className="mx-2 text-gray-500">|</div>
                    <select 
                      className="bg-transparent text-sm pr-6 focus:outline-none"
                      value={pageSize}
                      onChange={handlePageSizeChange}
                      disabled={isDocumentsLoading}
                    >
                      <option value="50">50条/页</option>
                      <option value="20">20条/页</option>
                      <option value="10">10条/页</option>
                      <option value="2">2条/页</option>
                    </select>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* 底部信息 */}
        <div className="text-center text-sm text-gray-500">
          
        </div>
      </div>
    </main>
  );
}

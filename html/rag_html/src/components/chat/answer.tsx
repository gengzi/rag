import React, { useState, useRef, useEffect } from 'react';
import { api } from '@/lib/api';
import API_CONFIG from '@/lib/config';
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeHighlight from "rehype-highlight";
import rehypeRaw from "rehype-raw";
import rehypeKatex from "rehype-katex";
import 'katex/dist/katex.min.css';
import 'highlight.js/styles/github.css';
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { File, Eye, CopyIcon as Copy, CheckCircle as Check } from "lucide-react";
import { cn } from "@/lib/utils";
import { Dialog, DialogContent, DialogTitle, DialogClose, DialogTrigger } from '@radix-ui/react-dialog';

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

const Code = ({ className, children, ...props }: any) => {
  const isCodeBlock = className && className.includes('language-');
  
  if (isCodeBlock) {
    const [copied, setCopied] = useState(false);
    const codeRef = useRef<HTMLPreElement>(null);
    const language = className.replace(/language-/, '') || 'code';
    
    const handleCopy = () => {
      if (codeRef.current) {
        navigator.clipboard.writeText(codeRef.current.textContent || '');
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    };
    
    const lines = String(children).trim().split('\n').length;
    const lineNumbers = Array.from({ length: lines }, (_, i) => i + 1);
    
    return (
      <div className="my-4 rounded-md overflow-hidden border border-gray-200 dark:border-gray-700 shadow-sm">
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
        <div className="bg-white dark:bg-gray-900 flex">
          <div className="bg-gray-50 dark:bg-gray-800 text-right pr-1.5 pl-1.5 py-1 border-r border-gray-200 dark:border-gray-700 select-none">
            {lineNumbers.map(num => (
              <div key={num} className="text-gray-500 dark:text-gray-400 font-mono leading-none" style={{ fontSize: '14px', padding: '1px' }}>
                {num}
              </div>
            ))}
          </div>
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
  
  return (
    <code 
      className="bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded-md text-sm font-mono text-gray-800 dark:text-gray-200"
      {...props}
    >
      {children}
    </code>
  );
};

interface Citation {
  id: number;
  text: string;
  metadata: Record<string, any>;
}

interface AnswerProps {
  content: string;
  citations?: Citation[];
  ragReference?: any;
  isStreaming?: boolean;
}

const Answer: React.FC<AnswerProps> = ({ content, citations, ragReference, isStreaming }) => {
  const getCitations = () => {
    if (citations && citations.length > 0) {
      return citations.map(citation => ({
        ...citation,
        metadata: {
          ...(citation.metadata || {}),
          imageUrl: citation.metadata?.imageUrl
        }
      }));
    }
    
    if (ragReference && ragReference.reference && Array.isArray(ragReference.reference)) {
      return ragReference.reference.map((ref: any, index: number) => ({
        id: index + 1,
        text: ref.text || '',
        metadata: {
          title: ref.documentName || `引用文档 ${index + 1}`,
          source: ref.contentType || '文档',
          page: ref.pageRange,
          url: ref.documentUrl,
          documentId: ref.documentId,
          imageUrl: ref.imageUrl,
          ...(ref.metadata || {})
        }
      }));
    }
    
    return [];
  };
  
  // 使用全局静态缓存，确保在整个应用生命周期内保持
  const imageUrlCacheRef = useRef<Record<string, string>>({
    // 全局图片URL缓存
  });
  
  const getAuthenticatedImageUrl = async (imageUrl: string): Promise<string> => {
    // 检查缓存中是否已有
    if (imageUrlCacheRef.current[imageUrl]) {
      return imageUrlCacheRef.current[imageUrl];
    }
    
    try {
      const fullApiPath = imageUrl.startsWith('/') ? imageUrl : `/${imageUrl}`;
      const response = await api.get(fullApiPath);
      
      const resultUrl = (response && typeof response === 'object' && 'url' in response) ? response.url : imageUrl;
      // 存入全局缓存
      imageUrlCacheRef.current[imageUrl] = resultUrl;
      return resultUrl;
    } catch (error) {
      console.error('Error fetching authenticated image URL:', error);
      // 出错时也缓存原始URL，避免重复请求失败的URL
      imageUrlCacheRef.current[imageUrl] = imageUrl;
      return imageUrl;
    }
  };
  
  const renderCitations = () => {
    const allCitations = getCitations();
    
    if (allCitations.length === 0) return null;

    return (
      <div className="mt-4 space-y-3">
        <h4 className="text-sm font-semibold text-muted-foreground flex items-center gap-1.5">
          <File className="h-3.5 w-3.5" />
          引用的文档
        </h4>
        <div className="grid gap-2 sm:grid-cols-1 md:grid-cols-1 lg:grid-cols-1">
          {allCitations.map((citation: Citation) => (
            <Card key={`${citation.id}-${citation.metadata.documentId || Math.random().toString(36).substr(2, 5)}`} className="border border-muted/30 bg-muted/50 hover:border-primary/50 hover:shadow-sm transition-all duration-300 transform hover:-translate-y-0.5">
              <CardHeader className="p-3 pb-0">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <File className="h-4 w-4 text-primary" />
                    <CardTitle className="text-sm font-medium">
                      {citation.metadata.title || `文档 ${citation.id}`}
                    </CardTitle>
                    <button
                      onClick={() => {
                        if (citation.metadata.documentId) {
                          const previewUrl = `/file-preview/${citation.metadata.documentId}`;
                          window.open(previewUrl, '_blank');
                        }
                      }}
                      className="text-xs font-medium text-primary/80 hover:text-primary transition-colors flex items-center gap-1"
                      aria-label="查看文件"
                    >
                      <Eye className="h-3 w-3" />
                      查看文件
                    </button>
                  </div>
                  <span className="text-xs font-medium text-primary/80">
                    [引用 {citation.id}]
                  </span>
                </div>
                <div className="text-xs mt-0.5 text-muted-foreground">
                  {citation.metadata.documentId ? `文档ID: ${citation.metadata.documentId}` : ''}
                  {citation.metadata.page ? `，页码: ${citation.metadata.page}` : ''}
                  {citation.metadata.source ? `，类型: ${citation.metadata.source}` : ''}
                </div>
              </CardHeader>
              <CardContent className="p-3 pt-2">
                {citation.metadata && citation.metadata.imageUrl ? (
                  <div className="flex flex-col md:flex-row gap-3">
                    <div className="w-full md:w-1/3 flex-shrink-0">
                      <AuthenticatedImage 
                        imageUrl={citation.metadata.imageUrl} 
                        alt={`${citation.metadata.title || '引用文档'} 图片`}
                      />
                    </div>
                    <div className="w-full md:w-2/3">
                      <div className="text-xs text-muted-foreground max-h-[200px] overflow-y-auto bg-background/30 p-2 rounded-md">
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
                          {citation.text}
                        </ReactMarkdown>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="text-xs text-muted-foreground max-h-[120px] overflow-y-auto bg-background/30 p-2 rounded-md">
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
                      {citation.text}
                    </ReactMarkdown>
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    );
  };

  // 在组件外部预加载图片
  useEffect(() => {
    const citations = getCitations();
    citations.forEach((citation: Citation) => {
      if (citation.metadata && citation.metadata.imageUrl) {
        const imageUrl = citation.metadata.imageUrl;
        if (!imageUrlCacheRef.current[imageUrl]) {
          getAuthenticatedImageUrl(imageUrl).catch(err => {
            console.error('Failed to preload image:', err);
          });
        }
      }
    });
  }, []);

  const AuthenticatedImage = React.memo(({ imageUrl, alt }: { imageUrl: string; alt: string }) => {
    // 直接从全局缓存获取，避免状态更新导致的渲染闪烁
    const cachedUrl = imageUrlCacheRef.current[imageUrl];
    const [finalImageUrl, setFinalImageUrl] = useState<string | null>(cachedUrl || null);
    const [loading, setLoading] = useState(!cachedUrl);
    const [error, setError] = useState<string | null>(null);
    const isMountedRef = useRef(true);
    const firstLoadRef = useRef(!cachedUrl);
    
    useEffect(() => {
      // 如果已经有缓存，直接使用，完全跳过加载状态
      if (cachedUrl) {
        setFinalImageUrl(cachedUrl);
        setLoading(false);
        firstLoadRef.current = false;
        return;
      }
      
      // 只有在第一次加载且没有缓存时才显示加载状态
      if (firstLoadRef.current) {
        const loadImageUrl = async () => {
          try {
            setLoading(true);
            setError(null);
            
            // 直接调用getAuthenticatedImageUrl获取URL
            const authenticatedUrl = await getAuthenticatedImageUrl(imageUrl);
            
            if (isMountedRef.current) {
              setFinalImageUrl(authenticatedUrl);
              setLoading(false);
            }
          } catch (err) {
            console.error('Failed to load authenticated image:', err);
            if (isMountedRef.current) {
              setError('图片加载失败');
              setLoading(false);
            }
          } finally {
            firstLoadRef.current = false;
          }
        };
        
        loadImageUrl();
      }
      
      return () => {
        isMountedRef.current = false;
      };
    }, [imageUrl, cachedUrl]);
    
    if (loading) {
      return (
        <div className="mb-3">
          <div className="w-full h-[200px] flex items-center justify-center bg-gray-100 rounded-md">
            <div className="text-sm text-gray-500">加载中...</div>
          </div>
        </div>
      );
    }
    
    if (error || !finalImageUrl) {
      return (
        <div className="mb-3">
          <div className="w-full h-[200px] flex items-center justify-center bg-gray-100 rounded-md text-red-500">
            {error || '图片加载失败'}
          </div>
        </div>
      );
    }
    
    return (
      <Dialog>
        <DialogTrigger asChild>
          <div 
            className="mb-3 relative overflow-hidden rounded-md border border-gray-200 shadow-sm transition-all duration-200 hover:shadow-md cursor-pointer bg-white"
            aria-label={`点击查看图片: ${alt}`}
          >
            <img 
              src={finalImageUrl} 
              alt={alt} 
              className="w-full max-h-[200px] object-contain"
              loading="lazy"
            />
            <div className="absolute inset-0 flex items-center justify-center opacity-0 hover:opacity-100 transition-opacity duration-200 bg-black/30">
              <div className="bg-white text-gray-800 p-2 rounded-full shadow-lg">
                <Eye className="h-6 w-6" />
              </div>
            </div>
          </div>
        </DialogTrigger>
        
        <DialogContent className="sm:max-w-5xl max-h-[90vh] p-1 bg-white rounded-lg shadow-xl overflow-hidden">
          <div className="p-4 border-b border-gray-200">
            <DialogTitle className="text-sm font-medium text-gray-700 truncate max-w-[80%]">{alt}</DialogTitle>
            <DialogClose asChild>
              <button 
                className="absolute top-4 right-4 p-2 text-gray-500 hover:text-gray-700 rounded-full hover:bg-gray-100 transition-colors duration-200"
                aria-label="关闭图片"
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </DialogClose>
          </div>
          <div className="flex justify-center items-center max-h-[calc(90vh-60px)] overflow-auto bg-gray-50">
            <img 
              src={finalImageUrl} 
              alt={alt} 
              className="max-w-full max-h-full object-contain" 
            />
          </div>
        </DialogContent>
      </Dialog>
    );
  });
  
  return (
    <div className={cn("space-y-2", isStreaming && "animate-pulse")}>
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
          {content}
        </ReactMarkdown>
      </div>
      {renderCitations()}
    </div>
  );
};

export default Answer;
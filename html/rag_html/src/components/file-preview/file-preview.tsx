import React, { useState, useEffect, useRef } from 'react';
import { Loader2, FileText, Image as ImageIcon, FileSpreadsheet, FileCode, FileText as FilePdf, Download, ExternalLink, RefreshCw, ArrowLeft, ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import MarkdownEditor from '@uiw/react-markdown-editor';
import { api } from '@/lib/api';
import { cn } from '@/lib/utils';
import * as XLSX from 'xlsx';
import * as mammoth from 'mammoth';

// 清理URL的辅助函数
const cleanUrlString = (url: string): string => {
  if (!url) return '';
  return url
    .replace(/^`|`$/g, '') // 移除首尾反引号
    .replace(/^"|"$/g, '') // 移除首尾双引号
    .replace(/^'|'$/g, '') // 移除首尾单引号
    .trim() // 移除首尾空格
    .replace(/\s+/g, ' ') // 规范化中间的空格为单个空格
    .trim(); // 再次修剪确保干净
};

interface FilePreviewProps {
  documentId: string;
  onBack?: () => void;
}

interface DocumentData {
  id?: string;
  name?: string;
  content?: string;
  contentType: string;
  suffix?: string;
  url: string;
  size?: number;
}

const FilePreview: React.FC<FilePreviewProps> = ({ documentId, onBack }) => {
  const [documentData, setDocumentData] = useState<DocumentData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pdfError, setPdfError] = useState(false);
  const [isTextLoaded, setIsTextLoaded] = useState(false);
  const [textContent, setTextContent] = useState('');
  const wordPreviewRef = useRef<HTMLDivElement>(null);
  const excelPreviewRef = useRef<HTMLDivElement>(null);

  // 获取文件扩展名
  const getFileExtension = (filename?: string) => {
    if (!filename) return '';
    const parts = filename.split('.');
    return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : '';
  };

  // 当文档数据变化时，自动渲染Word文档
  useEffect(() => {
    if (documentData && getFileType(documentData.contentType) === 'word') {
      renderWordDocument();
    }
  }, [documentData]);
  
  // 使用mammoth.js渲染Word文档
  const renderWordDocument = async () => {
    if (!documentData || !documentData.url) return;
    
    const container = wordPreviewRef.current;
    if (!container) return;
    
    try {
      // 清空容器
      container.innerHTML = '';
      
      // 下载Word文档
      const cleanUrl = cleanUrlString(documentData.url);
      const response = await fetch(cleanUrl);
      if (!response.ok) throw new Error(`下载失败: ${response.status}`);
      
      const arrayBuffer = await response.arrayBuffer();
      
      // 使用mammoth.js转换为HTML，包含样式信息
      const result = await mammoth.convertToHtml({ 
        arrayBuffer
      }, { 
        // 配置选项以保留更多格式信息
        styleMap: [
          "p[style-name='Title'] => h1:fresh",
          "p[style-name='Heading 1'] => h1:fresh",
          "p[style-name='Heading 2'] => h2:fresh",
          "p[style-name='Heading 3'] => h3:fresh",
          "p[style-name='Heading 4'] => h4:fresh",
          "p[style-name='Normal'] => p:fresh",
          "r[style-name='Strong'] => strong",
          "r[style-name='Emphasis'] => em"
        ],
        includeDefaultStyleMap: true
      });
      
      // 创建完整的HTML结构，包含样式
      const htmlContent = `
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            body {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
              line-height: 1.6;
              color: #333;
              margin: 0;
              padding: 20px;
              background: white;
            }
            h1, h2, h3, h4, h5, h6 {
              margin: 1.5em 0 0.5em 0;
              font-weight: 600;
              line-height: 1.3;
            }
            h1 { font-size: 2em; color: #1a1a1a; }
            h2 { font-size: 1.5em; color: #2d2d2d; }
            h3 { font-size: 1.25em; color: #404040; }
            h4 { font-size: 1.1em; color: #555; }
            p { margin: 1em 0; }
            table {
              border-collapse: collapse;
              width: 100%;
              margin: 1em 0;
              border: 1px solid #ddd;
            }
            th, td {
              border: 1px solid #ddd;
              padding: 8px 12px;
              text-align: left;
            }
            th {
              background-color: #f5f5f5;
              font-weight: 600;
            }
            tr:nth-child(even) {
              background-color: #f9f9f9;
            }
            ul, ol {
              margin: 1em 0;
              padding-left: 2em;
            }
            li {
              margin: 0.5em 0;
            }
            strong, b {
              font-weight: 600;
            }
            em, i {
              font-style: italic;
            }
            blockquote {
              margin: 1em 0;
              padding: 0.5em 1em;
              border-left: 4px solid #ddd;
              background-color: #f9f9f9;
              font-style: italic;
            }
            .word-content {
              max-width: 100%;
              overflow-x: auto;
              min-height: 100vh;
              height: 100%;
            }
            html, body {
              height: 100%;
              margin: 0;
              padding: 0;
            }
          </style>
        </head>
        <body>
          <div class="word-content">
            ${result.value}
          </div>
        </body>
        </html>
      `;
      
      // 使用iframe来更好地显示Word文档内容
      const iframe = document.createElement('iframe');
      iframe.style.width = '100%';
      iframe.style.height = '100vh';
      iframe.style.border = 'none';
      iframe.style.display = 'block';
      iframe.srcdoc = htmlContent;
      
      container.appendChild(iframe);
      
      // 添加容器样式
      container.className = 'word-preview w-full h-full min-h-0 bg-white rounded border';
      
      // 确保iframe加载后设置正确的高度
      iframe.onload = () => {
        iframe.style.height = '100vh';
        iframe.style.width = '100%';
      };
      
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      container.innerHTML = `<div class="text-red-500 p-4">Word文档预览失败: ${errorMessage}</div>`;
    }
  };


  
  // 移除wordContent状态，不再需要单独存储
  
  // 获取文件图标
  const getFileIcon = () => {
    if (!documentData) return <FileText className="h-6 w-6" />;
    
    const { contentType } = documentData;
    
    if (contentType === 'application/pdf') {
      return <FilePdf className="h-6 w-6 text-red-500" />;
    }
    
    if (contentType.startsWith('image/')) {
      return <ImageIcon className="h-6 w-6 text-blue-500" />;
    }
    
    if (contentType.includes('spreadsheet') || contentType.includes('excel')) {
      return <FileSpreadsheet className="h-6 w-6 text-green-500" />;
    }
    
    if (contentType.includes('text/') || 
        contentType.includes('markdown') || 
        contentType.includes('json') || 
        contentType.includes('xml') || 
        contentType.includes('code')) {
      return <FileCode className="h-6 w-6 text-purple-500" />;
    }
    
    if (contentType.includes('word') || contentType.includes('msword')) {
      return <FileText className="h-6 w-6 text-blue-600" />;
    }
    
    if (contentType.includes('powerpoint') || contentType.includes('presentation')) {
      return <FileText className="h-6 w-6 text-orange-500" />;
    }
    
    return <FileText className="h-6 w-6" />;
  };
  
  // 渲染PowerPoint文档预览
  const renderPowerPointPreview = () => {
    if (!documentData) return null;
    
    const cleanUrl = cleanUrlString(documentData.url);
    
    // 组件挂载时加载状态
    useEffect(() => {
      setLoading(true);
      setError(null);
      
      // 模拟加载完成
      const timer = setTimeout(() => {
        setLoading(false);
      }, 1000);
      
      return () => clearTimeout(timer);
    }, [cleanUrl]);
    
    return (
      <div className="flex flex-col items-center w-full h-full">
        {loading ? (
          <div className="w-full h-[calc(100vh-180px)] flex items-center justify-center">
            <div className="flex flex-col items-center">
              <Loader2 className="h-8 w-8 animate-spin text-primary mb-4" />
              <p className="text-sm text-muted-foreground">正在加载PowerPoint文档...</p>
            </div>
          </div>
        ) : (
          <>
            <div className="w-full min-h-[80vh] h-[calc(100vh-180px)] overflow-hidden border border-muted rounded-md bg-white flex flex-col">
              {/* 使用iframe加载PowerPoint预览 */}
              <iframe
                src={cleanUrl}
                title="PowerPoint Preview"
                className="w-full flex-1"
                frameBorder="0"
                onLoad={() => setLoading(false)}
                onError={() => {
                  console.error('PowerPoint加载失败');
                  setError('PowerPoint文档预览失败，请尝试下载文件');
                }}
              />
            </div>
            
            <div className="flex flex-wrap gap-2 mt-4 justify-center">
              <Button
                asChild
                variant="default"
                size="sm"
              >
                <a 
                  href={cleanUrl} 
                  target="_blank" 
                  rel="noopener noreferrer"
                  className="flex items-center gap-1"
                >
                  <ExternalLink className="h-4 w-4" />
                  在新窗口打开
                </a>
              </Button>
              
              <Button
                asChild
                variant="secondary"
                size="sm"
              >
                <a 
                  href={cleanUrl} 
                  download
                  rel="noopener noreferrer"
                  className="flex items-center gap-1"
                >
                  <Download className="h-4 w-4" />
                  下载文件
                </a>
              </Button>
            </div>
            
            {error && (
              <div className="mt-4 p-3 bg-destructive/10 text-destructive rounded-md text-sm">
                {error}
              </div>
            )}
          </>
        )}
      </div>
    );
  };

  // 格式化文件大小
  const formatFileSize = (bytes?: number) => {
    if (!bytes) return '未知';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  // 加载文档数据
  const fetchDocumentData = async () => {
    if (!documentId) return;
    
    setLoading(true);
    setError(null);
    setPdfError(false);
    
    try {
      const response = await api.get(`/document/${documentId}`);
      setDocumentData(response);
      
      // 尝试获取文本内容用于纯文本预览
      if (response.contentType.includes('text/') || 
          response.contentType.includes('markdown') || 
          response.contentType.includes('json') ||
          response.contentType.includes('xml') ||
          response.contentType.includes('code')) {
        try {
          const textResponse = await fetch(response.url);
          const text = await textResponse.text();
          setTextContent(text);
          setIsTextLoaded(true);
        } catch (textErr) {
          console.warn('Failed to load text content:', textErr);
        }
      }
    } catch (err) {
      setError('获取文件信息失败');
      console.error('Failed to fetch document data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!documentId) return;
    
    fetchDocumentData();
    
    // 重置状态
    setIsTextLoaded(false);
    setTextContent('');
  }, [documentId]);

  // 渲染PDF预览（使用iframe）
  const renderPdfPreview = () => {
    if (!documentData) return null;
    
    const cleanUrl = documentData.url.trim().replace(/^`|`$/g, '');
    
    return (
      <div className="flex flex-col items-center w-full h-full">
        <div className="w-full min-h-[80vh] h-[calc(100vh-180px)] overflow-auto">
          <div className="flex justify-center h-full">
            <iframe
              src={cleanUrl}
              title="PDF Preview"
              className={`w-full h-full border border-muted rounded-md ${pdfError ? 'hidden' : ''}`}
              onLoad={() => setPdfError(false)}
              onError={() => {
                console.error('PDF加载失败');
                setPdfError(true);
              }}
            />
            {pdfError && renderFallbackView()}
          </div>
        </div>
        
        <div className="flex flex-wrap gap-2 mt-4 justify-center">
          <Button
            asChild
            variant="default"
            size="sm"
          >
            <a 
              href={cleanUrl} 
              target="_blank" 
              rel="noopener noreferrer"
              className="flex items-center gap-1"
            >
              <ExternalLink className="h-4 w-4" />
              在新窗口打开
            </a>
          </Button>
          
          <Button
            asChild
            variant="secondary"
            size="sm"
          >
            <a 
              href={cleanUrl} 
              download
              rel="noopener noreferrer"
              className="flex items-center gap-1"
            >
              <Download className="h-4 w-4" />
              下载文件
            </a>
          </Button>
          
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setPdfError(false);
              // 刷新iframe可以通过重新设置src实现
            }}
            className="gap-1"
          >
            <RefreshCw className="h-4 w-4" />
            刷新预览
          </Button>
        </div>
      </div>
    );
  };

  // 渲染图像预览
  const renderImagePreview = () => {
    if (!documentData) return null;
    
    const cleanUrl = documentData.url.trim().replace(/^`|`$/g, '');
    
    return (
      <div className="flex flex-col items-center w-full h-full">
        <div className="w-full h-[calc(100vh-180px)] flex items-center justify-center bg-muted/30 rounded-lg p-4">
          <img 
            src={cleanUrl} 
            alt={documentData.name || '预览图片'} 
            className="max-w-full max-h-full object-contain" 
            loading="lazy"
          />
        </div>
        
        <div className="flex gap-2 mt-4 justify-center">
          <Button
            asChild
            variant="default"
            size="sm"
          >
            <a 
              href={cleanUrl} 
              target="_blank" 
              rel="noopener noreferrer"
              className="flex items-center gap-1"
            >
              <ExternalLink className="h-4 w-4" />
              查看大图
            </a>
          </Button>
          
          <Button
            asChild
            variant="secondary"
            size="sm"
          >
            <a 
              href={cleanUrl} 
              download
              rel="noopener noreferrer"
              className="flex items-center gap-1"
            >
              <Download className="h-4 w-4" />
              下载图片
            </a>
          </Button>
        </div>
      </div>
    );
  };

  // 渲染文本文件预览
  const renderTextPreview = () => {
    if (!documentData) return null;
    
    const cleanUrl = documentData.url.trim().replace(/^`|`$/g, '');
    const isMarkdown = documentData.contentType.includes('markdown') || 
                       getFileExtension(documentData.name) === 'md';
    
    return (
      <div className="flex flex-col items-center w-full h-full">
        <div className="w-full h-[calc(100vh-180px)] overflow-auto border border-muted rounded-lg shadow-sm">
          {isMarkdown ? (
            <MarkdownEditor
              value={isTextLoaded ? textContent : ''}
              onChange={() => {}} // 只读模式
              height="100%"
            />
          ) : (
            <pre className="p-6 bg-muted/30 h-full overflow-auto text-sm whitespace-pre-wrap break-words">
              {isTextLoaded ? textContent : (
                <iframe 
                  src={cleanUrl} 
                  title="Text Preview" 
                  className="w-full h-full border-none"
                  sandbox="allow-same-origin"
                  onLoad={() => setIsTextLoaded(true)}
                />
              )}
            </pre>
          )}
        </div>
        
        <div className="flex gap-2 mt-4 justify-center">
          <Button
            asChild
            variant="default"
            size="sm"
          >
            <a 
              href={cleanUrl} 
              target="_blank" 
              rel="noopener noreferrer"
              className="flex items-center gap-1"
            >
              <ExternalLink className="h-4 w-4" />
              查看原始文件
            </a>
          </Button>
          
          <Button
            asChild
            variant="secondary"
            size="sm"
          >
            <a 
              href={cleanUrl} 
              download
              rel="noopener noreferrer"
              className="flex items-center gap-1"
            >
              <Download className="h-4 w-4" />
              下载文件
            </a>
          </Button>
        </div>
      </div>
    );
  };

  // 渲染Excel文档预览
  const renderExcelPreview = () => {
    if (!documentData || !excelPreviewRef.current) return null;
    
    const cleanUrl = cleanUrlString(documentData.url);
    
    // 组件挂载时加载Excel文档
    useEffect(() => {
      const loadExcelDocument = async () => {
        setLoading(true);
        setError(null);
        
        try {
          // 清空预览容器
          excelPreviewRef.current!.innerHTML = '';
          
          // 下载文件
          const response = await fetch(cleanUrl);
          if (!response.ok) throw new Error('Failed to fetch Excel document');
          
          const arrayBuffer = await response.arrayBuffer();
          
          // 使用XLSX解析Excel文件
          const workbook = XLSX.read(arrayBuffer, { type: 'array' });
          
          // 获取第一个工作表
          const firstSheetName = workbook.SheetNames[0];
          const worksheet = workbook.Sheets[firstSheetName];
          
          // 将工作表转换为HTML
          const html = XLSX.utils.sheet_to_html(worksheet, {
            editable: false,
            header: 'A', // 使用默认的A1表示法作为标题行
            footer: '', // 空字符串表示没有页脚
            id: 'excel-preview-table'
          });
          
          // 创建一个临时容器来处理HTML
          const tempContainer = document.createElement('div');
          tempContainer.innerHTML = html;
          
          // 添加CSS样式
          const style = document.createElement('style');
          style.textContent = `
            #excel-preview-table {
              border-collapse: collapse;
              width: 100%;
              font-size: 12px;
            }
            #excel-preview-table th {
              background-color: #f8fafc;
              border: 1px solid #e2e8f0;
              padding: 4px 8px;
              text-align: left;
              font-weight: 500;
            }
            #excel-preview-table td {
              border: 1px solid #e2e8f0;
              padding: 4px 8px;
            }
            #excel-preview-table tr:nth-child(even) {
              background-color: #fafafa;
            }
          `;
          tempContainer.appendChild(style);
          
          // 添加工作表选择器
          const sheetSelector = document.createElement('div');
          sheetSelector.className = 'flex flex-wrap gap-2 mb-4 pb-2 border-b border-muted';
          sheetSelector.innerHTML = '<h4 className="text-sm font-medium mr-2">工作表:</h4>';
          
          workbook.SheetNames.forEach((sheetName, index) => {
            const button = document.createElement('button');
            button.className = `px-3 py-1 text-xs rounded ${index === 0 ? 'bg-primary text-primary-foreground' : 'bg-muted hover:bg-muted/80'}`;
            button.textContent = sheetName;
            button.onclick = () => {
              const sheet = workbook.Sheets[sheetName];
              const sheetHtml = XLSX.utils.sheet_to_html(sheet, {
                editable: false,
                header: 'A', // 使用默认的A1表示法作为标题行
                footer: '', // 空字符串表示没有页脚
                id: 'excel-preview-table'
              });
              
              const tableContainer = tempContainer.querySelector('#excel-content');
              if (tableContainer) {
                tableContainer.innerHTML = sheetHtml;
              }
              
              // 更新按钮样式
              const allButtons = sheetSelector.querySelectorAll('button');
              allButtons.forEach((btn, i) => {
                btn.className = `px-3 py-1 text-xs rounded ${i === index ? 'bg-primary text-primary-foreground' : 'bg-muted hover:bg-muted/80'}`;
              });
            };
            sheetSelector.appendChild(button);
          });
          
          // 创建内容容器
          const contentContainer = document.createElement('div');
          contentContainer.id = 'excel-content';
          contentContainer.appendChild(tempContainer.querySelector('table') || document.createElement('div'));
          
          tempContainer.innerHTML = '';
          tempContainer.appendChild(sheetSelector);
          tempContainer.appendChild(contentContainer);
          
          // 将处理后的内容添加到预览容器
          if (excelPreviewRef.current) {
            excelPreviewRef.current.appendChild(tempContainer);
          }
          
        } catch (error) {
          console.error('Error rendering Excel document:', error);
          setError('Excel文档预览失败，请尝试下载文件');
        } finally {
          setLoading(false);
        }
      };
      
      loadExcelDocument();
      
      // 清理函数
      return () => {
        if (excelPreviewRef.current) {
          excelPreviewRef.current.innerHTML = '';
        }
      };
    }, [cleanUrl]);
    
    return (
      <div className="flex flex-col items-center w-full h-full">
        {loading ? (
          <div className="w-full h-[calc(100vh-180px)] flex items-center justify-center">
            <div className="flex flex-col items-center">
              <Loader2 className="h-8 w-8 animate-spin text-primary mb-4" />
              <p className="text-sm text-muted-foreground">正在加载Excel文档...</p>
            </div>
          </div>
        ) : error ? (
          <Card className="w-full max-w-md mx-auto">
            <CardContent className="flex flex-col items-center justify-center text-center p-8">
              <div className="mb-6 text-destructive">
                {getFileIcon()}
              </div>
              <h3 className="text-lg font-medium text-destructive mb-2">{error}</h3>
              <p className="text-muted-foreground mb-6">
                请尝试下载文件后查看，或检查网络连接
              </p>
              <Button
                onClick={() => window.open(cleanUrl, '_blank')}
                variant="default"
                className="gap-1"
              >
                <Download className="h-4 w-4" />
                下载文件
              </Button>
            </CardContent>
          </Card>
        ) : (
          <>
            <div className="w-full min-h-[80vh] h-[calc(100vh-180px)] overflow-auto border border-muted rounded-md bg-white p-4">
              <div ref={excelPreviewRef} className="w-full" />
            </div>
            
            <div className="flex flex-wrap gap-2 mt-4 justify-center">
              <Button
                asChild
                variant="default"
                size="sm"
              >
                <a 
                  href={cleanUrl} 
                  target="_blank" 
                  rel="noopener noreferrer"
                  className="flex items-center gap-1"
                >
                  <ExternalLink className="h-4 w-4" />
                  在新窗口打开
                </a>
              </Button>
              
              <Button
                asChild
                variant="secondary"
                size="sm"
              >
                <a 
                  href={cleanUrl} 
                  download
                  rel="noopener noreferrer"
                  className="flex items-center gap-1"
                >
                  <Download className="h-4 w-4" />
                  下载文件
                </a>
              </Button>
            </div>
          </>
        )}
      </div>
    );
  };

  // 在DOM渲染完成后，检查wordPreviewRef是否可用并触发文档渲染
  useEffect(() => {
    let timeoutId: NodeJS.Timeout | null = null;
    
    // 只有当文档数据可用，且是Word类型时才渲染
    if (documentData && getFileType(documentData.contentType) === 'word') {
      console.log('Word文档数据可用，开始渲染');
      
      // 直接调用新的渲染函数
      timeoutId = setTimeout(() => {
        console.log('开始Word文档渲染');
        renderWordDocument().catch(error => {
          console.error('Word文档渲染失败:', error);
        });
      }, 100); // 减少延迟时间，让渲染更快开始
    }
    
    // 清理函数
    return () => {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      if (typeof window !== 'undefined' && wordPreviewRef.current) {
        try {
          wordPreviewRef.current.innerHTML = '';
        } catch (error) {
          console.warn('清理预览容器时出错:', error);
        }
      }
    };
  }, [documentData]); // 只依赖documentData，确保文档变化时重新渲染
  
  // 移除不再需要的shouldRenderWord状态，简化逻辑
  
  // 渲染Word文档预览 - 参考document-preview.tsx的实现
  const renderWordPreview = () => {
    if (!documentData) return null;
    
    const cleanUrl = cleanUrlString(documentData.url);
    
    return (
      <div className="w-full h-full flex flex-col min-h-0">
        <div className="bg-white h-full w-full rounded-lg shadow-sm overflow-hidden flex flex-col">
          {/* 标题栏 */}
          <div className="px-4 py-2 border-b bg-muted/10 flex justify-between items-center flex-shrink-0">
            <div className="text-lg font-medium">Word 文档预览</div>
            <Button 
              variant="default"
              size="sm"
              onClick={() => window.open(cleanUrl, '_blank')}
            >
              <ExternalLink className="h-4 w-4 mr-2" />
              在新窗口打开
            </Button>
          </div>
          
          {/* Word预览区域 - 铺满剩余高度 */}
          <div className="flex-1 min-h-0 overflow-hidden">
            <div
              ref={wordPreviewRef}
              className="word-preview w-full h-full min-h-0"
            />
          </div>
        </div>
      </div>
    );
  };

  // 渲染不支持的文件类型预览
  const renderFallbackView = () => {
    if (!documentData) return null;
    
    const cleanUrl = documentData.url.trim().replace(/^`|`$/g, '');
    
    return (
      <Card className="w-full max-w-md mx-auto">
        <CardContent className="flex flex-col items-center justify-center text-center p-8">
          <div className="mb-6">
            {getFileIcon()}
          </div>
          <h3 className="text-xl font-medium mb-2">
            {pdfError ? 'PDF预览失败' : '不支持的文件类型'}
          </h3>
          <p className="text-muted-foreground mb-6 max-w-md">
            {pdfError 
              ? '无法在线预览PDF文件，可能是由于浏览器扩展阻止了请求或文件格式不兼容。' 
              : '此文件类型不支持在线预览，请下载后查看'}
          </p>
          <div className="flex flex-wrap gap-2 justify-center">
            <Button
              asChild
              variant="default"
              size="sm"
            >
              <a 
                href={cleanUrl} 
                target="_blank" 
                rel="noopener noreferrer"
                className="flex items-center gap-1"
              >
                <ExternalLink className="h-4 w-4" />
                在新窗口打开
              </a>
            </Button>
            
            <Button
              asChild
              variant="secondary"
              size="sm"
            >
              <a 
                href={cleanUrl} 
                download
                rel="noopener noreferrer"
                className="flex items-center gap-1"
              >
                <Download className="h-4 w-4" />
                下载文件
              </a>
            </Button>
            
            {pdfError && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setPdfError(false);
                }}
                className="gap-1"
              >
                <RefreshCw className="h-4 w-4" />
                重试预览
              </Button>
            )}
          </div>
        </CardContent>
      </Card>
    );
  };

  // MIME类型映射表，用于优化文件类型判断
  const MIME_TYPE_MAPPING = {
    // PDF文件
    pdf: ['application/pdf'],
    
    // 图像文件
    image: [
      'image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/svg+xml',
      'image/bmp', 'image/tiff', 'image/x-icon'
    ],
    
    // 文本文件
    text: [
      'text/plain', 'text/html', 'text/css', 'text/javascript', 'text/xml',
      'application/json', 'application/xml', 'application/javascript',
      'text/markdown', 'text/x-markdown', 'application/x-httpd-php',
      'application/typescript', 'text/x-typescript', 'application/yaml',
      'text/yaml', 'application/x-yaml', 'application/json5'
    ],
    
    // Word文档
    word: [
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'application/msword',
      'application/vnd.wordprocessingml.document',
      'application/x-doc', 'application/x-word'
    ],
    
    // Excel文档
    excel: [
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.ms-excel',
      'application/vnd.ms-excel.sheet.macroEnabled.12',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.template',
      'application/vnd.ms-excel.template.macroEnabled.12',
      'application/vnd.ms-excel.addin.macroEnabled.12',
      'application/vnd.ms-excel.sheet.binary.macroEnabled.12',
      'text/csv', 'application/csv', 'text/comma-separated-values',
      'text/tab-separated-values'
    ],
    
    // PowerPoint文档
    powerpoint: [
      'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      'application/vnd.ms-powerpoint',
      'application/vnd.openxmlformats-officedocument.presentationml.template',
      'application/vnd.openxmlformats-officedocument.presentationml.slideshow',
      'application/vnd.ms-powerpoint.addin.macroEnabled.12',
      'application/vnd.ms-powerpoint.presentation.macroEnabled.12',
      'application/vnd.ms-powerpoint.template.macroEnabled.12',
      'application/vnd.ms-powerpoint.slideshow.macroEnabled.12'
    ],
    
    // 压缩文件
    archive: [
      'application/zip', 'application/x-zip-compressed', 'application/x-rar-compressed',
      'application/x-7z-compressed', 'application/x-gzip', 'application/x-tar',
      'application/x-bzip2', 'application/x-zip'
    ],
    
    // 音频文件
    audio: [
      'audio/mpeg', 'audio/wav', 'audio/ogg', 'audio/m4a', 'audio/aac',
      'audio/flac', 'audio/webm', 'audio/mp3', 'audio/x-wav', 'audio/x-m4a'
    ],
    
    // 视频文件
    video: [
      'video/mp4', 'video/avi', 'video/mpeg', 'video/quicktime', 'video/webm',
      'video/x-flv', 'video/x-matroska', 'video/x-msvideo', 'video/mp2t'
    ]
  };

  // 判断文件类型的辅助函数
  const getFileType = (contentType: string): string => {
    if (!contentType) return 'unknown';
    
    // 遍历MIME类型映射表
    for (const [type, mimes] of Object.entries(MIME_TYPE_MAPPING)) {
      if (mimes.some(mime => contentType.toLowerCase().includes(mime))) {
        return type;
      }
    }
    
    // 针对通用文本类型的额外检查
    if (contentType.startsWith('text/')) {
      return 'text';
    }
    
    return 'unknown';
  };

  // 根据文件类型渲染不同的预览内容
  const renderFileContent = () => {
    if (!documentData || !documentData.url) return null;

    const { contentType } = documentData;
    const fileType = getFileType(contentType);

    // 根据文件类型渲染相应的预览
    switch (fileType) {
      case 'pdf':
        return renderPdfPreview();
      
      case 'image':
        return renderImagePreview();
      
      case 'text':
        return renderTextPreview();
      
      case 'word':
        return renderWordPreview();
      
      case 'excel':
        return renderExcelPreview();
      
      case 'powerpoint':
        return renderPowerPointPreview();
      
      case 'audio':
      case 'video':
      case 'archive':
      case 'unknown':
      default:
        return renderFallbackView();
    }
  };

  // PDF加载中组件
  const PdfLoading = () => (
    <div className="flex flex-col items-center justify-center py-16">
      <Loader2 className="h-8 w-8 animate-spin text-primary mb-4" />
      <p className="text-sm text-muted-foreground">正在加载PDF文档...</p>
    </div>
  );

  // 主加载中状态
  if (loading) {
    return (
      <div className="min-h-screen flex flex-col bg-background">
        <div className="flex-1 flex items-center justify-center p-4">
          <div className="flex flex-col items-center">
            <Loader2 className="h-10 w-10 animate-spin text-primary mb-4" />
            <p className="text-sm text-muted-foreground">加载文件内容中...</p>
          </div>
        </div>
      </div>
    );
  }

  // 错误状态
  if (error) {
    return (
      <div className="min-h-screen flex flex-col bg-background">
        <div className="flex-1 flex items-center justify-center p-4">
          <div className="max-w-md text-center">
            <div className="mb-6 text-destructive">
              {getFileIcon()}
            </div>
            <h3 className="text-lg font-medium text-destructive mb-2">{error}</h3>
            <p className="text-muted-foreground mb-6">
              请检查网络连接后重试，或联系系统管理员获取帮助。
            </p>
            <Button
              onClick={fetchDocumentData}
              variant="default"
              className="gap-1"
            >
              <RefreshCw className="h-4 w-4" />
              重试
            </Button>
          </div>
        </div>
      </div>
    );
  }

  // 主界面
  return (
    <div className="min-h-screen flex flex-col bg-background">
      {/* 顶部标题栏 */}
      <header className="sticky top-0 left-0 right-0 z-10 bg-background/95 backdrop-blur-md border-b border-muted px-4 sm:px-6 py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {onBack && (
              <Button
                variant="ghost"
                size="icon"
                onClick={onBack}
                className="h-8 w-8"
              >
                <ArrowLeft className="h-4 w-4" />
                <span className="sr-only">返回</span>
              </Button>
            )}
            {getFileIcon()}
            <h2 className="text-lg font-medium truncate max-w-[70vw]">
              {documentData?.name || '文件预览'}
            </h2>
            {documentData?.size && (
              <span className="text-xs text-muted-foreground bg-muted rounded-full px-2 py-0.5">
                {formatFileSize(documentData.size)}
              </span>
            )}
          </div>
        </div>
        {documentData?.contentType && (
          <p className="text-sm text-muted-foreground mt-1">
            文件类型: {documentData.contentType}
          </p>
        )}
      </header>
      
      {/* 主内容区 */}
      <main className="flex-1 w-full p-4">
        {renderFileContent()}
      </main>
    </div>
  );
};

export default FilePreview;
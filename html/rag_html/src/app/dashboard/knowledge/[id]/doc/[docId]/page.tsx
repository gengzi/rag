"use client";

import { useState, useEffect } from "react";
import { useParams } from "next/navigation";
import { ChevronLeft, FileText, Share2, Download, Info, Search, AlertCircle } from "lucide-react";
import DocumentPreview from "@/components/file-preview/document-preview";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Divider } from "@/components/ui/divider";

import DashboardLayout from "@/components/layout/dashboard-layout";
import { useToast } from "@/components/ui/use-toast";
import { FileIcon, defaultStyles } from "react-file-icon";
import { api } from "@/lib/api";
import API_CONFIG from "@/lib/config";

interface DocumentDetail {
  id: string;
  name: string;
  size: number;
  suffix: string;
  createDate: string;
  status: string;
  chunks: DocumentChunk[];
  pages?: number;
  totalChunks?: number;
  updateTime?: string;
  contentType?: string;
}

interface ApiChunkDetail {
  id: string;
  content: string;
  pageNumInt: string;
  img: string[];
}

interface ApiDocumentResponse {
  code: number;
  success: boolean;
  message: string;
  data: {
    id: string;
    name: string;
    createTime: number;
    size: number;
    chunkNum: number;
    chunkDetails: ApiChunkDetail[];
    contentType: string;
  };
}

interface DocumentPreview {
  url: string;
  contentType: string;
}

interface DocumentChunk {
  id: string;
  content: string;
  index: number;
  metadata: {
    pageNumber?: number;
    pageNumbers?: number[];
    chunkNumber?: number;
    startLine?: number;
    endLine?: number;
    section?: string;
    [key: string]: any;
  };
  imgUrls?: string[];
}

interface KnowledgeBaseInfo {
  id: string;
  name: string;
  description: string;
}

export default function DocumentDetailPage() {
  const params = useParams();
  const knowledgeBaseId = params.id as string;
  const documentId = params.docId as string;
  const [document, setDocument] = useState<DocumentDetail | null>(null);
  const [knowledgeBase, setKnowledgeBase] = useState<KnowledgeBaseInfo | null>(null);
  const [documentPreview, setDocumentPreview] = useState<DocumentPreview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedSection, setSelectedSection] = useState<string | null>(null);
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set());
  const { toast } = useToast();
  
  // 图片URL缓存，用于存储已请求的图片URL，避免重复请求
  const [imageUrlCache, setImageUrlCache] = useState<Record<string, string>>({});
  // 大图预览状态
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  
  // 使用api实例加载图片的组件，自动应用认证头信息并使用缓存避免重复请求
  const ImageWithAuth = ({ imgKey, pageNumber, index, totalImages }: { 
    imgKey: string, 
    pageNumber: number, 
    index: number, 
    totalImages: number 
  }) => {
    const [imageUrl, setImageUrl] = useState<string>(imageUrlCache[imgKey] || '');
    const [isHovered, setIsHovered] = useState(false);
    
    useEffect(() => {
      // 检查缓存中是否已有该图片的URL
      if (imageUrlCache[imgKey]) {
        setImageUrl(imageUrlCache[imgKey]);
        return;
      }
      
      const loadImage = async () => {
        try {
          // 使用api.get获取图片URL，自动应用认证头
          const response = await api.get('/document/img', { params: { imgkey: imgKey } });
          
          // 从响应中获取url字段作为图片预览地址
          if (response && response.url) {
            // 更新组件状态
            setImageUrl(response.url);
            // 更新全局缓存
            setImageUrlCache(prev => ({
              ...prev,
              [imgKey]: response.url
            }));
          }
        } catch (error) {
          console.error('加载图片失败:', error);
        }
      };
      
      loadImage();
    }, [imgKey, imageUrlCache]);
    
    // 处理点击事件，显示大图预览
    const handleImageClick = () => {
      if (imageUrl) {
        setPreviewImage(imageUrl);
      }
    };
    
    return (
      <div 
        className="relative border rounded-md overflow-hidden bg-white cursor-zoom-in"
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        onClick={handleImageClick}
      >
        <div 
          className={`overflow-hidden transition-all duration-300 ${isHovered ? 'scale-150 z-10' : ''}`}
          style={{ 
            transformOrigin: 'center center',
            position: isHovered ? 'relative' : 'static'
          }}
        >
          <img
            src={imageUrl || '/file.svg'} // 使用占位图标直到图片加载完成
            alt={`Page ${pageNumber} - Image ${index + 1}`}
            className="w-full h-auto object-contain max-h-[150px]"
          />
        </div>
        {totalImages > 1 && (
          <div className="absolute top-1 right-1 bg-black/50 text-white text-xs px-1 rounded">
            {index + 1}/{totalImages}
          </div>
        )}
        {isHovered && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/10">
            <span className="text-xs bg-black/70 text-white px-2 py-1 rounded">
              点击查看大图
            </span>
          </div>
        )}
      </div>
    );
  };
  
  // 图片预览模态框组件
  const ImagePreviewModal = ({ imageUrl, onClose }: { imageUrl: string | null, onClose: () => void }) => {
    if (!imageUrl) return null;
    
    return (
      <div 
        className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4"
        onClick={onClose}
      >
        <div className="relative max-w-4xl max-h-[90vh]" onClick={(e) => e.stopPropagation()}>
          <button 
            className="absolute -top-12 right-0 text-white bg-black/50 rounded-full p-2 hover:bg-black/70 transition-colors"
            onClick={onClose}
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
          <img 
            src={imageUrl} 
            alt="预览图片" 
            className="max-w-full max-h-[90vh] object-contain"
          />
        </div>
      </div>
    );
  };
  
  // 辅助函数：从文件扩展名获取后缀
  const getFileExtension = (fileName: string): string => {
    const parts = fileName.split('.');
    return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : '';
  };
  
  // 辅助函数：格式化日期
  const formatDate = (timestamp: number): string => {
    const date = new Date(timestamp);
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };
  
  // 移除模拟数据，以接口返回数据为准

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        setError(null);
        
        // 调用分块详情API
        try {
          // 使用公共定义的API配置
          const chunksData = await api.get(`/document/chunks/details?documentId=${documentId}`);
          console.log("分块数据:", chunksData);
          
          // 解析API响应 - api.get()已经返回了responseData.data
          const data = chunksData;
            
            // 转换为文档详情格式
             const chunks: DocumentChunk[] = data.chunkDetails.map((chunkDetail: any, index: number) => ({
               id: chunkDetail.id,
               content: chunkDetail.content,
               index: index, // 添加index属性以匹配接口定义
               metadata: {
                 pageNumber: chunkDetail.pageNumInt ? parseInt(chunkDetail.pageNumInt.split(',')[0]) + 1 : index + 1,
                 pageNumbers: chunkDetail.pageNumInt.split(',').map((p: string) => parseInt(p) + 1),
                 chunkNumber: index + 1
               },
               embedding: [], // 添加空的embedding数组以匹配接口定义
               imgUrls: chunkDetail.img
             }));
            
            // 构建文档详情对象
            const documentDetail: DocumentDetail = {
              id: data.id,
              name: data.name,
              size: data.size,
              suffix: getFileExtension(data.name),
              createDate: formatDate(data.createTime),
              status: "已处理",
              chunks: chunks,
              pages: Math.max(0, ...(chunks.map(c => c.metadata.pageNumbers).flat().filter((num): num is number => typeof num === 'number'))),
              totalChunks: data.chunkNum,
              updateTime: formatDate(data.createTime),
              contentType: data.contentType
            };
            
            setDocument(documentDetail);
            
            // 从API响应中获取知识库信息，这里暂时保留空值，实际应从API获取
            setKnowledgeBase({ id: knowledgeBaseId, name: documentDetail.name, description: '' });
        } catch (chunksError) {
          console.error("获取分块数据失败:", chunksError);
          // 失败时设置错误状态，不再使用模拟数据
          setError("获取文档分块数据失败");
          toast({ variant: "destructive", title: "错误", description: "无法获取文档分块数据" });
        }
        
        // 获取文档预览URL和内容类型
        try {
          const previewData = await api.get(`/document/${documentId}`);
          setDocumentPreview(previewData);
          console.log("文档预览数据:", previewData);
        } catch (previewError) {
          console.error("获取文档预览失败:", previewError);
          // 继续使用模拟数据，不阻止页面加载
        }
      } catch (err) {
        console.error("获取数据失败:", err);
        setError("获取数据失败，请稍后重试");
        // 不再使用模拟数据作为兜底
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [knowledgeBaseId, documentId, toast]);

  const handleDownload = async () => {
    try {
      // 直接使用window.open打开下载链接
      window.open(`/api/knowledge-base/${knowledgeBaseId}/doc/${documentId}/download`, '_blank');
      
      toast({
        title: "开始下载",
        description: "文档已开始下载"
      });
    } catch (err) {
      console.error("下载文档失败:", err);
      toast({
        title: "下载失败",
        description: "无法下载文档，请稍后重试",
        variant: "destructive"
      });
    }
  };

  // 过滤文档块
  const filteredChunks = document?.chunks
    ?.filter(chunk => {
      if (!searchQuery) return true;
      return chunk.content.toLowerCase().includes(searchQuery.toLowerCase());
    })
    .sort((a, b) => a.index - b.index) || [];

  // 按章节组织文档块
  const sections = document?.chunks?.reduce((acc, chunk) => {
    const section = chunk.metadata?.section || "未分类";
    if (!acc[section]) {
      acc[section] = [];
    }
    acc[section].push(chunk);
    return acc;
  }, {} as Record<string, DocumentChunk[]>) || {};

  const toggleSection = (section: string) => {
    const newExpanded = new Set(expandedSections);
    if (newExpanded.has(section)) {
      newExpanded.delete(section);
    } else {
      newExpanded.add(section);
    }
    setExpandedSections(newExpanded);
  };

  const handleSectionClick = (section: string) => {
    setSelectedSection(section);
    if (!expandedSections.has(section)) {
      toggleSection(section);
    }
  };

  // 根据选择的章节过滤文档块
  const displayedChunks = selectedSection 
    ? sections[selectedSection] || []
    : filteredChunks;

  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex flex-col min-h-[70vh] justify-center items-center p-8">
          <div className="w-12 h-12 border-4 border-primary/30 border-t-primary rounded-full animate-spin mb-4"></div>
          <p className="text-muted-foreground">加载文档详情中...</p>
        </div>
      </DashboardLayout>
    );
  }

  if (error || !document || !knowledgeBase) {
    return (
      <DashboardLayout>
        <div className="flex flex-col min-h-[70vh] justify-center items-center p-8 text-center">
          <AlertCircle className="w-16 h-16 text-destructive/50 mb-4" />
          <h3 className="text-xl font-semibold mb-2">无法加载文档</h3>
          <p className="text-muted-foreground max-w-md mb-6">{error || "文档不存在或已被删除"}</p>
          <Link href={`/dashboard/knowledge/${knowledgeBaseId}/doc`}>
            <Button>返回知识库</Button>
          </Link>
        </div>
      </DashboardLayout>
    );
  }

  // 获取文件图标
  const getFileIcon = () => {
    const suffix = document.suffix.toLowerCase();
    if (suffix === "pdf") return <FileIcon extension="pdf" {...defaultStyles.pdf} />;
    if (suffix === "doc" || suffix === "docx") return <FileIcon extension="doc" {...defaultStyles.docx} />;
    if (suffix === "txt") return <FileIcon extension="txt" {...defaultStyles.txt} />;
    if (suffix === "md") return <FileIcon extension="md" {...defaultStyles.md} />;
    return <FileIcon extension={suffix} color="#E2E8F0" labelColor="#94A3B8" />;
  };

  return (
    <DashboardLayout>
    <div className="space-y-4">
        {/* 面包屑导航 */}
        <div className="flex items-center gap-2 text-sm">
          <Link href="/dashboard" className="text-muted-foreground hover:text-foreground">
            仪表盘
          </Link>
          <ChevronLeft className="h-4 w-4 text-muted-foreground" />
          <Link href={`/dashboard/knowledge`} className="text-muted-foreground hover:text-foreground">
            知识库
          </Link>
          <ChevronLeft className="h-4 w-4 text-muted-foreground" />
          <Link href={`/dashboard/knowledge/${knowledgeBaseId}`} className="text-muted-foreground hover:text-foreground">
            {knowledgeBase.name}
          </Link>
          <ChevronLeft className="h-4 w-4 text-muted-foreground" />
          <span className="font-medium truncate max-w-[200px]">{document.name}</span>
        </div>

        {/* 文档信息头部 */}
        <div className="flex justify-between items-center">
          <div className="flex items-center gap-4">
            <div className="w-10 h-10 flex-shrink-0">
              {getFileIcon()}
            </div>
            <div>
              <h1 className="text-xl font-bold tracking-tight mb-1">{document.name}</h1>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-muted-foreground">
                <span>{(document.size / 1024 / 1024).toFixed(2)} MB</span>
                <span>•</span>
                <span>{new Date(document.createDate).toLocaleDateString()}</span>
                <Badge variant="outline">{document.pages} 页</Badge>
              </div>
            </div>
          </div>
          
          <div className="flex gap-3">
            {/* <Button variant="ghost" size="sm" onClick={handleDownload}>
              <Download className="h-4 w-4 mr-2" />
              下载
            </Button> */}
            <Button variant="ghost" size="sm">
              <Share2 className="h-4 w-4 mr-2" />
              分享
            </Button>
            {/* <Button variant="ghost" size="sm">
              <Info className="h-4 w-4 mr-2" />
              详情
            </Button> */}
          </div>
        </div>

        {/* 主内容区域 - 双栏布局 */}
          <div className="flex gap-4">
            {/* 左侧面板 - 原文档内容 */}
            <div className="w-1/2">
              <Card>
                <CardContent className="p-0">
                  <div className="p-4 border-b">
                    <div className="flex justify-between items-center">
                      <h3 className="font-medium">{document.name}</h3>
                      <Badge variant="outline">{document.suffix.toUpperCase()}</Badge>
                    </div>
                  </div>
                  <div className="h-[calc(100vh-280px)] overflow-auto">
                    {/* 使用文档预览组件 */}
                    <div className="min-h-full p-4 bg-muted/30">
                      <DocumentPreview 
                        previewData={documentPreview}
                        fileName={document.name}
                        filteredChunks={filteredChunks}
                      />
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>

            {/* 右侧面板 - 分块结果 */}
            <div className="w-1/2">
              <Card>
                <CardContent className="p-0">
                  <div className="p-4 border-b">
                    <div className="flex justify-between items-center">
                      <h3 className="font-medium">切片结果</h3>
                      <Badge variant="outline">{document.chunks.length} 块</Badge>
                    </div>
                    {/* 搜索框 */}
                    <div className="relative mt-3">
                      <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                      <Input 
                        placeholder="搜索切片..." 
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="pl-9"
                      />
                    </div>
                  </div>
                  <div className="h-[calc(100vh-280px)] overflow-y-auto">
                    {filteredChunks.length === 0 ? (
                      <div className="text-center py-12 text-muted-foreground">
                        未找到匹配的切片
                      </div>
                    ) : (
                      <div className="p-2">
                        {filteredChunks.map((chunk) => (
                          <div key={chunk.id} className="mb-4 border rounded-lg overflow-hidden">
                            {/* 根据文档类型决定是否显示图片预览 */}
                            <div className={document?.contentType === 'application/pdf' ? 'flex' : ''}>
                              {/* 仅在PDF类型时显示图片预览区域 */}
                              {document?.contentType === 'application/pdf' && (
                                <div className="w-1/3 bg-muted/30 p-2">
                                  {chunk.imgUrls && chunk.imgUrls.length > 0 ? (
                                    <div className="w-full h-full space-y-2 overflow-auto">
                                      {chunk.imgUrls.map((imgUrl, idx) => (
                                        <ImageWithAuth
                                          key={idx}
                                          imgKey={imgUrl}
                                          pageNumber={chunk.metadata.pageNumber ?? 1}
                                          index={idx}
                                          totalImages={chunk.imgUrls?.length ?? 0}
                                        />
                                      ))}
                                    </div>
                                  ) : (
                                    <div className="w-full h-full flex flex-col items-center justify-center">
                                      <FileText className="h-8 w-8 mx-auto text-muted-foreground mb-2" />
                                      <span className="text-xs text-muted-foreground">第 {chunk.metadata.pageNumber} 页</span>
                                    </div>
                                  )}
                                </div>
                              )}
                              {/* 内容信息区域 - 非PDF类型时占满整行 */}
                              <div className={document?.contentType === 'application/pdf' ? 'w-2/3 p-3' : 'w-full p-3'}>
                                <div className="flex justify-between items-center text-xs text-muted-foreground mb-2">
                                  <span>切片 {chunk.index + 1}</span>
                                  <span>
                                    {chunk.metadata.pageNumbers && chunk.metadata.pageNumbers.length > 1
                                      ? `第 ${chunk.metadata.pageNumbers.join(',')} 页`
                                      : `第 ${chunk.metadata.pageNumber || '未知'} 页`
                                    }
                                  </span>
                                </div>
                                <div className="text-sm mb-3">
                                  {chunk.content}
                                </div>
                                {/* <div className="flex justify-between items-center">
                                  <div className={`w-4 h-4 border rounded flex-shrink-0 ${selectedSection === chunk.id ? 'bg-primary border-primary flex items-center justify-center' : 'border-border'}`}>
                                    {selectedSection === chunk.id && <span className="text-xs text-primary-foreground">✓</span>}
                                  </div>
                                  <Button variant="ghost" size="sm" className="h-8 text-xs">
                                    查看原始内容
                                  </Button>
                                </div> */}
                              </div>
                            </div>
                            </div>
                          ))}
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
    </div>
    
    {/* 图片预览模态框 */}
    <ImagePreviewModal 
      imageUrl={previewImage} 
      onClose={() => setPreviewImage(null)} 
    />
    
    </DashboardLayout>
  );
}
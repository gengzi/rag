"use client";

import { useEffect, useState, useRef } from "react";
import { useRouter } from "next/navigation";
import { formatDistanceToNow } from "date-fns";
import { zhCN } from "date-fns/locale";
import { FileText, Eye, Code } from "lucide-react";
import { FileIcon, defaultStyles } from "react-file-icon";
import { api, ApiError } from "@/lib/api";
import API_CONFIG from "@/lib/config";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Pagination } from "@/components/ui/pagination";

interface Document {
  id: string;
  name: string;
  location: string;
  size: number;
  suffix: string;
  createDate: string;
  status: string;
  progress: number;
  // 其他可能需要的字段
}

interface KnowledgeBase {
  id: number;
  name: string;
  description: string;
  documents: Document[];
}

interface DocumentListProps {
  knowledgeBaseId: string;
}

export function DocumentList({ knowledgeBaseId }: DocumentListProps) {
  const router = useRouter();
  const [documents, setDocuments] = useState<Document[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  // 添加分页状态
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [itemsPerPage, setItemsPerPage] = useState<number>(10);
  const [totalDocuments, setTotalDocuments] = useState<number>(0);
  // 正常加载数据，不再需要防止重复调用的逻辑

  const fetchDocuments = async (page: number = 1, pageSize: number = 10) => {
    try {
      setLoading(true);
      setError(null);
      
      // 只调用分页API，移除回退逻辑
      // 注意：API使用0-based索引，所以需要减1
      const apiPage = page - 1;
      const response = await api.get(`${API_CONFIG.API_PREFIX}${API_CONFIG.ENDPOINTS.DOCUMENT.KNOWLEDGE_BASE_DOCUMENTS}`, {
        params: {
          kbId: knowledgeBaseId,
          page: apiPage,
          size: pageSize
        }
      });
      
      // 处理新的分页格式数据
      if (response && response.content && response.totalElements !== undefined) {
        setDocuments(response.content);
        setTotalDocuments(response.totalElements);
      } else {
        // 如果返回格式不符合预期，显示空数据
        setDocuments([]);
        setTotalDocuments(0);
      }
    } catch (error) {
      if (error instanceof ApiError) {
        setError(error.message);
      } else {
        setError("获取文档失败");
      }
    } finally {
      setLoading(false);
    }
  };

  // 处理解析操作
  const handleParse = async (documentId: string) => {
    try {
      // 调用后端解析API - 使用配置文件中的设置
      const response = await api.post(
        `${API_CONFIG.ENDPOINTS.DOCUMENT.EMBEDDING}`,
        null,
        {
          headers: API_CONFIG.DEFAULT_HEADERS,
          params: {
            documentId: documentId
          }
        }
      );
      
      // 解析成功，重新加载文档列表
      fetchDocuments(currentPage, itemsPerPage);
      console.log(`解析文档成功: ${documentId}`);
    } catch (error) {
      console.error(`调用解析API时发生错误:`, error);
      if (error instanceof ApiError) {
        // 可以在这里添加更友好的错误提示
        console.error(`解析文档失败: ${error.message}`);
      }
    }
  };

  // 处理查看操作 - 跳转到文档分块详情页
  const handleView = (documentId: string) => {
    // 跳转到文档分块详情页
    router.push(`/dashboard/knowledge/${knowledgeBaseId}/doc/${documentId}`);
  };

  useEffect(() => {
    // 正常加载文档数据
    fetchDocuments(currentPage, itemsPerPage);
  }, [knowledgeBaseId, currentPage, itemsPerPage]);

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  const handleItemsPerPageChange = (newItemsPerPage: number) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1); // 重置到第一页
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center p-8">
        <div className="space-y-4">
          <div className="w-8 h-8 border-4 border-primary/30 border-t-primary rounded-full animate-spin mx-auto"></div>
          <p className="text-muted-foreground animate-pulse">
            加载文档中...
          </p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex justify-center items-center p-8">
        <p className="text-destructive">{error}</p>
      </div>
    );
  }

  if (documents.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] p-8">
        <div className="flex flex-col items-center max-w-[420px] text-center space-y-6">
          <div className="w-20 h-20 rounded-full bg-muted flex items-center justify-center">
            <FileText className="w-10 h-10 text-muted-foreground" />
          </div>
          <div className="space-y-2">
            <h3 className="text-xl font-semibold">暂无文档</h3>
            <p className="text-muted-foreground">
              上传您的第一个文档，开始构建您的知识库。
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>名称</TableHead>
            <TableHead>大小</TableHead>
            <TableHead>创建时间</TableHead>
            <TableHead>状态</TableHead>
            <TableHead>操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {documents.map((doc) => (
            <TableRow key={doc.id}>
              <TableCell className="font-medium">
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6">
                    {doc.suffix.toLowerCase() === "pdf" ? (
                      <FileIcon extension="pdf" {...defaultStyles.pdf} />
                    ) : doc.suffix.toLowerCase() === "doc" || doc.suffix.toLowerCase() === "docx" ? (
                      <FileIcon extension="doc" {...defaultStyles.docx} />
                    ) : doc.suffix.toLowerCase() === "txt" ? (
                      <FileIcon extension="txt" {...defaultStyles.txt} />
                    ) : doc.suffix.toLowerCase() === "md" ? (
                      <FileIcon extension="md" {...defaultStyles.md} />
                    ) : (
                      <FileIcon
                        extension={doc.suffix || ""}
                        color="#E2E8F0"
                        labelColor="#94A3B8"
                      />
                    )}
                  </div>
                  {doc.name}
                </div>
              </TableCell>
              <TableCell>{(doc.size / 1024 / 1024).toFixed(2)} MB</TableCell>
              <TableCell>
                {formatDistanceToNow(new Date(doc.createDate), {
                  addSuffix: true,
                  locale: zhCN
                })}</TableCell>
              <TableCell>
                <Badge
                  variant={
                    doc.status === "2"
                      ? "secondary" // Green for completed
                      : doc.status === "1"
                      ? "default" // Default for processing
                      : doc.status === "3"
                      ? "destructive" // Red for failed
                      : "outline" // Outline for unprocessed
                  }
                >
                  {doc.status === "0" ? "未处理" : 
                   doc.status === "1" ? "处理中" : 
                   doc.status === "2" ? "处理完成" : 
                   doc.status === "3" ? "处理失败" : "未知"}
                </Badge>
              </TableCell>
              <TableCell>
                <div className="flex gap-1">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    onClick={() => handleView(doc.id)}
                    disabled={doc.status !== "2"}
                    title="查看"
                  >
                    <Eye className="h-4 w-4" />
                  </Button>
                  
                  {/* 只在未处理(0)和处理失败(3)状态下显示解析按钮 */}
                  {(doc.status === "0" || doc.status === "3") && (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() => handleParse(doc.id)}
                      title="解析"
                    >
                      <Code className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      
      {/* 添加分页控件 */}
      {totalDocuments > itemsPerPage && (
        <div className="mt-4">
          <Pagination
            totalItems={totalDocuments}
            itemsPerPage={itemsPerPage}
            currentPage={currentPage}
            onPageChange={handlePageChange}
            onItemsPerPageChange={handleItemsPerPageChange}
            showItemsPerPage={true}
          />
        </div>
      )}
    </div>
  );
}

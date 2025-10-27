"use client";
import { useState, useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import { FileText, ArrowLeft, Loader2, AlertCircle } from "lucide-react";
import DashboardLayout from "@/components/layout/dashboard-layout";
import { api, ApiError } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

interface DocumentChunk {
  id: string;
  content: string;
  index: number;
  metadata: Record<string, any>;
}

interface DocumentDetail {
  id: string;
  name: string;
  location: string;
  size: number;
  suffix: string;
  createDate: string;
  status: string;
  progress: number;
  chunks: DocumentChunk[];
}

export default function DocumentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const knowledgeBaseId = params.id as string;
  const documentId = params.documentId as string;
  
  const [documentDetail, setDocumentDetail] = useState<DocumentDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  useEffect(() => {
    const fetchDocumentDetail = async () => {
      try {
        setLoading(true);
        setError(null);
        
        // 首先获取文档基本信息
        const documentResponse = await api.get(`/api/knowledge-base/document`, {
          params: {
            kbId: knowledgeBaseId,
            documentId: documentId
          }
        });
        
        // 然后获取文档分块信息
        const chunksResponse = await api.get(`/api/knowledge-base/document/chunks`, {
          params: {
            kbId: knowledgeBaseId,
            documentId: documentId
          }
        });
        
        // 合并文档信息和分块信息
        const detail: DocumentDetail = {
          ...documentResponse,
          chunks: chunksResponse.content || []
        };
        
        setDocumentDetail(detail);
      } catch (err) {
        if (err instanceof ApiError) {
          setError(err.message);
        } else {
          setError("Failed to fetch document details");
        }
      } finally {
        setLoading(false);
      }
    };
    
    fetchDocumentDetail();
  }, [knowledgeBaseId, documentId]);
  
  const handleBack = () => {
    router.back();
  };
  
  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex justify-center items-center min-h-[400px]">
          <div className="space-y-4 text-center">
            <div className="w-12 h-12 border-4 border-primary/30 border-t-primary rounded-full animate-spin mx-auto"></div>
            <p className="text-muted-foreground animate-pulse">Loading document details...</p>
          </div>
        </div>
      </DashboardLayout>
    );
  }
  
  if (error || !documentDetail) {
    return (
      <DashboardLayout>
        <div className="flex justify-center items-center min-h-[400px]">
          <div className="space-y-4 text-center">
            <AlertCircle className="w-12 h-12 text-destructive mx-auto" />
            <h3 className="text-xl font-semibold">Failed to load document</h3>
            <p className="text-muted-foreground">{error || "Document not found"}</p>
            <Button onClick={handleBack}>
              <ArrowLeft className="w-4 h-4 mr-2" />
              Back to Documents
            </Button>
          </div>
        </div>
      </DashboardLayout>
    );
  }
  
  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };
  
  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };
  
  return (
    <DashboardLayout>
      <div className="mb-6 flex items-center gap-4">
        <Button onClick={handleBack} variant="ghost" className="gap-2">
          <ArrowLeft className="h-4 w-4" />
          Back
        </Button>
        <h1 className="text-3xl font-bold">Document Details</h1>
      </div>
      
      <div className="grid gap-6">
        {/* 文档基本信息卡片 */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-md bg-muted flex items-center justify-center">
                <FileText className="h-6 w-6 text-muted-foreground" />
              </div>
              <div>
                <CardTitle>{documentDetail.name}</CardTitle>
                <CardDescription>Document ID: {documentDetail.id}</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent className="grid gap-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-1">
                <p className="text-sm font-medium text-muted-foreground">File Size</p>
                <p>{formatFileSize(documentDetail.size)}</p>
              </div>
              <div className="space-y-1">
                <p className="text-sm font-medium text-muted-foreground">File Type</p>
                <p>.{documentDetail.suffix}</p>
              </div>
              <div className="space-y-1">
                <p className="text-sm font-medium text-muted-foreground">Created At</p>
                <p>{formatDate(documentDetail.createDate)}</p>
              </div>
              <div className="space-y-1">
                <p className="text-sm font-medium text-muted-foreground">Status</p>
                <Badge 
                  variant={
                    documentDetail.status === "0" || documentDetail.progress === 100
                      ? "secondary"
                      : documentDetail.status === "1" || documentDetail.progress < 100
                      ? "default"
                      : "destructive"
                  }
                >
                  {documentDetail.status === "0" ? "Completed" : documentDetail.status === "1" ? "Processing" : "Failed"}
                </Badge>
              </div>
            </div>
          </CardContent>
        </Card>
        
        {/* 文档分块列表 */}
        <Card>
          <CardHeader>
            <CardTitle>Document Chunks</CardTitle>
            <CardDescription>Total chunks: {documentDetail.chunks.length}</CardDescription>
          </CardHeader>
          <CardContent>
            {documentDetail.chunks.length === 0 ? (
              <div className="flex flex-col items-center justify-center p-8 border rounded-lg bg-muted">
                <AlertCircle className="h-10 w-10 text-muted-foreground mb-4" />
                <p className="text-muted-foreground">No chunks available for this document</p>
              </div>
            ) : (
              <div className="h-[600px] overflow-y-auto pr-4">
                <div className="space-y-6 pb-4">
                  {documentDetail.chunks.map((chunk, index) => (
                    <Card key={chunk.id || index} className="border-2">
                      <CardHeader className="bg-muted/50 pb-2">
                        <div className="flex justify-between items-center">
                          <CardTitle className="text-lg">Chunk {index + 1}</CardTitle>
                          {chunk.id && (
                            <Badge variant="outline" className="text-xs">
                              {chunk.id.slice(0, 8)}...
                            </Badge>
                          )}
                        </div>
                        {chunk.metadata && Object.keys(chunk.metadata).length > 0 && (
                          <CardDescription className="text-xs text-muted-foreground">
                            {Object.entries(chunk.metadata)
                              .map(([key, value]) => `${key}: ${value}`)
                              .join(', ')}
                          </CardDescription>
                        )}
                      </CardHeader>
                      <CardContent>
                        <pre className="whitespace-pre-wrap text-sm font-mono bg-muted/30 p-4 rounded-md overflow-x-auto">
                          {chunk.content || "[Empty chunk]"}
                        </pre>
                      </CardContent>
                    </Card>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
}
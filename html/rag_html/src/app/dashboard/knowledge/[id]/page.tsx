"use client";

import { useParams } from "next/navigation";
import { useState, useCallback } from "react";
import { DocumentUploadSteps } from "@/components/knowledge-base/document-upload-steps";
import { DocumentList } from "@/components/knowledge-base/document-list";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { PlusIcon, AlertCircle, ArrowLeft } from "lucide-react";
import DashboardLayout from "@/components/layout/dashboard-layout";

export default function KnowledgeBasePage() {
  const params = useParams();
  // 添加空值检查
  if (!params || !params.id) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center min-h-[50vh]">
          <div className="text-center">
            <AlertCircle className="mx-auto h-12 w-12 text-red-500 mb-4" />
            <h2 className="text-xl font-bold">参数错误</h2>
            <p className="text-muted-foreground mt-2">缺少必要的参数信息</p>
            <Button variant="default" onClick={() => window.history.back()} className="mt-4">
              <ArrowLeft className="h-4 w-4 mr-2" />
              返回
            </Button>
          </div>
        </div>
      </DashboardLayout>
    );
  }
  
  const knowledgeBaseId = params.id as string;
  const [refreshKey, setRefreshKey] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);

  const handleUploadComplete = useCallback(() => {
    setRefreshKey((prev) => prev + 1);
    setDialogOpen(false);
  }, []);

  return (
    <DashboardLayout>
      <div className="flex justify-between items-center mb-8">
        <h1 className="text-3xl font-bold">知识库详情</h1>
        <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
          <DialogTrigger asChild>
            <Button>
              <PlusIcon className="w-4 h-4 mr-2" />
              添加文档
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-4xl">
            <DialogHeader>
              <DialogTitle>添加文档</DialogTitle>
              <DialogDescription>
                上传文档到您的知识库。支持的格式：PDF、DOCX、Markdown 和文本文档。
              </DialogDescription>
            </DialogHeader>
            <DocumentUploadSteps
              knowledgeBaseId={knowledgeBaseId}
              onComplete={handleUploadComplete}
            />
          </DialogContent>
        </Dialog>
      </div>

      <div className="mt-8">
        <DocumentList key={refreshKey} knowledgeBaseId={knowledgeBaseId} />
      </div>
    </DashboardLayout>
  );
}

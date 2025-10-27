"use client";

import { useState, useCallback } from "react";
import { FileIcon, defaultStyles } from "react-file-icon";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/use-toast";
import { Loader2, Upload, X, Settings, FileText } from "lucide-react";
import { cn } from "@/lib/utils";
import { api, ApiError } from "@/lib/api";
import { useDropzone } from "react-dropzone";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";

interface DocumentUploadStepsProps {
  knowledgeBaseId: string;
  onComplete?: () => void;
}

interface FileStatus {
  file: File;
  status:
    | "pending"
    | "uploading"
    | "uploaded"
    | "processing"
    | "completed"
    | "error";
  uploadId?: number;
  documentId?: number;
  tempPath?: string;
  error?: string;
}

interface UploadResult {
  upload_id?: number;
  document_id?: number;
  file_name: string;
  status: "exists" | "pending";
  message?: string;
  skip_processing: boolean;
  temp_path?: string;
}

interface PreviewChunk {
  content: string;
  metadata: Record<string, any>;
}

interface PreviewResponse {
  chunks: PreviewChunk[];
  total_chunks: number;
}

interface TaskResponse {
  tasks: Array<{
    upload_id: number;
    task_id: number;
  }>;
}

interface TaskStatus {
  document_id: number;
  status: "pending" | "processing" | "completed" | "failed";
  error_message?: string;
}

interface TaskStatusMap {
  [key: number]: TaskStatus;
}

interface TaskStatusResponse {
  [key: string]: TaskStatus;
}

export function DocumentUploadSteps({
  knowledgeBaseId,
  onComplete,
}: DocumentUploadStepsProps) {
  const [currentStep, setCurrentStep] = useState(1);
  const [files, setFiles] = useState<FileStatus[]>([]);
  const [uploadedDocuments, setUploadedDocuments] = useState<{
    [key: number]: PreviewResponse;
  }>({});
  const [selectedDocumentId, setSelectedDocumentId] = useState<number | null>(
    null
  );
  const [taskStatuses, setTaskStatuses] = useState<{
    [key: number]: TaskStatus;
  }>({});
  const [isLoading, setIsLoading] = useState(false);
  const [chunkSize, setChunkSize] = useState(1000);
  const [chunkOverlap, setChunkOverlap] = useState(200);
  const { toast } = useToast();

  const onDrop = useCallback((acceptedFiles: File[]) => {
    setFiles((prev) => [
      ...prev,
      ...acceptedFiles.map((file) => ({
        file,
        status: "pending" as const,
      })),
    ]);
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      "application/pdf": [".pdf"],
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
        [".docx"],
      "text/plain": [".txt"],
      "text/markdown": [".md"],
    },
  });

  const removeFile = (file: File) => {
    setFiles((prev) => prev.filter((f) => f.file !== file));
  };

  // Step 1: Upload files
  const handleFileUpload = async () => {
    const pendingFiles = files.filter((f) => f.status === "pending");
    if (pendingFiles.length === 0) return;

    setIsLoading(true);
    try {
      const formData = new FormData();
      // 添加knowledgeId参数
      formData.append("knowledgeId", knowledgeBaseId);
      // 添加files参数
      pendingFiles.forEach((fileStatus) => {
        formData.append("files", fileStatus.file);
      });

      const response = await api.post(
        `/api/knowledge-base/batch-upload`,
        formData,
        {
          headers: {},
        }
      );
      
      // 确保data是数组格式
      const data = Array.isArray(response) ? response : [response];

      // Update file statuses
      setFiles((prev) =>
        prev.map((f) => {
          const uploadResult = data.find((d) => d.file_name === f.file.name);
          if (uploadResult) {
            if (uploadResult.status === "exists") {
              return {
                ...f,
                status: "completed",
                documentId: uploadResult.document_id,
                error: uploadResult.message,
              };
            } else {
              return {
                ...f,
                status: "uploaded",
                uploadId: uploadResult.upload_id,
                tempPath: uploadResult.temp_path,
              };
            }
          }
          return f;
        })
      );

          // 上传完成后直接调用完成回调
      if (onComplete) {
        onComplete();
      }
      toast({
        title: "上传成功",
        description: `${data.length}个文件已成功上传。`,
      });
    } catch (error) {
      toast({
        title: "上传失败",
        description:
          error instanceof ApiError ? error.message : "发生错误",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  // Step 2: Preview chunks
  const handlePreview = async () => {
    const selectedFile = files.find(
      (f) =>
        f.documentId === selectedDocumentId || f.uploadId === selectedDocumentId
    );
    if (!selectedFile) return;

    setIsLoading(true);
    try {
      const data = await api.post(
        `/api/knowledge-base/${knowledgeBaseId}/documents/preview`,
        {
          document_ids: [selectedDocumentId],
          chunk_size: chunkSize,
          chunk_overlap: chunkOverlap,
        }
      );

      setUploadedDocuments(data);

      toast({
        title: "预览生成成功",
        description: "文档预览已成功生成。",
      });
    } catch (error) {
      toast({
        title: "预览失败",
        description:
          error instanceof ApiError ? error.message : "发生错误",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  // Step 3: Process documents
  const handleProcess = async (uploadResults?: UploadResult[]) => {
    const resultsToProcess =
      uploadResults ||
      files
        .filter((f) => f.status === "uploaded")
        .map((f) => ({
          upload_id: f.uploadId!,
          file_name: f.file.name,
          status: "pending" as const,
          skip_processing: false,
          temp_path: f.tempPath!,
        }));

    if (resultsToProcess.length === 0) return;

    setIsLoading(true);
    try {
      const data = (await api.post(
        `/api/knowledge-base/${knowledgeBaseId}/documents/process`,
        resultsToProcess
      )) as TaskResponse;

      // Initialize task statuses
      const initialStatuses = data.tasks.reduce<TaskStatusMap>(
        (acc, task) => ({
          ...acc,
          [task.task_id]: {
            document_id: task.upload_id,
            status: "pending" as const,
          },
        }),
        {}
      );
      setTaskStatuses(initialStatuses);

      // Start polling for task status
      pollTaskStatus(data.tasks.map((t) => t.task_id));
    } catch (error) {
      setIsLoading(false);
      toast({
        title: "处理失败",
        description:
          error instanceof ApiError ? error.message : "发生错误",
        variant: "destructive",
      });
    }
  };

  // Poll task status
  const pollTaskStatus = async (taskIds: number[]) => {
    const poll = async () => {
      try {
        const response = (await api.get(
          `/api/knowledge-base/${knowledgeBaseId}/documents/tasks?task_ids=${taskIds.join(
            ","
          )}`
        )) as TaskStatusResponse;

        // Convert string keys to numbers
        const data = Object.entries(response).reduce<TaskStatusMap>(
          (acc, [key, value]) => ({
            ...acc,
            [parseInt(key)]: value,
          }),
          {}
        );

        setTaskStatuses(data);

        // Check if all tasks are completed or failed
        const allDone = Object.values(data).every(
          (task) => task.status === "completed" || task.status === "failed"
        );

        if (allDone) {
          setIsLoading(false);
          const hasErrors = Object.values(data).some(
            (task) => task.status === "failed"
          );
          if (!hasErrors) {
            toast({
              title: "处理完成",
              description: "所有文档已成功处理。",
            });
            onComplete?.();
          } else {
            toast({
              title: "处理完成但有错误",
              description: "部分文档处理失败。",
              variant: "destructive",
            });
          }
        } else {
          // Continue polling
          setTimeout(poll, 2000);
        }
      } catch (error) {
        setIsLoading(false);
        toast({
          title: "状态检查失败",
          description:
            error instanceof ApiError ? error.message : "发生错误",
          variant: "destructive",
        });
      }
    };

    poll();
  };

  const handleProcessClick = (e: React.MouseEvent) => {
    e.preventDefault();
    handleProcess();
  };

  return (
    <div className="w-full max-w-4xl mx-auto">
      <div className="mb-8">
        <div className="flex justify-center mb-2">
          <div
            className="flex flex-col items-center space-y-2 flex-1"
          >
            <div
              className="w-12 h-12 rounded-full flex items-center justify-center border-2 bg-primary text-primary-foreground border-primary"
            >
              <Upload className="w-6 h-6" />
            </div>
            <span className="text-sm font-medium">
              1. 上传
            </span>
          </div>
        </div>
      </div>

      <Tabs value="1" className="w-full">
        <TabsContent value="1" className="mt-6">
          <Card className="p-6">
            <div className="space-y-4">
              <div
                {...getRootProps()}
                className={cn(
                  "border-2 border-dashed rounded-lg p-8 text-center transition-colors",
                  isDragActive
                    ? "border-primary bg-primary/5"
                    : "hover:border-primary/50"
                )}
              >
                <input {...getInputProps()} />
                <Upload className="w-12 h-12 mx-auto text-muted-foreground" />
                <p className="mt-2 text-sm font-medium">
                  将文件拖放到此处或点击浏览
                </p>
                <p className="text-xs text-muted-foreground">
                  支持PDF、DOCX、TXT和MD文件
                </p>
              </div>
              {files.length > 0 && (
                <div className="space-y-2 max-h-[300px] overflow-y-auto">
                  {files.map((fileStatus) => (
                    <div
                      key={fileStatus.file.name}
                      className="flex items-center justify-between p-4 rounded-lg border"
                    >
                      <div className="flex items-center space-x-4">
                        <div className="w-8 h-8">
                          <FileIcon
                            extension={fileStatus.file.name.split(".").pop()}
                            {...defaultStyles[
                              fileStatus.file.name
                                .split(".")
                                .pop() as keyof typeof defaultStyles
                            ]}
                          />
                        </div>
                        <div>
                          <p className="text-sm font-medium">
                            {fileStatus.file.name}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {(fileStatus.file.size / 1024 / 1024).toFixed(2)} MB
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center space-x-2">
                        {fileStatus.status === "uploaded" && (
                          <span className="text-green-500 text-sm">
                            已上传
                          </span>
                        )}
                        {fileStatus.status === "error" && (
                          <span className="text-red-500 text-sm">
                            {fileStatus.error}
                          </span>
                        )}
                        <button
                          onClick={() => removeFile(fileStatus.file)}
                          className="p-1 hover:bg-accent rounded-full"
                        >
                          <X className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <Button
                onClick={handleFileUpload}
                disabled={
                  !files.some((f) => f.status === "pending") || isLoading
                }
                className="w-full"
              >
                {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                上传文件
              </Button>
            </div>
          </Card>
        </TabsContent>


      </Tabs>
    </div>
  );
}

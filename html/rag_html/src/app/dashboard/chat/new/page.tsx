"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import DashboardLayout from "@/components/layout/dashboard-layout";
import { api, ApiError } from "@/lib/api";
import { useToast } from "@/components/ui/use-toast";
import { Plus } from "lucide-react";

interface KnowledgeBase {
  id: number;
  name: string;
  description: string;
}

export default function NewChatPage() {
  const router = useRouter();
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [selectedKB, setSelectedKB] = useState<number | null>(null);
  const [title, setTitle] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const { toast } = useToast();

  useEffect(() => {
    fetchKnowledgeBases();
  }, []);

  const fetchKnowledgeBases = async () => {
    try {
      const data = await api.get("/api/knowledge-base");
      setKnowledgeBases(data);
      setIsLoading(false);
    } catch (error) {
      console.error("获取知识库失败:", error);
      if (error instanceof ApiError) {
        toast({
          title: "错误",
          description: error.message,
          variant: "destructive",
        });
      }
    }
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!selectedKB) {
      setError("请选择一个知识库");
      return;
    }

    setError("");
    setIsSubmitting(true);

    try {
      const data = await api.post("/chat/rag/create", {
        chatName: title,
        kbId: selectedKB.toString(),
      });

      router.push(`/dashboard/chat/${data}`);
    } catch (error) {
      console.error("创建对话失败:", error);
      if (error instanceof ApiError) {
        setError(error.message);
        toast({
          title: "错误",
          description: error.message,
          variant: "destructive",
        });
      } else {
        setError("创建对话失败");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const selectKnowledgeBase = (id: number) => {
    setSelectedKB(id);
  };

  if (!isLoading && knowledgeBases.length === 0) {
    return (
      <DashboardLayout>
        <div className="max-w-2xl mx-auto text-center py-16">
          <h2 className="text-3xl font-bold tracking-tight mb-4">
            未找到知识库
          </h2>
          <p className="text-muted-foreground mb-8">
            开始聊天前，您需要至少创建一个知识库。
          </p>
          <Link
            href="/dashboard/knowledge"
            className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
          >
            <Plus className="mr-2 h-4 w-4" />
            创建知识库
          </Link>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="max-w-2xl mx-auto space-y-8">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">开始新对话</h2>
          <p className="text-muted-foreground">
            选择一个知识库进行对话
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="space-y-2">
            <label
              htmlFor="title"
              className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
            >
              对话标题
            </label>
            <input
              id="title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              type="text"
              required
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              placeholder="输入对话标题"
            />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
              知识库
            </label>
            <div className="text-xs text-muted-foreground">
              即将支持多选...
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              {isLoading ? (
                <div className="col-span-2 flex justify-center py-8">
                  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                </div>
              ) : (
                knowledgeBases.map((kb) => (
                  <label
                    key={kb.id}
                    className={`group flex items-center space-x-3 rounded-lg border p-4 cursor-pointer transition-all duration-200 hover:shadow-md ${selectedKB === kb.id ? "border-primary bg-primary/5 shadow-sm" : "hover:border-primary/50"}`}
                  >
                    <div className="relative flex items-center justify-center">
                      <input
                        type="radio"
                        name="knowledge-base"
                        className="peer h-4 w-4 shrink-0 rounded-full border border-primary text-primary focus:outline-none focus:ring-2 focus:ring-primary/20 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                        checked={selectedKB === kb.id}
                        onChange={() => selectKnowledgeBase(kb.id)}
                      />
                    </div>
                    <div className="flex-1 space-y-1">
                      <p className="font-medium group-hover:text-primary transition-colors">
                        {kb.name}
                      </p>
                      <p className="text-sm text-muted-foreground line-clamp-2">
                        {kb.description || "无描述"}
                      </p>
                    </div>
                  </label>
                ))
              )}
            </div>
          </div>

          {error && <div className="text-sm text-red-500">{error}</div>}

          <div className="flex justify-end space-x-4">
            <button
              type="button"
              onClick={() => router.back()}
              className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-background hover:bg-accent hover:text-accent-foreground h-10 px-4 py-2"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={isSubmitting || !selectedKB}
              className="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 px-4 py-2"
            >
              {isSubmitting ? "创建中..." : "开始对话"}
            </button>
          </div>
        </form>
      </div>
    </DashboardLayout>
  );
}

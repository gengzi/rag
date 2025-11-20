"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import DashboardLayout from "@/components/layout/dashboard-layout";
import { useToast } from "@/components/ui/use-toast";
import { api, ApiError } from "@/lib/api";

// 创建新对话的新版本页面
export default function NewChatPage() {
  const router = useRouter();
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(true);

  // 获取token用于Authorization头
  const getToken = () => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('token') || '';
    }
    return '';
  };

  useEffect(() => {
    // 创建新对话
    const createNewChat = async () => {
      try {
        setIsLoading(true);
        // TODO: 替换为实际的API调用
        const response = await fetch('http://127.0.0.1:8889/aippt/chat/rag/init', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${getToken()}`
          },
          body: JSON.stringify({})
        });
        
        if (!response.ok) {
          throw new Error(`创建对话失败: ${response.status}`);
        }
        
        const data = await response.json();
        if (data.code === 200 && data.data) {
          // 跳转到新版本聊天页面
          router.push(`/dashboard/chat/v2/${data.data.conversationId}`);
        } else {
          throw new Error('创建对话失败，返回格式错误');
        }
      } catch (error) {
        console.error('创建对话错误:', error);
        toast({
          title: "错误",
          description: "创建新对话失败",
          variant: "destructive"
        });
        // 创建失败后跳回聊天列表
        router.push('/dashboard/chat');
      } finally {
        setIsLoading(false);
      }
    };

    createNewChat();
  }, [router, toast]);

  return (
    <DashboardLayout>
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="text-center">
          <Loader2 className="mx-auto h-12 w-12 animate-spin text-primary mb-4" />
          <h2 className="text-xl font-bold">正在创建新对话...</h2>
          <p className="text-muted-foreground mt-2">请稍候...</p>
        </div>
      </div>
    </DashboardLayout>
  );
}

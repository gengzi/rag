"use client";

import { useState, useRef, useEffect } from "react";
import DashboardLayout from "@/components/layout/dashboard-layout";
import {
  Book,
  MessageSquare,
  ArrowRight,
  Plus,
  Upload,
  Brain,
  Search,
  Sparkles,
} from "lucide-react";
import { api, ApiError } from "@/lib/api";

interface KnowledgeBase {
  id: number;
  name: string;
  description: string;
  documents: any[];
}

interface Chat {
  id: string;
  name: string;
  createDate: string;
  knowledgebaseId: string;
  dialogId: string;
}

interface Stats {
  knowledgeBases: number;
  chats: number;
}

export default function DashboardPage() {
  const [stats, setStats] = useState<Stats>({ knowledgeBases: 0, chats: 0 });

  useEffect(() => {
    const fetchStats = async () => {
        try {
          const [kbData, chatData] = await Promise.all([
            api.get("/api/knowledge-base"),
            api.get("/chat/rag/all"),
          ]);

          setStats({
            knowledgeBases: kbData.length,
            chats: chatData ? chatData.length : 0,
          });
        } catch (error) {
            console.error("Failed to fetch stats:", error);
            if (error instanceof ApiError && error.status === 401) {
              return;
            }
          }
        };

        fetchStats();
    }, []);

  return (
    <DashboardLayout>
      <div className="p-8 max-w-7xl mx-auto">
        {/* Hero Section */}
        <div className="mb-12 rounded-2xl bg-gradient-to-r from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 p-8 shadow-sm">
          <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
            <div className="space-y-4">
              <h1 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-blue-600 to-indigo-500 bg-clip-text text-transparent">
                知识助手
              </h1>
              <p className="text-slate-600 dark:text-slate-300 max-w-xl">
                您的个人AI驱动知识中心。上传文档，创建知识库，并通过自然对话获取即时答案。
              </p>
            </div>
            <a
              href="/dashboard/knowledge/new"
              className="inline-flex items-center justify-center rounded-full bg-blue-600 px-6 py-3 text-sm font-medium text-white hover:bg-blue-700 transition-all shadow-lg shadow-blue-600/20"
            >
              <Plus className="mr-2 h-4 w-4" />
              新建知识库
            </a>
          </div>
        </div>

        {/* Stats Section */}
        <div className="grid gap-6 md:grid-cols-2 mb-12">
          <div className="rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-8 shadow-sm hover:shadow-md transition-all">
            <div className="flex items-center gap-6">
              <div className="rounded-full bg-blue-100 dark:bg-blue-900/30 p-4">
                <Book className="h-8 w-8 text-blue-600 dark:text-blue-400" />
              </div>
              <div>
                <h3 className="text-4xl font-bold text-slate-900 dark:text-white">
                  {stats.knowledgeBases}
                </h3>
                <p className="text-slate-500 dark:text-slate-400 mt-1">
                  知识库
                </p>
              </div>
            </div>
            <a
              href="/dashboard/knowledge"
              className="mt-6 flex items-center text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 text-sm font-medium"
            >
              查看所有知识库
              <ArrowRight className="ml-2 h-4 w-4" />
            </a>
          </div>

          <div className="rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-8 shadow-sm hover:shadow-md transition-all">
            <div className="flex items-center gap-6">
              <div className="rounded-full bg-indigo-100 dark:bg-indigo-900/30 p-4">
                <MessageSquare className="h-8 w-8 text-indigo-600 dark:text-indigo-400" />
              </div>
              <div>
                <h3 className="text-4xl font-bold text-slate-900 dark:text-white">
                  {stats.chats}
                </h3>
                <p className="text-slate-500 dark:text-slate-400 mt-1">
                  对话会话
                </p>
              </div>
            </div>
            <a
              href="/dashboard/chat"
              className="mt-6 flex items-center text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 text-sm font-medium"
            >
              查看所有对话会话
              <ArrowRight className="ml-2 h-4 w-4" />
            </a>
          </div>
        </div>

        {/* Quick Actions */}
        <h2 className="text-2xl font-semibold text-slate-900 dark:text-white mb-6">
          快速操作
        </h2>
        <div className="grid gap-6 md:grid-cols-3 mb-12">
          <a
            href="/dashboard/knowledge/new"
            className="flex flex-col items-center justify-center rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-8 shadow-sm hover:shadow-md transition-all hover:border-blue-500 dark:hover:border-blue-500"
          >
            <div className="rounded-full bg-blue-100 dark:bg-blue-900/30 p-4 mb-4">
              <Brain className="h-8 w-8 text-blue-600 dark:text-blue-400" />
            </div>
            <h3 className="text-lg font-medium text-slate-900 dark:text-white mb-2">
              创建知识库
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 text-center">
              构建一个新的AI驱动知识库
            </p>
          </a>

          <a
            href="/dashboard/knowledge"
            className="flex flex-col items-center justify-center rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-8 shadow-sm hover:shadow-md transition-all hover:border-indigo-500 dark:hover:border-indigo-500"
          >
            <div className="rounded-full bg-indigo-100 dark:bg-indigo-900/30 p-4 mb-4">
              <Upload className="h-8 w-8 text-indigo-600 dark:text-indigo-400" />
            </div>
            <h3 className="text-lg font-medium text-slate-900 dark:text-white mb-2">
              上传文档
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 text-center">
              向您的知识库添加PDF、DOCX、MD或TXT文件
            </p>
          </a>

          <a
            href="/dashboard/chat/new"
            className="flex flex-col items-center justify-center rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-8 shadow-sm hover:shadow-md transition-all hover:border-purple-500 dark:hover:border-purple-500"
          >
            <div className="rounded-full bg-purple-100 dark:bg-purple-900/30 p-4 mb-4">
              <Sparkles className="h-8 w-8 text-purple-600 dark:text-purple-400" />
            </div>
            <h3 className="text-lg font-medium text-slate-900 dark:text-white mb-2">
              开始对话
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 text-center">
              通过AI从您的知识库中获取即时答案
            </p>
          </a>
        </div>

        {/* 入门指南 */}
        <div className="rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-8 shadow-sm">
          <h2 className="text-2xl font-semibold text-slate-900 dark:text-white mb-6 flex items-center">
            <Search className="mr-3 h-5 w-5 text-blue-600 dark:text-blue-400" />
            如何使用
          </h2>
          <div className="space-y-6">
            <div className="flex items-start gap-6 p-6 rounded-xl bg-slate-50 dark:bg-slate-700/30">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-blue-600 text-white font-semibold">
                1
              </div>
              <div>
                <h3 className="font-medium text-lg text-slate-900 dark:text-white mb-2">
                  创建知识库
                </h3>
                <p className="text-slate-600 dark:text-slate-300">
                  首先创建一个新的知识库来组织您的信息。给它一个名称和描述，以帮助您确定其用途。
                </p>
                <a
                  href="/dashboard/knowledge/new"
                  className="mt-4 inline-flex items-center text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 text-sm font-medium"
                >
                  立即创建
                  <ArrowRight className="ml-2 h-4 w-4" />
                </a>
              </div>
            </div>

            <div className="flex items-start gap-6 p-6 rounded-xl bg-slate-50 dark:bg-slate-700/30">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-white font-semibold">
                2
              </div>
              <div>
                <h3 className="font-medium text-lg text-slate-900 dark:text-white mb-2">
                  上传您的文档
                </h3>
                <p className="text-slate-600 dark:text-slate-300">
                  向您的知识库上传PDF、DOCX、MD或TXT文件。我们的系统将处理并索引这些文件，以供AI驱动的检索使用。
                </p>
                <a
                  href="/dashboard/knowledge"
                  className="mt-4 inline-flex items-center text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 text-sm font-medium"
                >
                  上传文档
                  <ArrowRight className="ml-2 h-4 w-4" />
                </a>
              </div>
            </div>

            <div className="flex items-start gap-6 p-6 rounded-xl bg-slate-50 dark:bg-slate-700/30">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-purple-600 text-white font-semibold">
                3
              </div>
              <div>
                <h3 className="font-medium text-lg text-slate-900 dark:text-white mb-2">
                  与您的知识库对话
                </h3>
                <p className="text-slate-600 dark:text-slate-300">
                  开始与您的知识库对话。用自然语言提问，获取基于您文档的准确答案。
                </p>
                <a
                  href="/dashboard/chat/new"
                  className="mt-4 inline-flex items-center text-purple-600 dark:text-purple-400 hover:text-purple-700 dark:hover:text-purple-300 text-sm font-medium"
                >
                  开始对话
                  <ArrowRight className="ml-2 h-4 w-4" />
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}

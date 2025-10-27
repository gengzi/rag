"use client";

import { useState } from "react"; 
import { useRouter } from "next/navigation"; 
import Link from "next/link"; 
import { api, ApiError } from "@/lib/api"; 

interface LoginResponse {
  access_token: string;
  token_type: string;
}

export default function LoginPage() {
  const router = useRouter();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    const formData = new FormData(e.currentTarget);
    const username = formData.get("username");
    const password = formData.get("password");

    try {
      // 由于在api.ts中已经处理了统一响应格式，成功时直接返回data字段的内容
      const data = await api.post("/user/login", { username, password }, {
        headers: { "Content-Type": "application/json" },
      });
      
      // 现在data就是response.data的内容
      if (data && data.token) {
        localStorage.setItem("token", data.token);
        
        // 存储用户信息（包括角色）到localStorage
        // 根据接口返回，role是一个数组，如果包含"ROLE_ADMIN"则为管理员
        const userInfo = {
          username: data.username || (username as string),
          userId: data.id,
          role: data.role && data.role.includes('ROLE_ADMIN') ? 'admin' : 'user'
        };
        localStorage.setItem('userInfo', JSON.stringify(userInfo));
        
        router.push("/dashboard");
      } else {
        setError("登录失败：响应格式无效");
      }
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("登录失败");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen bg-gray-50 flex items-center justify-center px-4 sm:px-6 lg:px-8">
      <div className="w-full max-w-md">
        <div className="bg-white rounded-lg shadow-md p-8 space-y-6">
          <div className="text-center">
            <h1 className="text-3xl font-bold text-gray-900">
              欢迎使用RAG
            </h1>
            <p className="mt-2 text-sm text-gray-600">
              请登录以继续
            </p>
          </div>
          
          <form className="space-y-6" onSubmit={handleSubmit}>
            <div className="space-y-4">
              <div>
                <label htmlFor="username" className="block text-sm font-medium text-gray-700">
                  用户名
                </label>
                <input
                  id="username"
                  name="username"
                  type="text"
                  required
                  disabled={loading}
                  className="mt-1 block w-full px-3 py-2 rounded-md border border-gray-300 shadow-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  placeholder="输入您的用户名"
                />
              </div>
              <div>
                <label htmlFor="password" className="block text-sm font-medium text-gray-700">
                  密码
                </label>
                <input
                  id="password"
                  name="password"
                  type="password"
                  required
                  disabled={loading}
                  className="mt-1 block w-full px-3 py-2 rounded-md border border-gray-300 shadow-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  placeholder="输入您的密码"
                />
              </div>
            </div>
            
            {error && (
              <div className="p-3 rounded-md bg-red-50 text-red-700 text-sm">
                {error}
              </div>
            )}
            
            <button
              type="submit"
              disabled={loading}
              className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-gray-600 hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? "登录中..." : "登录"}
            </button>
          </form>
          
          <div className="text-center">
            <Link href="/register" className="text-sm font-medium text-gray-600 hover:text-gray-500">
              还没有账户？立即创建
            </Link>
          </div>
        </div>
      </div>
    </main>
  );
}

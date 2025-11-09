"use client";

import { useState, useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import { Book, MessageSquare, LogOut, Menu, User, BarChart } from "lucide-react";
import Breadcrumb from "@/components/ui/breadcrumb";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [userRole, setUserRole] = useState<string>('user'); // 默认普通用户
  const [username, setUsername] = useState<string>(''); // 存储用户名

  useEffect(() => {
    // 直接在这里加载用户信息并进行权限检查
    const token = localStorage.getItem("token");
    if (!token) {
      router.push("/login");
      return;
    }
    
    // 从localStorage获取用户信息
    const storedUser = localStorage.getItem('userInfo');
    let currentRole = 'user';
    
    if (storedUser) {
      try {
        const userInfo = JSON.parse(storedUser);
        currentRole = userInfo.role || 'user';
        setUserRole(currentRole);
        setUsername(userInfo.username || '');
      } catch (e) {
        console.error('解析用户信息失败:', e);
      }
    }
    
    // 立即进行访问控制检查
    const restrictedPaths = ['/dashboard/knowledge', '/dashboard/rag-evaluation'];
    const isRestrictedPath = restrictedPaths.some(path => pathname.startsWith(path));
    
    // 只有非管理员尝试访问受限页面时才重定向
    if (isRestrictedPath && currentRole !== 'admin') {
      router.push('/dashboard/chat');
    }
  }, [router, pathname]);

  const handleLogout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("userInfo"); // 清除用户角色信息
    router.push("/login");
  };

  // 根据用户角色生成导航菜单
  const getNavigationItems = () => {
    const baseNav = [
      { name: "对话", href: "/dashboard/chat", icon: MessageSquare },
    ];
    
    // 只有管理员才能看到知识库、RAG评估和用户管理菜单
    if (userRole === 'admin') {
      return [
        { name: "知识库", href: "/dashboard/knowledge", icon: Book },
        ...baseNav,
        { name: "测试页面", href: "/dashboard/test-page", icon: Book },
        { name: "RAG评估", href: "/dashboard/rag-evaluation", icon: BarChart },
        { name: "用户管理", href: "/dashboard/users", icon: User },
      ];
    }
    
    return [
      ...baseNav,
      { name: "测试页面", href: "/dashboard/test-page", icon: Book },
    ];
  };
  
  const navigation = getNavigationItems();

  return (
    <div className="min-h-screen bg-background">
      {/* Mobile menu button */}
      <div className="lg:hidden fixed top-0 left-0 m-4 z-50">
        <button
          onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          className="p-2 rounded-md bg-primary text-primary-foreground"
        >
          <Menu className="h-6 w-6" />
        </button>
      </div>

      {/* Sidebar */}
      <div
        className={`fixed inset-y-0 left-0 z-40 w-64 transform bg-card border-r transition-transform duration-200 ease-in-out lg:translate-x-0 ${isMobileMenuOpen ? "translate-x-0" : "-translate-x-full"}`}
      >
        <div className="flex h-full flex-col">
          {/* Sidebar header */}
          <div className="flex h-16 items-center border-b pl-8">
            <Link
              href="/dashboard"
              className="flex items-center text-lg font-semibold hover:text-primary transition-colors"
            >
              <img
                src="/logo.svg"
                alt="Logo"
                className="w-16 h-16 rounded-lg"
              />
              RAG
            </Link>
          </div>

          {/* Navigation */}
          <nav className="flex-1 space-y-2 px-4 py-6">
            {navigation.map((item, index) => {
              const isActive = pathname.startsWith(item.href);
              return (
                <Link
                  key={`${item.name}-${index}`}
                  href={item.href}
                  className={`group flex items-center rounded-lg px-4 py-3 text-sm font-medium transition-all duration-200 ${isActive
                    ? "bg-gradient-to-r from-primary/10 to-primary/5 text-primary shadow-sm"
                    : "text-muted-foreground hover:bg-accent/50 hover:text-foreground hover:shadow-sm"}`}
                >
                  <item.icon
                    className={`mr-3 h-5 w-5 transition-transform duration-200 ${isActive
                      ? "text-primary scale-110"
                      : "group-hover:scale-110"}`}
                  />
                  <span className="font-medium">{item.name}</span>
                  {isActive && (
                    <div className="ml-auto h-1.5 w-1.5 rounded-full bg-primary" />
                  )}
                </Link>
              );
            })}
          </nav>
          {/* User profile and logout */}
          <div className="border-t p-4 space-y-4">
            <div className="flex items-center justify-between px-3 py-2">
              <div className="flex items-center">
                <User className="h-5 w-5 text-muted-foreground mr-2" />
                <span className="text-sm font-medium">{username || '用户'}</span>
              </div>
              <span className="text-xs px-2 py-0.5 rounded-full bg-primary/10 text-primary">
                {userRole === 'admin' ? '管理员' : '普通用户'}
              </span>
            </div>
            <button
              onClick={handleLogout}
              className="flex w-full items-center rounded-lg px-3 py-2.5 text-sm font-medium text-destructive hover:bg-destructive/10 transition-colors duration-200"
            >
              <LogOut className="mr-3 h-4 w-4" />
              退出登录
            </button>
          </div>
        </div>
      </div>

      {/* Main content */}
      <div className="lg:pl-64">
        <main className="min-h-screen py-6 px-4 sm:px-6 lg:px-8">
          <Breadcrumb />
          {children}
        </main>
      </div>
    </div>
  );
}

export const dashboardConfig = {
  mainNav: [],
  sidebarNav: [
    {
      title: "知识库",
      href: "/dashboard/knowledge",
      icon: "database",
    },
    {
      title: "对话",
      href: "/dashboard/chat",
      icon: "messageSquare",
    },
    {
      title: "RAG评估",
      href: "/dashboard/rag-evaluation",
      icon: "barChart",
    },
    {
      title: "用户管理",
      href: "/dashboard/users",
      icon: "user",
    },
    {
      title: "API 密钥",
      href: "/dashboard/api-keys",
      icon: "key",
    },
  ],
};

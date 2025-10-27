"use client";

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { User, Plus, Edit, Trash2, Search, ChevronLeft, ChevronRight, Check, X } from 'lucide-react';
import DashboardLayout from '@/components/layout/dashboard-layout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
  import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
  import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
  import { MultiSelect } from "@/components/ui/multi-select";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { useToast } from '@/components/ui/use-toast';
import { Badge } from '@/components/ui/badge';
import { api } from '@/lib/api';
import API_CONFIG from '@/lib/config';

// 知识库类型定义
interface KnowledgeBase {
  kbId: string;
  kbName: string;
}

// 用户类型定义
interface User {
  id: string;
  username: string;
  nickname: string;
  isSuperuser: boolean;
  createTime: string | null;
  status: string;
  knowledgeIds?: string[]; // 关联的知识库ID列表
}



export default function UsersPage() {
  const router = useRouter();
  const { toast } = useToast();
  const [users, setUsers] = useState<User[]>([]);
  const [filteredUsers, setFilteredUsers] = useState<User[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [isLoadingKnowledgeBases, setIsLoadingKnowledgeBases] = useState(false);
  
  // 表单状态
  const [formData, setFormData] = useState({
    username: '',
    nickname: '',
    isSuperuser: false,
    password: '',
    knowledgeIds: [] as string[]
  });
  
  // 组件加载时获取知识库数据
  useEffect(() => {
    fetchKnowledgeBases();
  }, []);



  // 获取知识库列表数据
  const fetchKnowledgeBases = async () => {
    try {
      setIsLoadingKnowledgeBases(true);
      // 修改API路径，使用更直接的方式获取知识库
      const response = await api.get('/api/knowledge/all', {
        headers: {
          accept: '*/*'
        }
      });
      
      console.log('知识库API响应数据:', response);
      
      // 更全面地处理API响应，适配多种可能的格式
      if (response) {
        // 1. 直接检查data是否为数组（适配API返回的格式）
        if (Array.isArray(response.data)) {
          setKnowledgeBases(response.data);
          console.log('设置知识库数据 - 直接数组格式');
        }
        // 2. 检查response.data.data是否为数组
        else if (response.data && Array.isArray(response.data.data)) {
          setKnowledgeBases(response.data.data);
          console.log('设置知识库数据 - data.data格式');
        }
        // 3. 检查response是否直接为数组
        else if (Array.isArray(response)) {
          setKnowledgeBases(response);
          console.log('设置知识库数据 - 响应直接为数组');
        } else {
          console.warn('无法识别的知识库响应格式', response);
          setKnowledgeBases([]);
          toast({
            title: "警告",
            description: "无法识别的知识库响应格式",
            variant: "destructive"
          });
        }
      }
    } catch (error) {
      console.error('获取知识库列表失败:', error);
      setKnowledgeBases([]);
      toast({
        title: "错误",
        description: "获取知识库列表失败，请稍后重试",
        variant: "destructive"
      });
    } finally {
      setIsLoadingKnowledgeBases(false);
    }
  };

  // 获取用户列表数据
  const fetchUsers = async () => {
    try {
      const response = await api.get('/list', {
        params: {
          username: searchQuery || undefined,
          page: currentPage - 1, // 后端使用0-based分页
          size: itemsPerPage
        },
        headers: {
          accept: '*/*'
        }
      });
      
      console.log('API响应数据:', response);
      
      // 更宽松地处理API响应，支持多种可能的格式
      if (response) {
        // 尝试从不同的位置获取用户数据
        if (response.data && Array.isArray(response.data.content)) {
          setUsers(response.data.content);
        } else if (response.content && Array.isArray(response.content)) {
          setUsers(response.content);
        } else if (Array.isArray(response)) {
          setUsers(response);
        } else {
          console.warn('无法识别的响应格式');
          setUsers([]);
          toast({
            title: "警告",
            description: "无法识别的响应格式",
            variant: "destructive"
          });
        }
      } else {
        console.warn('响应为空');
        setUsers([]);
        toast({
          title: "警告",
          description: "响应为空",
          variant: "destructive"
        });
      }
    } catch (error) {
      console.error('获取用户列表失败:', error);
      toast({
        title: "错误",
        description: "获取用户列表失败，请稍后重试",
        variant: "destructive"
      });
      setUsers([]);
    }
  };

  // 页面加载时获取知识库数据
  useEffect(() => {
    fetchKnowledgeBases();
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [currentPage, itemsPerPage, searchQuery]);

  useEffect(() => {
    // 过滤用户，确保users是数组
    if (!Array.isArray(users)) {
      setFilteredUsers([]);
      return;
    }
    
    const filtered = users.filter(user => 
      user.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (user.nickname && user.nickname.toLowerCase().includes(searchQuery.toLowerCase()))
    );
    setFilteredUsers(filtered);
    setCurrentPage(1); // 重置到第一页
  }, [searchQuery, users]);

  // 分页逻辑
  const indexOfLastItem = currentPage * itemsPerPage;
  const indexOfFirstItem = indexOfLastItem - itemsPerPage;
  const currentItems = filteredUsers.slice(indexOfFirstItem, indexOfLastItem);
  const totalPages = Math.ceil(filteredUsers.length / itemsPerPage);

  // 处理添加用户
  const handleAddUser = async () => {
    // 表单验证
    if (!formData.username || !formData.nickname || !formData.password || formData.knowledgeIds.length === 0) {
      toast({
        title: "错误",
        description: "请填写所有必填字段",
        variant: "destructive"
      });
      return;
    }
    
    try {
      await api.post('/user/add', {
        username: formData.username,
        nickname: formData.nickname,
        password: formData.password,
        isSuperuser: formData.isSuperuser,
        knowledgeIds: formData.knowledgeIds
      });
      
      setIsAddDialogOpen(false);
      resetForm();
      
      // 重新获取用户列表
      fetchUsers();
      
      toast({
        title: "成功",
        description: "用户添加成功",
        variant: "default"
      });
    } catch (error) {
      console.error('添加用户失败:', error);
      toast({
        title: "错误",
        description: "添加用户失败，请稍后重试",
        variant: "destructive"
      });
    }
  };

  // 处理编辑用户
  const handleEditUser = async () => {
    if (!selectedUser) return;
    
    // 表单验证（密码可选）
    if (!formData.username || !formData.nickname || formData.knowledgeIds.length === 0) {
      toast({
        title: "错误",
        description: "请填写所有必填字段",
        variant: "destructive"
      });
      return;
    }
    
    try {
      await api.put(`/user/${selectedUser.id}`, {
        username: formData.username,
        nickname: formData.nickname,
        isSuperuser: formData.isSuperuser,
        knowledgeIds: formData.knowledgeIds,
        ...(formData.password ? { password: formData.password } : {})
      });
      
      setIsEditDialogOpen(false);
      resetForm();
      
      // 重新获取用户列表
      fetchUsers();
      
      toast({
        title: "成功",
        description: "用户信息更新成功",
        variant: "default"
      });
    } catch (error) {
      console.error('更新用户失败:', error);
      toast({
        title: "错误",
        description: "更新用户失败，请稍后重试",
        variant: "destructive"
      });
    }
  };

  // 处理删除用户
  const handleDeleteUser = async (id: string) => {
    if (id === '1') { // 防止删除管理员账户
      toast({
        title: "错误",
        description: "不能删除管理员账户",
        variant: "destructive"
      });
      return;
    }
    
    try {
      await api.delete(`/user/${id}`);
      
      // 重新获取用户列表
      fetchUsers();
      
      toast({
        title: "成功",
        description: "用户删除成功",
        variant: "default"
      });
    } catch (error) {
      console.error('删除用户失败:', error);
      toast({
        title: "错误",
        description: "删除用户失败，请稍后重试",
        variant: "destructive"
      });
    }
  };

  // 打开编辑对话框
  const openEditDialog = (user: User) => {
    setSelectedUser(user);
    setFormData({
      username: user.username,
      nickname: user.nickname,
      isSuperuser: user.isSuperuser,
      password: '',
      knowledgeIds: user.knowledgeIds || [] // 使用用户实际关联的知识库ID数组
    });
    setIsEditDialogOpen(true);
  };

  // 重置表单
  const resetForm = () => {
    setFormData({
      username: '',
      nickname: '',
      isSuperuser: false,
      password: '',
      knowledgeIds: [] as string[]
    });
    setSelectedUser(null);
  };

  // 表单输入变化处理
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  // 处理选择变化（用于多选下拉框）
  const handleSelectChange = (name: string, value: string[]) => {
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  // 移除不再需要的handleRoleChange函数

  // 格式化日期
  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  // 分页导航
  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">用户管理</h1>
            <p className="text-muted-foreground mt-1">管理系统用户，设置权限和角色</p>
          </div>
          <Dialog open={isAddDialogOpen} onOpenChange={(open) => {
            if (open) {
              // 打开对话框时重新获取知识库数据
              fetchKnowledgeBases();
            }
            setIsAddDialogOpen(open);
          }}>
            <DialogTrigger asChild>
              <Button className="gap-2">
                <Plus className="h-4 w-4" />
                添加用户
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-md">
              <DialogHeader>
                <DialogTitle>添加新用户</DialogTitle>
              </DialogHeader>
              <div className="grid gap-4 py-4">
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label htmlFor="username" className="text-right">
                    用户名
                  </Label>
                  <Input
                    id="username"
                    name="username"
                    value={formData.username}
                    onChange={handleInputChange}
                    required
                    className="col-span-3"
                  />
                </div>
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label htmlFor="nickname" className="text-right">
                    昵称
                  </Label>
                  <Input
                    id="nickname"
                    name="nickname"
                    value={formData.nickname}
                    onChange={handleInputChange}
                    required
                    className="col-span-3"
                  />
                </div>
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label htmlFor="password" className="text-right">
                    密码
                  </Label>
                  <Input
                    id="password"
                    name="password"
                    type="password"
                    value={formData.password}
                    onChange={handleInputChange}
                    required
                    className="col-span-3"
                  />
                </div>
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label htmlFor="isSuperuser" className="text-right">
                    管理员权限
                  </Label>
                  <Select
                    value={formData.isSuperuser.toString()}
                    onValueChange={(value) => setFormData(prev => ({ ...prev, isSuperuser: value === 'true' }))}
                    required
                  >
                    <SelectTrigger className="col-span-3">
                      <SelectValue placeholder="选择权限" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="false">普通用户</SelectItem>
                      <SelectItem value="true">管理员</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label htmlFor="knowledgeIds" className="text-right">
                    知识库 (可多选)
                  </Label>
                  <div className="col-span-3">
                    {isLoadingKnowledgeBases ? (
                      <div className="h-10 rounded-md border border-input bg-background px-3 py-2 text-sm text-muted-foreground">
                        加载中...
                      </div>
                    ) : knowledgeBases.length > 0 ? (
                      <MultiSelect
                        options={knowledgeBases.map(kb => ({ value: kb.kbId, label: kb.kbName }))}
                        selectedValues={formData.knowledgeIds}
                        onSelectionChange={(values) => setFormData(prev => ({ ...prev, knowledgeIds: values }))}
                        placeholder="选择知识库"
                      />
                    ) : (
                      <div className="h-10 rounded-md border border-input bg-background px-3 py-2 text-sm text-muted-foreground">
                        暂无知识库
                      </div>
                    )}
                  </div>
                </div>
              </div>
              <DialogFooter>
                <Button type="button" variant="secondary" onClick={() => setIsAddDialogOpen(false)}>
                  取消
                </Button>
                <Button type="button" onClick={handleAddUser}>
                  保存
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>

        {/* 搜索框 */}
        <div className="relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="搜索用户名或邮箱..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-10 w-full max-w-md"
          />
        </div>

        {/* 用户表格 */}
        <Card>
          <CardContent className="p-0">
            <div className="rounded-md border">
              <table className="w-full text-sm">
                <thead className="bg-muted">
                  <tr>
                  <th className="px-6 py-3 text-left font-medium">用户名</th>
                  <th className="px-6 py-3 text-left font-medium">昵称</th>
                  <th className="px-6 py-3 text-left font-medium">管理员权限</th>
                  <th className="px-6 py-3 text-left font-medium">关联知识库</th>
                  <th className="px-6 py-3 text-left font-medium">创建时间</th>
                  <th className="px-6 py-3 text-right font-medium">操作</th>
                </tr>
                </thead>
                <tbody className="divide-y">
                  {currentItems.length > 0 ? (
                    currentItems.map((user) => (
                      <tr key={user.id} className="hover:bg-accent/50 transition-colors">
                        <td className="px-6 py-4 font-medium">{user.username}</td>
                        <td className="px-6 py-4 text-muted-foreground">{user.nickname || '-'}</td>
                        <td className="px-6 py-4">
                          <Badge variant={user.isSuperuser ? 'default' : 'outline'}>
                            {user.isSuperuser ? '管理员' : '普通用户'}
                          </Badge>
                        </td>
                        <td className="px-6 py-4">
                          {user.knowledgeIds && user.knowledgeIds.length > 0 ? (
                            <div className="flex flex-wrap gap-1">
                              {Array.isArray(user.knowledgeIds) ? (
                                user.knowledgeIds.map((kbId, index) => {
                                  // 查找对应的知识库名称，如果找不到则显示ID
                                  const kb = knowledgeBases.find(base => base.kbId === kbId);
                                  return (
                                    <Badge key={`${user.id}-kb-${index}`} variant="secondary">
                                      {kb ? kb.kbName : kbId}
                                    </Badge>
                                  );
                                })
                              ) : (
                                // 处理字符串类型的knowledgeIds
                                <Badge variant="secondary">{user.knowledgeIds}</Badge>
                              )}
                            </div>
                          ) : (
                            <span className="text-muted-foreground">无</span>
                          )}
                        </td>
                        <td className="px-6 py-4 text-muted-foreground">
                          {user.createTime ? formatDate(user.createTime) : '-'}
                        </td>
                        <td className="px-6 py-4 text-right space-x-2">
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-8 w-8"
                            onClick={() => openEditDialog(user)}
                          >
                            <Edit className="h-4 w-4" />
                            <span className="sr-only">编辑</span>
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-8 w-8 text-destructive hover:bg-destructive/10"
                            onClick={() => handleDeleteUser(user.id)}
                            disabled={user.id === '1'}
                          >
                            <Trash2 className="h-4 w-4" />
                            <span className="sr-only">删除</span>
                          </Button>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={6} className="px-6 py-10 text-center text-muted-foreground">
                        {searchQuery ? '没有找到匹配的用户' : '暂无用户数据'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </CardContent>
          <CardFooter className="flex flex-col sm:flex-row justify-between items-center py-4">
            <div className="text-sm text-muted-foreground mb-4 sm:mb-0">
              显示 {indexOfFirstItem + 1} - {Math.min(indexOfLastItem, filteredUsers.length)} 共 {filteredUsers.length} 条
            </div>
            <div className="flex items-center space-x-2">
              <Select value={itemsPerPage.toString()} onValueChange={(value) => setItemsPerPage(Number(value))}>
                <SelectTrigger className="w-24">
                  <SelectValue placeholder="每页条数" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10条/页</SelectItem>
                  <SelectItem value="20">20条/页</SelectItem>
                  <SelectItem value="50">50条/页</SelectItem>
                </SelectContent>
              </Select>
              <div className="flex items-center space-x-1">
                <Button
                  variant="outline"
                  size="icon"
                  className="h-8 w-8"
                  onClick={() => handlePageChange(currentPage - 1)}
                  disabled={currentPage === 1}
                >
                  <ChevronLeft className="h-4 w-4" />
                  <span className="sr-only">上一页</span>
                </Button>
                {Array.from({ length: totalPages }, (_, index) => (
                  <Button
                    key={index}
                    variant={currentPage === index + 1 ? 'default' : 'outline'}
                    size="sm"
                    className="h-8 w-8 p-0"
                    onClick={() => handlePageChange(index + 1)}
                  >
                    {index + 1}
                  </Button>
                ))}
                <Button
                  variant="outline"
                  size="icon"
                  className="h-8 w-8"
                  onClick={() => handlePageChange(currentPage + 1)}
                  disabled={currentPage === totalPages}
                >
                  <ChevronRight className="h-4 w-4" />
                  <span className="sr-only">下一页</span>
                </Button>
              </div>
            </div>
          </CardFooter>
        </Card>

        {/* 编辑用户对话框 */}
        <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>编辑用户</DialogTitle>
            </DialogHeader>
            <div className="grid gap-4 py-4">
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="edit-username" className="text-right">
                  用户名
                </Label>
                <Input
                  id="edit-username"
                  name="username"
                  value={formData.username}
                  onChange={handleInputChange}
                  required
                  className="col-span-3"
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                  <Label htmlFor="edit-nickname" className="text-right">
                    昵称
                  </Label>
                  <Input
                    id="edit-nickname"
                    name="nickname"
                    value={formData.nickname}
                    onChange={handleInputChange}
                    required
                    className="col-span-3"
                  />
                </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="edit-password" className="text-right">
                  密码 (留空不修改)
                </Label>
                <Input
                  id="edit-password"
                  name="password"
                  type="password"
                  value={formData.password}
                  onChange={handleInputChange}
                  className="col-span-3"
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                  <Label htmlFor="edit-isSuperuser" className="text-right">
                    管理员权限
                  </Label>
                  <Select
                    value={formData.isSuperuser.toString()}
                    onValueChange={(value) => setFormData(prev => ({ ...prev, isSuperuser: value === 'true' }))}
                    required
                  >
                    <SelectTrigger className="col-span-3">
                      <SelectValue placeholder="选择权限" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="false">普通用户</SelectItem>
                      <SelectItem value="true">管理员</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label htmlFor="edit-knowledgeIds" className="text-right">
                    知识库 (可多选)
                  </Label>
                  <div className="col-span-3">
                    {isLoadingKnowledgeBases ? (
                      <div className="h-10 rounded-md border border-input bg-background px-3 py-2 text-sm text-muted-foreground">
                        加载中...
                      </div>
                    ) : knowledgeBases.length > 0 ? (
                      <MultiSelect
                        options={knowledgeBases.map(kb => ({ value: kb.kbId, label: kb.kbName }))}
                        selectedValues={formData.knowledgeIds}
                        onSelectionChange={(values) => setFormData(prev => ({ ...prev, knowledgeIds: values }))}
                        placeholder="选择知识库"
                      />
                    ) : (
                      <div className="h-10 rounded-md border border-input bg-background px-3 py-2 text-sm text-muted-foreground">
                        暂无知识库
                      </div>
                    )}
                  </div>
                </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="secondary" onClick={() => setIsEditDialogOpen(false)}>
                取消
              </Button>
              <Button type="button" onClick={handleEditUser}>
                保存更改
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </DashboardLayout>
  );
}
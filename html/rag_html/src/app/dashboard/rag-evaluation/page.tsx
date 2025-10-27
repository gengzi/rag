'use client';

import React, { useState, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import rehypeRaw from 'rehype-raw';
import DashboardLayout from '@/components/layout/dashboard-layout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { MultiSelect } from '@/components/ui/multi-select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Radar } from 'recharts';
import { Search, Download, RefreshCw, PlusCircle, X, Play } from 'lucide-react';
import { fetchApi } from '@/lib/api';
import { useToast } from '@/components/ui/use-toast';

// 评估数据现在从API获取

// 默认图表数据
const defaultChartData = [
  { name: 'Mon', 准确率: 90, 完整度: 85, 相关性: 92, 一致性: 88, 及时性: 94, 可理解性: 91 },
  { name: 'Tue', 准确率: 92, 完整度: 88, 相关性: 94, 一致性: 90, 及时性: 95, 可理解性: 93 },
  { name: 'Wed', 准确率: 88, 完整度: 86, 相关性: 90, 一致性: 87, 及时性: 92, 可理解性: 90 },
  { name: 'Thu', 准确率: 95, 完整度: 90, 相关性: 96, 一致性: 93, 及时性: 97, 可理解性: 95 },
  { name: 'Fri', 准确率: 93, 完整度: 89, 相关性: 95, 一致性: 91, 及时性: 96, 可理解性: 94 },
  { name: 'Sat', 准确率: 94, 完整度: 91, 相关性: 97, 一致性: 92, 及时性: 98, 可理解性: 96 },
  { name: 'Sun', 准确率: 96, 完整度: 92, 相关性: 98, 一致性: 94, 及时性: 99, 可理解性: 97 },
];

// 雷达图数据
const radarData = [
  { subject: '准确率', A: 94, fullMark: 100 },
  { subject: '完整度', A: 89, fullMark: 100 },
  { subject: '相关性', A: 95, fullMark: 100 },
  { subject: '一致性', A: 91, fullMark: 100 },
  { subject: '及时性', A: 96, fullMark: 100 },
  { subject: '可理解性', A: 94, fullMark: 100 },
];

export default function RAGEvaluationPage() {
  const { toast } = useToast();
  const [chartData, setChartData] = useState(defaultChartData);
  const [radarChartData, setRadarChartData] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 批次相关状态
  const [batchNumbers, setBatchNumbers] = useState<Array<{value: string, label: string}>>([]);
  const [selectedBatch, setSelectedBatch] = useState('all');
  
  // 辅助函数：解析JSON字符串
  const parseJson = (str: string) => {
    try {
      return JSON.parse(str);
    } catch {
      return str;
    }
  };
  
  // 辅助函数：格式化时间
  const formatTime = (timeStr: string) => {
    try {
      const date = new Date(timeStr);
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      });
    } catch {
      return timeStr;
    }
  };
  const [isBatchLoading, setIsBatchLoading] = useState(false);
  const [optimizationAdvice, setOptimizationAdvice] = useState<string>('');
  
  // 分页相关状态
  const [evaluationData, setEvaluationData] = useState<any[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalItems, setTotalItems] = useState(0);
  const [isDataLoading, setIsDataLoading] = useState(false);
  
  // 详情弹窗状态
  const [showDetailDialog, setShowDetailDialog] = useState(false);
  const [selectedItem, setSelectedItem] = useState<any>(null);
  
  // 新建评估弹窗状态
  const [showNewEvaluationDialog, setShowNewEvaluationDialog] = useState(false);
  const [batchNum, setBatchNum] = useState('');
  const [selectedKnowledgeBase, setSelectedKnowledgeBase] = useState('');
  const [selectedSingleDocument, setSelectedSingleDocument] = useState<string[]>([]);
  const [selectedMultipleDocuments, setSelectedMultipleDocuments] = useState<string[][]>([[]]);
  const [isColloquialQuestion, setIsColloquialQuestion] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  // 开始评估弹窗状态
  const [showStartEvaluationDialog, setShowStartEvaluationDialog] = useState(false);
  const [startEvaluationBatchNum, setStartEvaluationBatchNum] = useState('');
  const [isStartEvaluationSubmitting, setIsStartEvaluationSubmitting] = useState(false);
  const [untrainedBatchNumbers, setUntrainedBatchNumbers] = useState<Array<{value: string, label: string}>>([]);
  const [isUntrainedBatchLoading, setIsUntrainedBatchLoading] = useState(false);
  
  // 获取未训练批次数据
  const fetchUntrainedBatchNumbers = async () => {
    setIsUntrainedBatchLoading(true);
    try {
      // 使用公共API工具函数获取未训练批次数据，添加isUntrainedBatch=true参数
      const response = await fetchApi('/evaluate/get/batchnums', {
        method: 'GET',
        params: {
          isUntrainedBatch: true
        }
      });
      
      // 格式化批次数据为Select组件需要的格式
      const formattedBatches = response.map((batch: string) => ({
        value: batch,
        label: `批次${batch}`
      }));
      
      setUntrainedBatchNumbers(formattedBatches);
      return formattedBatches;
    } catch (err) {
      console.error('获取未训练批次数据失败:', err);
      return [];
    } finally {
      setIsUntrainedBatchLoading(false);
    }
  };
  
  // 知识库数据状态
  const [knowledgeBases, setKnowledgeBases] = useState<Array<{value: string, label: string}>>([]);
  const [isKnowledgeBaseLoading, setIsKnowledgeBaseLoading] = useState(false);
  
  // 文档数据状态
  const [documents, setDocuments] = useState<Array<{value: string, label: string}>>([]);
  const [isDocumentsLoading, setIsDocumentsLoading] = useState(false);
  
  // 获取知识库数据
  const fetchKnowledgeBases = async () => {
    setIsKnowledgeBaseLoading(true);
    try {
      const response = await fetchApi('/api/knowledge-base');
      console.log('获取到的知识库数据:', response);
      if (response.success && Array.isArray(response.data)) {
        // 将API返回的数据转换为Select组件需要的格式
        const formattedKnowledgeBases = response.data.map((kb: any) => ({
          value: kb.id,
          label: kb.name
        }));
        console.log('格式化后的知识库数据:', formattedKnowledgeBases);
        setKnowledgeBases(formattedKnowledgeBases);
      } else {
        // 如果响应格式不符合预期，直接使用响应数据（兼容知识库页面的实现）
        if (Array.isArray(response)) {
          const formattedKnowledgeBases = response.map((kb: any) => ({
            value: kb.id,
            label: kb.name
          }));
          console.log('直接格式化的知识库数据:', formattedKnowledgeBases);
          setKnowledgeBases(formattedKnowledgeBases);
        }
      }
    } catch (error) {
      console.error('获取知识库数据失败:', error);
    } finally {
      setIsKnowledgeBaseLoading(false);
    }
  };
  
  // 根据知识库ID获取文档列表
  const fetchDocumentsByKnowledgeBase = async (kbId: string) => {
    if (!kbId) {
      setDocuments([]);
      return;
    }
    
    setIsDocumentsLoading(true);
    try {
      const response = await fetchApi(`/document/chunks?kbId=${kbId}`);
      console.log('获取到的文档数据:', response);
      
      if (response.success && Array.isArray(response.data)) {
        // 将API返回的数据转换为Select组件需要的格式
        const formattedDocuments = response.data.map((doc: any) => ({
          value: doc.id,
          label: doc.name
        }));
        console.log('格式化后的文档数据:', formattedDocuments);
        setDocuments(formattedDocuments);
      } else {
        // 如果响应格式不符合预期，直接使用响应数据
        if (Array.isArray(response)) {
          const formattedDocuments = response.map((doc: any) => ({
            value: doc.id,
            label: doc.name
          }));
          console.log('直接格式化的文档数据:', formattedDocuments);
          setDocuments(formattedDocuments);
        } else {
          setDocuments([]);
        }
      }
    } catch (error) {
      console.error('获取文档数据失败:', error);
      setDocuments([]);
    } finally {
      setIsDocumentsLoading(false);
    }
  };
  
  // 获取批次数据
  const fetchBatchNumbers = async () => {
    setIsBatchLoading(true);
    try {
      // 使用公共API工具函数获取批次数据
      const response = await fetchApi('/evaluate/get/batchnums', {
        method: 'GET'
      });
      
      // 格式化批次数据为Select组件需要的格式
      const formattedBatches = response.map((batch: string) => ({
        value: batch,
        label: `批次${batch}`
      }));
      
      setBatchNumbers(formattedBatches);
      return formattedBatches;
    } catch (err) {
      console.error('获取批次数据失败:', err);
      return [];
    } finally {
      setIsBatchLoading(false);
    }
  };
  
  // 获取评估结果分页数据
  const fetchEvaluationData = async (page = 0, batchNum = selectedBatch) => {
    setIsDataLoading(true);
    try {
      // 如果选择全部批次，默认使用第一个批次
      const targetBatchNum = batchNum === 'all' && batchNumbers.length > 0 ? batchNumbers[0].value : batchNum;
      
      // 使用公共API工具函数调用分页数据接口
      const response = await fetchApi(`/evaluate/get/statistics/batchnum`, {
        method: 'GET',
        params: {
          batchNum: targetBatchNum === 'all' ? '1' : targetBatchNum,
          page: page,
          size: pageSize
        }
      });
      
      console.log('API响应数据:', response);
      
      // 简化响应处理，直接使用响应数据作为评估数据
      // 假设API直接返回数据数组
      const data = Array.isArray(response) ? response : (response.content || []);
      setEvaluationData(data);
      
      // 设置总数据量
      setTotalItems(response.totalElements || data.length);
      setCurrentPage(page);
    } catch (err) {
      console.error('获取评估数据失败:', err);
      // 出错时使用空数据
      setEvaluationData([]);
      setTotalItems(0);
    } finally {
      setIsDataLoading(false);
    }
  };

  // 获取折线图数据
  const fetchChartData = async () => {
    setIsLoading(true);
    setError(null);
    try {
      // 使用公共API工具函数，自动处理认证信息和基础URL
      const responseData = await fetchApi('/evaluate/statistics/linechart', {
        method: 'GET'
      });
      
      // 转换API响应数据为图表所需格式
      const transformedData = responseData.map((item: any) => ({
        name: `批次${item.batchNum}`, // 使用batchNum作为x轴值
        忠实度: parseFloat(item.faithfulnessAverageScore),
        答案相关性: parseFloat(item.answerRelevancyAverageScore),
        答案相似度: parseFloat(item.answerSimilarityAverageScore),
        上下文召回率: parseFloat(item.contextRecallAverageScore),
        上下文精确度: parseFloat(item.contextPrecisionAverageScore),
        上下文相关性: parseFloat(item.contextRelevancyAverageScore),
        总体评分: parseFloat(item.overallScore)
      }));
      
      setChartData(transformedData);
      
      // 处理雷达图数据，支持多个批次对比
      if (responseData.length > 0) {
        // 获取最近几个批次的数据（最多显示5个批次以避免图表过于复杂）
        const recentBatches = responseData.slice(-5);
        
        // 创建雷达图数据结构
        const subjects = ['忠实度', '答案相关性', '答案相似度', '上下文召回率', '上下文精确度', '上下文相关性'];
        const newRadarData = subjects.map(subject => {
          const subjectData: any = { subject, fullMark: 100 };
          
          // 为每个批次添加对应指标的值
          recentBatches.forEach((batch: any) => {
            const batchKey = `batch_${batch.batchNum}`;
            let value = 0;
            
            switch(subject) {
              case '忠实度':
                value = parseFloat(batch.faithfulnessAverageScore) * 100;
                break;
              case '答案相关性':
                value = parseFloat(batch.answerRelevancyAverageScore) * 100;
                break;
              case '答案相似度':
                value = parseFloat(batch.answerSimilarityAverageScore) * 100;
                break;
              case '上下文召回率':
                value = parseFloat(batch.contextRecallAverageScore) * 100;
                break;
              case '上下文精确度':
                value = parseFloat(batch.contextPrecisionAverageScore) * 100;
                break;
              case '上下文相关性':
                value = parseFloat(batch.contextRelevancyAverageScore) * 100;
                break;
            }
            
            subjectData[batchKey] = value;
          });
          
          return subjectData;
        });
        
        setRadarChartData(newRadarData);
      }
      
      // 设置优化建议 - 显示最新时间批次的内容
      if (responseData.length > 0) {
        // 按创建时间排序，获取最新的批次
        const sortedByTime = [...responseData].sort((a, b) => 
          new Date(b.createTime).getTime() - new Date(a.createTime).getTime()
        );
        if (sortedByTime[0].directionImprovement) {
          setOptimizationAdvice(sortedByTime[0].directionImprovement);
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '获取数据时发生错误');
      // 出错时使用默认数据
      setChartData(defaultChartData);
    } finally {
      setIsLoading(false);
    }
  };

  // 初始加载数据
  useEffect(() => {
    let isMounted = true;
    
    const initializeData = async () => {
      if (isMounted) {
        try {
          // 并行获取批次数据、图表数据和知识库数据
          const [batches] = await Promise.all([
            fetchBatchNumbers(),
            fetchChartData(),
            fetchKnowledgeBases()
          ]);
          
          // 批次数据获取后，立即获取评估数据
          if (batches.length > 0 || selectedBatch !== 'all') {
            await fetchEvaluationData(0, selectedBatch);
          }
        } catch (error) {
          console.error('初始化数据失败:', error);
        }
      }
    };
    
    initializeData();
    
    return () => {
      isMounted = false;
    };
  }, []);
  
  // 当批次或页面大小改变时重新获取数据
  useEffect(() => {
    let isMounted = true;
    
    const loadData = async () => {
      if ((batchNumbers.length > 0 || selectedBatch !== 'all') && isMounted) {
        await fetchEvaluationData(currentPage, selectedBatch);
      }
    };
    
    loadData();
    
    return () => {
      isMounted = false;
    };
  }, [selectedBatch, pageSize, currentPage]);
  
  // 处理批次选择变化
  const handleBatchChange = (batchValue: string) => {
    setSelectedBatch(batchValue);
    setCurrentPage(0); // 重置为第一页
  };
  
  // 处理行点击事件，打开详情弹窗
  const handleRowClick = (item: any) => {
    setSelectedItem(item);
    setShowDetailDialog(true);
  };
  
  // 关闭详情弹窗
  const handleCloseDialog = () => {
    setShowDetailDialog(false);
    setSelectedItem(null);
  };
  

  
  // 打开新建评估弹窗
  const handleOpenNewEvaluationDialog = async () => {
    // 每次打开弹窗时刷新知识库数据
    await fetchKnowledgeBases();
    resetForm();
    setShowNewEvaluationDialog(true);
  };
  
  // 重置表单
  const resetForm = () => {
    setBatchNum('');
    setSelectedKnowledgeBase('');
    setSelectedSingleDocument([]);
    setSelectedMultipleDocuments([[]]);
    setIsColloquialQuestion(false);
    // 重置文档列表
    setDocuments([]);
  };
  
  // 添加新的多选下拉框
  const addMultipleDocumentSelector = () => {
    setSelectedMultipleDocuments(prev => [...prev, []]);
  };
  
  // 删除指定索引的多选下拉框
  const removeMultipleDocumentSelector = (index: number) => {
    if (selectedMultipleDocuments.length > 1) {
      setSelectedMultipleDocuments(prev => prev.filter((_, i) => i !== index));
    }
  };
  
  // 更新指定索引的多选下拉框的值
  const updateMultipleDocumentSelector = (index: number, values: string[]) => {
    setSelectedMultipleDocuments(prev => {
      const newSelected = [...prev];
      newSelected[index] = values;
      return newSelected;
    });
  };
  
  // 处理知识库选择变化
  const handleKnowledgeBaseChange = (value: string) => {
    setSelectedKnowledgeBase(value);
    // 根据选择的知识库获取文档列表
    fetchDocumentsByKnowledgeBase(value);
  };
  
  // 关闭新建评估弹窗
  const handleCloseNewEvaluationDialog = () => {
    setShowNewEvaluationDialog(false);
    resetForm();
  };
  
  // 处理表单提交
  const handleSubmitEvaluation = async () => {
    setIsSubmitting(true);
    try {
      // 构建请求数据，符合新的API接口要求
      const evaluationData = {
        batchNum: batchNum,
        kbId: selectedKnowledgeBase,
        singleDocumentIds: selectedSingleDocument,
        multipleDocumentIds: selectedMultipleDocuments,
        colloquial: isColloquialQuestion
      };
      
      console.log('提交评估数据:', evaluationData);
      
      // 调用API提交评估数据
      // 使用fetchApi工具函数，它会自动处理认证信息和基础URL
      await fetchApi('/evaluate/create', {
        method: 'POST',
        data: evaluationData
      });
      
      // 提交成功后关闭弹窗并刷新数据
      handleCloseNewEvaluationDialog();
      // 刷新批次数据
      await fetchBatchNumbers();
      // 刷新评估数据
      await fetchEvaluationData(0, selectedBatch);
      
      // 可以添加成功提示
      toast({
              title: '评估创建成功',
              description: '评估已创建，正在异步处理中...',
            });
    } catch (error) {
      console.error('创建评估失败:', error);
      toast({
              title: '创建评估失败',
              description: '请稍后重试',
              variant: 'destructive',
            });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <DashboardLayout>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-3xl font-bold">RAG评估</h1>
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={async () => {
            try {
              // 实现页面数据刷新功能
              // 并行刷新所有数据
              await Promise.all([
                fetchBatchNumbers(),
                fetchChartData(),
                fetchEvaluationData(0, selectedBatch)
              ]);
              console.log('数据刷新成功');
            } catch (error) {
              console.error('刷新失败:', error);
            }
          }}>
            <RefreshCw className="h-5 w-5" />
          </Button>
          <Button onClick={handleOpenNewEvaluationDialog}>
            <PlusCircle className="mr-2 h-4 w-4" />
            新建评估
          </Button>
          <Button onClick={async () => {
            // 打开弹窗前先获取未训练批次数据
            await fetchUntrainedBatchNumbers();
            setShowStartEvaluationDialog(true);
          }} className="bg-blue-600 hover:bg-blue-700">
            <Play className="mr-2 h-4 w-4" />
            开始评估
          </Button>
        </div>
      </div>

      <Tabs defaultValue="evaluation" className="mb-8">
        <TabsList className="mb-6">
          <TabsTrigger value="evaluation">评估结果</TabsTrigger>
          <TabsTrigger value="statistics">统计分析</TabsTrigger>
        </TabsList>

        <TabsContent value="evaluation" className="space-y-6">
          {/* 搜索和筛选区域 */}
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="relative w-full md:w-96">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input 
                placeholder="搜索问题或回答..." 
                className="pl-10 h-10 w-full"
              />
            </div>
            <div className="flex items-center gap-3">
              {/* 批次过滤下拉框 */}
              <Select value={selectedBatch} onValueChange={handleBatchChange}>
                <SelectTrigger className="h-10 w-[140px]">
                  <SelectValue placeholder="选择批次" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部批次</SelectItem>
                  {batchNumbers.map((batch) => (
                    <SelectItem key={batch.value} value={batch.value}>{batch.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* 评估结果表格 */}
          <Card>
            <CardHeader>
              <CardTitle>评估结果列表</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <Table className="min-w-[1200px]">
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-[60px]">ID</TableHead>
                      <TableHead className="min-w-[150px]">问题</TableHead>
                      <TableHead className="min-w-[150px]">参考答案</TableHead>
                      <TableHead className="min-w-[180px]">DocumentId</TableHead>
                      <TableHead className="min-w-[180px]">ChunkId</TableHead>
                      <TableHead className="min-w-[150px]">LLM回答</TableHead>
                      <TableHead className="min-w-[180px]">LLM DocumentId</TableHead>
                      <TableHead className="min-w-[180px]">LLM ChunkId</TableHead>
                      <TableHead className="min-w-[120px]">创建时间</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {isDataLoading ? (
                      <TableRow>
                        <TableCell colSpan={9} className="text-center py-10">
                          加载中...
                        </TableCell>
                      </TableRow>
                    ) : evaluationData.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={9} className="text-center py-10">
                          暂无数据
                        </TableCell>
                      </TableRow>
                    ) : evaluationData.map((item, index) => (
                      <TableRow key={index} className="cursor-pointer hover:bg-muted transition-colors" onClick={() => handleRowClick(item)}>
                        <TableCell className="font-medium text-nowrap py-4">{item.id || index + 1}</TableCell>
                        <TableCell className="whitespace-normal max-w-[150px] break-words py-4">
                          <div className="max-h-[120px] overflow-y-auto p-1">{item.question}</div>
                        </TableCell>
                        <TableCell className="whitespace-normal max-w-[150px] break-words py-4">
                          <div className="max-h-[120px] overflow-y-auto p-1">{item.referenceAnswer}</div>
                        </TableCell>
                        <TableCell className="whitespace-normal max-w-[180px] break-all py-4">
                          <div className="max-h-[120px] overflow-y-auto text-xs p-1">
                            {typeof item.documentId === 'string' ? JSON.stringify(parseJson(item.documentId)) : item.documentId}
                          </div>
                        </TableCell>
                        <TableCell className="whitespace-normal max-w-[180px] break-all py-4">
                          <div className="max-h-[120px] overflow-y-auto text-xs p-1">
                            {typeof item.chunkId === 'string' ? JSON.stringify(parseJson(item.chunkId)) : item.chunkId}
                          </div>
                        </TableCell>
                        <TableCell className="whitespace-normal max-w-[150px] break-words py-4">
                          <div className="max-h-[120px] overflow-y-auto p-1">{item.llmAnswer}</div>
                        </TableCell>
                        <TableCell className="whitespace-normal max-w-[180px] break-all py-4">
                          <div className="max-h-[120px] overflow-y-auto text-xs p-1">
                            {typeof item.llmDocumentId === 'string' ? JSON.stringify(parseJson(item.llmDocumentId)) : item.llmDocumentId}
                          </div>
                        </TableCell>
                        <TableCell className="whitespace-normal max-w-[180px] break-all py-4">
                          <div className="max-h-[120px] overflow-y-auto text-xs p-1">
                            {typeof item.llmChunkId === 'string' ? JSON.stringify(parseJson(item.llmChunkId)) : item.llmChunkId}
                          </div>
                        </TableCell>
                        <TableCell className="text-nowrap py-4">{formatTime(item.createTime)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
                
                {/* 分页组件 */}
                {evaluationData.length > 0 && !isDataLoading && (
                  <div className="flex justify-between items-center mt-4">
                    <div className="text-sm text-muted-foreground">
                      显示 {(currentPage * pageSize) + 1} 到 {Math.min((currentPage + 1) * pageSize, totalItems)} 条，共 {totalItems} 条
                    </div>
                    <div className="flex gap-2">
                      <Button 
                        variant="ghost" 
                        size="sm" 
                        disabled={currentPage === 0}
                        onClick={() => fetchEvaluationData(currentPage - 1)}
                      >
                        上一页
                      </Button>
                      <Button 
                        variant="ghost" 
                        size="sm" 
                        disabled={(currentPage + 1) * pageSize >= totalItems}
                        onClick={() => fetchEvaluationData(currentPage + 1)}
                      >
                        下一页
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="statistics" className="space-y-6">
          {/* 统计图表 */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle>评估指标趋势</CardTitle>
              <Button 
                variant="ghost" 
                size="icon"
                onClick={fetchChartData}
                disabled={isLoading}
              >
                <RefreshCw className={`h-5 w-5 ${isLoading ? 'animate-spin' : ''}`} />
              </Button>
            </CardHeader>
            <CardContent>
              {error && (
                <div className="mb-4 p-3 bg-red-50 text-red-500 rounded-md">
                  {error}
                </div>
              )}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* 折线图 */}
                <div className="h-[400px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart
                      data={chartData}
                      margin={{ top: 10, right: 30, left: 0, bottom: 0 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="name" />
                      <YAxis domain={[0, 1]} tickFormatter={(value) => `${(value * 100).toFixed(0)}%`} />
                      <Tooltip formatter={(value: number) => `${(value * 100).toFixed(2)}%`} />
                      <Line type="monotone" dataKey="忠实度" stroke="#8884d8" strokeWidth={2} />
                      <Line type="monotone" dataKey="答案相关性" stroke="#82ca9d" strokeWidth={2} />
                      <Line type="monotone" dataKey="答案相似度" stroke="#ffc658" strokeWidth={2} />
                      <Line type="monotone" dataKey="上下文召回率" stroke="#ff8042" strokeWidth={2} />
                      <Line type="monotone" dataKey="上下文精确度" stroke="#0088fe" strokeWidth={2} />
                      <Line type="monotone" dataKey="上下文相关性" stroke="#00C49F" strokeWidth={2} />
                      <Line type="monotone" dataKey="总体评分" stroke="#FF6B6B" strokeWidth={3} strokeDasharray="5 5" />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
                
                {/* 雷达图 */}
                <div className="h-[400px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <RadarChart cx="50%" cy="50%" outerRadius="80%" data={radarChartData}>
                      <PolarGrid />
                      <PolarAngleAxis dataKey="subject" />
                      <PolarRadiusAxis angle={30} domain={[0, 100]} />
                      {/* 根据数据动态生成多个雷达图层 */}
                      {Object.keys(radarChartData[0] || {}).map((key) => {
                        if (key !== 'subject' && key !== 'fullMark') {
                          // 为不同批次设置不同颜色
                          const colors = ['#8884d8', '#82ca9d', '#ffc658', '#ff8042', '#0088fe'];
                          const batchNum = key.split('_')[1];
                          // 使用批次号的最后一位来选择颜色，确保不同批次有不同颜色
                          const colorIndex = parseInt(batchNum.slice(-1)) % colors.length;
                          const color = colors[colorIndex];
                          return (
                            <Radar 
                              key={key} 
                              name={`批次${batchNum}`} 
                              dataKey={key} 
                              stroke={color} 
                              fill={color} 
                              fillOpacity={0.4} 
                            />
                          );
                        }
                        return null;
                      })}
                      <Tooltip />
                      {/* 添加图例 */}
                      <Legend />
                    </RadarChart>
                  </ResponsiveContainer>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* 优化建议 */}
          <Card>
            <CardHeader>
              <CardTitle>优化建议</CardTitle>
            </CardHeader>
            <CardContent>
              {optimizationAdvice && optimizationAdvice.length > 0 ? (
            <div className="prose max-w-none">
              <ReactMarkdown
                components={{
                  table: ({ node, ...props }) => (
                    <div className="my-4 overflow-x-auto">
                      <table className="w-full border-collapse border border-gray-200 text-sm">{props.children}</table>
                    </div>
                  ),
                  th: ({ node, ...props }) => (
                    <th className="border border-gray-200 px-3 py-2 bg-gray-50 text-left font-medium">{props.children}</th>
                  ),
                  td: ({ node, ...props }) => (
                    <td className="border border-gray-200 px-3 py-2 text-left">{props.children}</td>
                  ),
                  h3: ({ node, ...props }) => (
                    <h3 className="text-lg font-bold mt-4 mb-2">{props.children}</h3>
                  ),
                  h4: ({ node, ...props }) => (
                    <h4 className="text-base font-bold mt-3 mb-1">{props.children}</h4>
                  ),
                  ul: ({ node, ...props }) => (
                    <ul className="list-disc pl-5 mb-3">{props.children}</ul>
                  ),
                  li: ({ node, ...props }) => (
                    <li className="mb-1">{props.children}</li>
                  ),
                  p: ({ node, ...props }) => (
                    <p className="mb-2">{props.children}</p>
                  )
                }}
                remarkPlugins={[remarkGfm]}
                rehypePlugins={[rehypeHighlight, rehypeRaw]}
              >
                {optimizationAdvice}
              </ReactMarkdown>
            </div>
          ) : (
            <div className="prose max-w-none">
              <p>优先解决缺失问题：若准确率（置信度低）或相关性较差（答非所问），优先优化提示模板（Prompt约束、模型调优等），这是用户最易感知的问题。</p>
              <p>再解决完整度问题：若回答详尽但存在"部分遗忘"或"知识点少"，优化检索策略（调整相似度阈值、增加返回文档数量）或增加知识库覆盖面。</p>
              <p>最后处理细节问题：如专业术语使用不当、回答逻辑混乱等，可通过优化提示词模板或微调模型参数解决。</p>
              <p>若准确率和完整度都很高，但用户仍不满意，可能是提示词或答案格式问题，需要进一步分析用户反馈优化。</p>
            </div>
          )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
        
        {/* 详情弹窗 */}
        <Dialog open={showDetailDialog} onOpenChange={handleCloseDialog}>
          <DialogContent className="max-w-[1600px] w-[98vw] max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle className="flex justify-between items-center">
                评估详情 (ID: {selectedItem?.id || 'N/A'})
                <Button variant="ghost" size="icon" onClick={handleCloseDialog}>
                  <X className="h-4 w-4" />
                </Button>
              </DialogTitle>
            </DialogHeader>
            
            {selectedItem && (
              <div className="space-y-6 mt-4">
                {/* 基本信息 */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <Card>
                    <CardHeader className="py-3">
                      <CardTitle className="text-sm font-medium">基本信息</CardTitle>
                    </CardHeader>
                    <CardContent className="py-3">
                      <div className="space-y-2 text-sm">
                        <div>
                          <span className="font-medium text-muted-foreground">ID:</span> {selectedItem.id}
                        </div>
                        <div>
                          <span className="font-medium text-muted-foreground">创建时间:</span> {formatTime(selectedItem.createTime)}
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </div>
                
                {/* 问题和答案 */}
                <Card>
                  <CardHeader className="py-3">
                    <CardTitle className="text-sm font-medium">问题</CardTitle>
                  </CardHeader>
                  <CardContent className="py-3">
                    <div className="whitespace-pre-wrap">{selectedItem.question}</div>
                  </CardContent>
                </Card>
                
                <Card>
                  <CardHeader className="py-3">
                    <CardTitle className="text-sm font-medium">参考答案</CardTitle>
                  </CardHeader>
                  <CardContent className="py-3">
                    <div className="whitespace-pre-wrap">{selectedItem.referenceAnswer}</div>
                  </CardContent>
                </Card>
                
                <Card>
                  <CardHeader className="py-3">
                    <CardTitle className="text-sm font-medium">LLM回答 (Markdown格式)</CardTitle>
                  </CardHeader>
                  <CardContent className="py-3">
                    <div className="prose max-w-none">
                      <ReactMarkdown
                        components={{
                          table: ({ node, ...props }) => (
                            <div className="my-4 overflow-x-auto">
                              <table className="w-full border-collapse border border-gray-200 text-sm">{props.children}</table>
                            </div>
                          ),
                          th: ({ node, ...props }) => (
                            <th className="border border-gray-200 px-3 py-2 bg-gray-50 text-left font-medium">{props.children}</th>
                          ),
                          td: ({ node, ...props }) => (
                            <td className="border border-gray-200 px-3 py-2 text-left">{props.children}</td>
                          ),
                          h3: ({ node, ...props }) => (
                            <h3 className="text-lg font-bold mt-4 mb-2">{props.children}</h3>
                          ),
                          h4: ({ node, ...props }) => (
                            <h4 className="text-base font-bold mt-3 mb-1">{props.children}</h4>
                          ),
                          ul: ({ node, ...props }) => (
                            <ul className="list-disc pl-5 mb-3">{props.children}</ul>
                          ),
                          li: ({ node, ...props }) => (
                            <li className="mb-1">{props.children}</li>
                          ),
                          p: ({ node, ...props }) => (
                            <p className="mb-2">{props.children}</p>
                          )
                        }}
                        remarkPlugins={[remarkGfm]}
                        rehypePlugins={[rehypeHighlight, rehypeRaw]}
                      >
                        {selectedItem.llmAnswer}
                      </ReactMarkdown>
                    </div>
                  </CardContent>
                </Card>
                
                {/* 文档和Chunk信息 */}
                <Card>
                  <CardHeader className="py-3">
                    <CardTitle className="text-sm font-medium">文档信息</CardTitle>
                  </CardHeader>
                  <CardContent className="py-3 space-y-4">
                    <div>
                      <h4 className="text-sm font-medium mb-1">参考文档 ID</h4>
                      <div className="bg-muted p-2 rounded text-xs font-mono overflow-x-auto">
                        {typeof selectedItem.documentId === 'string' ? JSON.stringify(parseJson(selectedItem.documentId)) : selectedItem.documentId}
                      </div>
                    </div>
                    <div>
                      <h4 className="text-sm font-medium mb-1">参考 Chunk ID</h4>
                      <div className="bg-muted p-2 rounded text-xs font-mono overflow-x-auto">
                        {typeof selectedItem.chunkId === 'string' ? JSON.stringify(parseJson(selectedItem.chunkId)) : selectedItem.chunkId}
                      </div>
                    </div>
                    <div>
                      <h4 className="text-sm font-medium mb-1">LLM 文档 ID</h4>
                      <div className="bg-muted p-2 rounded text-xs font-mono overflow-x-auto">
                        {typeof selectedItem.llmDocumentId === 'string' ? JSON.stringify(parseJson(selectedItem.llmDocumentId)) : selectedItem.llmDocumentId}
                      </div>
                    </div>
                    <div>
                      <h4 className="text-sm font-medium mb-1">LLM Chunk ID</h4>
                      <div className="bg-muted p-2 rounded text-xs font-mono overflow-x-auto">
                        {typeof selectedItem.llmChunkId === 'string' ? JSON.stringify(parseJson(selectedItem.llmChunkId)) : selectedItem.llmChunkId}
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </div>
            )}
            
            <DialogFooter className="mt-4">
              <Button onClick={handleCloseDialog}>关闭</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
        
        {/* 开始评估弹窗 */}
        <Dialog open={showStartEvaluationDialog} onOpenChange={setShowStartEvaluationDialog}>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>开始评估</DialogTitle>
            </DialogHeader>
            
            <div className="grid gap-4 py-4">
              {/* 批次选择 */}
              <div className="space-y-2">
                <label className="text-sm font-medium">选择批次</label>
                <Select value={startEvaluationBatchNum} onValueChange={setStartEvaluationBatchNum}>
                  <SelectTrigger>
                    {isUntrainedBatchLoading ? (
                      <span className="flex items-center gap-1">
                        <span className="animate-spin h-4 w-4 border-2 border-current border-t-transparent rounded-full"></span>
                        <span>加载中...</span>
                      </span>
                    ) : (
                      <SelectValue placeholder="选择批次" />
                    )}
                  </SelectTrigger>
                  <SelectContent>
                    {isUntrainedBatchLoading ? (
                      <div className="flex justify-center items-center py-4">
                        <span className="animate-spin h-4 w-4 border-2 border-current border-t-transparent rounded-full mr-2"></span>
                        <span>加载中...</span>
                      </div>
                    ) : untrainedBatchNumbers.length === 0 ? (
                      <div className="flex justify-center items-center py-4 text-muted-foreground">
                        暂无未训练批次数据
                      </div>
                    ) : (
                      untrainedBatchNumbers.map((batch) => (
                        <SelectItem key={batch.value} value={batch.value}>{batch.label}</SelectItem>
                      ))
                    )}
                  </SelectContent>
                </Select>
              </div>
            </div>
            
            <DialogFooter>
              <Button variant="ghost" onClick={() => setShowStartEvaluationDialog(false)} disabled={isStartEvaluationSubmitting}>
                取消
              </Button>
              <Button 
                onClick={async () => {
                  setIsStartEvaluationSubmitting(true);
                  try {
                    // 使用fetchApi调用评估开始接口（修改为POST请求）
                    await fetchApi('/evaluate/start',{
                      method: 'POST',
                      data: {
                        batchNum: startEvaluationBatchNum
                      }
                    });
                    
                    toast({
              title: '开始评估成功',
              description: `批次 ${startEvaluationBatchNum} 已开始评估`,
            });
                    setShowStartEvaluationDialog(false);
                    // 刷新评估数据
                    await fetchEvaluationData(0, selectedBatch);
                  } catch (error) {
                    console.error('开始评估失败:', error);
                    toast({
              title: '开始评估失败',
              description: '请稍后重试',
              variant: 'destructive',
            });
                  } finally {
                    setIsStartEvaluationSubmitting(false);
                  }
                }} 
                disabled={isStartEvaluationSubmitting || !startEvaluationBatchNum}
              >
                {isStartEvaluationSubmitting ? '提交中...' : '提交'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
        
        {/* 新建评估弹窗 */}
        <Dialog open={showNewEvaluationDialog} onOpenChange={handleCloseNewEvaluationDialog}>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>新建评估</DialogTitle>
            </DialogHeader>
            
            <div className="grid gap-4 py-4">
              {/* 批次输入 */}
              <div className="space-y-2">
                <label className="text-sm font-medium">批次</label>
                <Input 
                  type="text" 
                  placeholder="请输入批次号" 
                  value={batchNum} 
                  onChange={(e) => setBatchNum(e.target.value)}
                />
              </div>
              
              {/* 知识库选择器 */}
              <div className="space-y-2">
                <label className="text-sm font-medium">知识库</label>
                <Select value={selectedKnowledgeBase} onValueChange={handleKnowledgeBaseChange}>
                  <SelectTrigger>
                    {isKnowledgeBaseLoading ? (
                      <span className="flex items-center gap-1">
                        <span className="animate-spin h-4 w-4 border-2 border-current border-t-transparent rounded-full"></span>
                        <span>加载中...</span>
                      </span>
                    ) : (
                      <SelectValue placeholder="选择知识库" />
                    )}
                  </SelectTrigger>
                  <SelectContent>
                    {isKnowledgeBaseLoading ? (
                      <div className="flex justify-center items-center py-4">
                        <span className="animate-spin h-4 w-4 border-2 border-current border-t-transparent rounded-full mr-2"></span>
                        <span>加载中...</span>
                      </div>
                    ) : knowledgeBases.length === 0 ? (
                      <div className="flex justify-center items-center py-4 text-muted-foreground">
                        暂无知识库数据
                      </div>
                    ) : (
                      knowledgeBases.map((kb) => (
                        <SelectItem key={kb.value} value={kb.value}>{kb.label}</SelectItem>
                      ))
                    )}
                  </SelectContent>
                </Select>
              </div>
              
              {/* 单文档选择器 */}
              <div className="space-y-2">
                <label className="text-sm font-medium">单文档</label>
                <MultiSelect 
                  options={documents}
                  selectedValues={selectedSingleDocument}
                  onSelectionChange={(values) => setSelectedSingleDocument(values)}
                  placeholder="选择文档"
                />
              </div>
              
              {/* 多文档选择器 */}
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <label className="text-sm font-medium">多文档</label>
                  <button 
                    type="button"
                    onClick={addMultipleDocumentSelector}
                    className="text-blue-500 hover:text-blue-700 text-sm"
                  >
                    + 添加多选框
                  </button>
                </div>
                {selectedMultipleDocuments.map((selected, index) => (
                  <div key={index} className="flex items-start mb-2">
                    <div className="flex-grow">
                      <MultiSelect 
                        options={documents}
                        selectedValues={selected}
                        onSelectionChange={(values) => updateMultipleDocumentSelector(index, values)}
                        placeholder={`请选择多个文档 ${index + 1}`}
                      />
                    </div>
                    {selectedMultipleDocuments.length > 1 && (
                      <button 
                        type="button"
                        onClick={() => removeMultipleDocumentSelector(index)}
                        className="ml-2 mt-2 text-red-500 hover:text-red-700"
                      >
                        ×
                      </button>
                    )}
                  </div>
                ))}
              </div>
              
              {/* 是否口语化问题 */}
              <div className="flex items-center justify-between">
                <label className="text-sm font-medium">是否口语化问题</label>
                <div className="flex items-center space-x-4">
                  <label className="flex items-center space-x-2">
                    <input 
                      type="radio" 
                      name="isColloquial" 
                      value="false" 
                      checked={!isColloquialQuestion} 
                      onChange={() => setIsColloquialQuestion(false)}
                    />
                    <span>否</span>
                  </label>
                  <label className="flex items-center space-x-2">
                    <input 
                      type="radio" 
                      name="isColloquial" 
                      value="true" 
                      checked={isColloquialQuestion} 
                      onChange={() => setIsColloquialQuestion(true)}
                    />
                    <span>是</span>
                  </label>
                </div>
              </div>
            </div>
            
            <DialogFooter>
              <Button variant="ghost" onClick={handleCloseNewEvaluationDialog} disabled={isSubmitting}>
                取消
              </Button>
              <Button onClick={handleSubmitEvaluation} disabled={isSubmitting || !batchNum || !selectedKnowledgeBase}>
                {isSubmitting ? '提交中...' : '提交'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </DashboardLayout>
    );
  }

  // 移除了自定义的Markdown解析函数，改用react-markdown库
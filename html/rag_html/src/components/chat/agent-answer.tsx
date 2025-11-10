import React, { useState } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Globe, ChevronDown, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeRaw from 'rehype-raw';
import rehypeHighlight from 'rehype-highlight';
import rehypeKatex from 'rehype-katex';

/**
 * 流程节点接口定义
 * type: 节点类型，agent表示代理节点，llm表示语言模型节点
 * id: 节点唯一标识
 * name: 节点名称
 * status: 节点状态
 * icon: 可选的节点图标
 * description: 可选的节点描述
 * content: 可选的节点内容，主要用于llm类型节点
 * order: 可选的排序字段，用于节点排序
 * reference: 可选的引用信息
 */
interface ProcessNode {
  type: 'agent' | 'llm';
  id: string;
  name: string;
  status: 'active' | 'completed' | 'pending';
  icon?: string;
  description?: string;
  content?: string;
  order?: number;
  reference?: Array<{
    chunkId: string;
    documentId: string;
    documentName: string;
    documentUrl: string;
    text: string;
    score: string;
    pageRange: string;
    contentType: string;
    imageUrl: string | null;
  }>;
}

/**
 * 流程定义接口
 * nodes: 流程节点数组
 * edges: 节点间连接关系数组
 */
interface ProcessFlow {
  nodes: ProcessNode[];
  edges: Array<{ from: string; to: string }>;
}

/**
 * AgentAnswer组件属性接口
 * processFlow: 处理流程信息
 * showProcessView: 是否显示流程视图
 * content: 回答内容
 * citations: 引用信息
 * ragReference: RAG引用信息
 */
interface AgentAnswerProps {
  processFlow?: ProcessFlow;
  showProcessView?: boolean;
  content?: string;
  citations?: any[];
  ragReference?: any;
}

/**
 * AgentAnswer组件
 * 负责渲染代理回答内容和处理流程
 */
const AgentAnswer: React.FC<AgentAnswerProps> = ({
                                                   processFlow,
                                                   content,
                                                   citations,
                                                   ragReference
                                                 }) => {
  // 用于存储每个节点的引用展开状态
  const [expandedRefs, setExpandedRefs] = useState<Record<string, boolean>>({});

  /**
   * 切换引用信息的展开/折叠状态
   * @param nodeId 节点ID
   */
  const toggleReference = (nodeId: string) => {
    setExpandedRefs(prev => ({
      ...prev,
      [nodeId]: !prev[nodeId]
    }));
  };

  /**
   * 根据节点类型渲染单个流程节点
   * @param node 流程节点对象
   * @returns 渲染后的节点JSX元素
   */
  const renderSingleProcessNode = (node: ProcessNode) => {
    const statusColors = {
      active: 'bg-blue-500 text-white',
      completed: 'bg-green-500 text-white',
      pending: 'bg-gray-300 text-gray-700'
    };

    // 根据节点类型选择不同的渲染方式
    if (node.type === 'agent') {
      // 渲染agent类型节点
      return (
          <div key={node.id} className="animate-fadeIn bg-gray-50 p-4 rounded-lg mb-4">
            <div className="flex gap-4 items-start">
              {/* 节点图标/状态 */}
              <div className={`flex-shrink-0 h-10 w-10 rounded-full flex items-center justify-center ${statusColors[node.status]}`}>
                {node.icon ? (
                    <Globe className="h-5 w-5" />
                ) : (
                    <span className="font-bold">{node.id.slice(-1)}</span>
                )}
              </div>

              {/* 节点内容 */}
              <div className="bg-white border border-gray-200 rounded-lg p-3 shadow-sm flex-grow">
                <div className="flex justify-between items-center mb-2">
                  <h4 className="font-medium text-sm">{node.name}</h4>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${statusColors[node.status]}`}>
                  {node.status === 'active' ? '执行中' :
                      node.status === 'completed' ? '已完成' : '待执行'}
                </span>
                </div>
                {node.description && (
                    <p className="text-xs text-gray-600">{node.description}</p>
                )}
              </div>
            </div>
          </div>
      );
    } else {
      // 渲染llm类型节点（markdown格式文本，去除装饰，保持原样输出）
      const isRefExpanded = expandedRefs[node.id] || false;
      const hasReferences = node.reference && node.reference.length > 0;

      return (
          <div key={node.id}>
            {node.content && (
                <div className="prose prose-sm max-w-none prose-h1:font-bold prose-h1:text-xl prose-h2:font-bold prose-h2:text-lg prose-h3:font-bold prose-h3:text-base prose-p:my-2 prose-li:my-1 prose-code:bg-muted/50 prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded prose-code:text-xs prose-ol:pl-5 prose-ul:pl-5 prose-strong:font-bold break-words">
                  <ReactMarkdown
                      remarkPlugins={[remarkGfm, remarkMath]}
                      rehypePlugins={[rehypeRaw, rehypeHighlight, rehypeKatex]}
                  >
                    {node.content}
                  </ReactMarkdown>
                </div>
            )}

            {/* 渲染引用信息，带折叠/展开功能 */}
            {hasReferences && (
                <div className="mt-4">
                  {/* 引用标题行，包含展开/折叠按钮 */}
                  <button
                      onClick={() => toggleReference(node.id)}
                      className="w-full flex items-center justify-between text-left text-sm font-medium text-gray-700 mb-2 focus:outline-none"
                  >
                    <div className="flex items-center">
                      <h4>参考资料：({node.reference!.length})</h4>
                    </div>
                    <span className="text-gray-500">
                  {isRefExpanded ?
                      <ChevronDown className="h-4 w-4" /> :
                      <ChevronRight className="h-4 w-4" />
                  }
                </span>
                  </button>

                  {/* 引用内容，根据展开状态显示/隐藏 */}
                  {isRefExpanded && (
                      <div className="space-y-3">
                        {node.reference!.map((ref, index) => (
                            <div key={index} className="bg-gray-50 border border-gray-200 rounded-md p-3">
                              <div className="flex items-start justify-between">
                                <div className="flex-1">
                                  <h5 className="text-sm font-medium text-gray-900 flex items-center">
                                    <Globe className="h-4 w-4 mr-1.5 text-blue-500" />
                                    {ref.documentName}
                                  </h5>
                                  {ref.pageRange && (
                                      <span className="text-xs text-gray-500 ml-5.5">第 {ref.pageRange} 页</span>
                                  )}
                                </div>
                                {ref.documentUrl && (
                                    <a
                                        href={ref.documentUrl.trim()}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="text-xs text-blue-500 hover:text-blue-700 ml-2 flex-shrink-0"
                                    >
                                      查看
                                    </a>
                                )}
                              </div>
                              {ref.text && (
                                  <p className="text-xs text-gray-600 mt-2 ml-5.5">
                                    {ref.text}
                                  </p>
                              )}
                            </div>
                        ))}
                      </div>
                  )}
                </div>
            )}
          </div>
      );
    }
  };

  /**
   * 渲染处理流程节点
   * 确保不会重复添加或显示llm类型节点
   * @returns 渲染后的节点列表JSX元素
   */
  const renderProcessNodes = () => {
    // 创建所有节点的合并数组
    const allNodes: ProcessNode[] = [];
    
    // 检查processFlow中是否包含llm类型节点
    let hasLlmNodeFromProcessFlow = false;
    
    // 添加processFlow中的节点，并检查是否有llm节点
    if (processFlow && processFlow.nodes.length) {
      hasLlmNodeFromProcessFlow = processFlow.nodes.some(node => node.type === 'llm');
      allNodes.push(...processFlow.nodes);
    }
    
    // 仅当processFlow中没有llm节点且有content时，才添加content作为llm节点
    // 确保即使content是空字符串也能正确处理（兼容旧版本和新版本）
    const contentHasValue = content !== undefined && content !== null && content !== '';
    if (contentHasValue && !hasLlmNodeFromProcessFlow) {
      allNodes.push({
        type: 'llm',
        id: 'llm-output',
        name: '回答生成',
        status: 'completed',
        content: content,
        order: processFlow && processFlow.nodes.length ? processFlow.nodes.length : 0,
        reference: citations || []
      });
    }
    
    if (allNodes.length === 0) {
      return null;
    }
    
    // 根据order字段排序节点，如果没有order则按照数组顺序
    const sortedNodes = [...allNodes].sort((a, b) => {
      const orderA = a.order ?? 0;
      const orderB = b.order ?? 0;
      return orderA - orderB;
    });
    
    // 渲染所有排序后的节点
    return (
        <div className="space-y-3">
          {sortedNodes.map((node, index) => (
              <React.Fragment key={node.id}>
                {renderSingleProcessNode(node)}
                {index < sortedNodes.length - 1 && (
                    <div className="ml-5 -mt-1 -mb-1 flex justify-center">
                      <div className="h-4 w-0.5 bg-gray-200"></div>
                    </div>
                )}
              </React.Fragment>
          ))}
        </div>
    );
  };

  return (
      <div className={cn("space-y-3")}>
        {/* 显示合并后的处理流程（包含agent节点和llm内容节点） */}
        {renderProcessNodes()}

        {/* 研究中提示（当有节点且存在active节点时显示） */}
        {processFlow && processFlow.nodes.some(node => node.status === 'active' && node.type !== 'llm') && (
            <div className="mt-2 p-3 bg-blue-50 border border-blue-100 rounded-md text-sm text-blue-700">
              <p className="font-medium flex items-center gap-2">
                <Globe className="h-4 w-4" />
                研究中...
              </p>
              <p className="text-xs mt-1 text-blue-600">
                正在分析结果，预计需要一些时间...
              </p>
            </div>
        )}
      </div>
  );
};

export default AgentAnswer;
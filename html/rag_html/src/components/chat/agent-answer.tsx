import React, { useState, useRef } from 'react';
import { Check, Copy } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Globe, ChevronDown, ChevronRight, File, Eye } from "lucide-react";
import { cn } from "@/lib/utils";
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeRaw from 'rehype-raw';
import rehypeHighlight from 'rehype-highlight';
import rehypeKatex from 'rehype-katex';
import 'highlight.js/styles/github.css';
import 'katex/dist/katex.min.css';

const CustomTable = ({ children, ...props }: any) => (
  <table 
    {...props} 
    className="border border-gray-300 border-collapse w-full"
  >
    {children}
  </table>
);

const CustomThead = ({ children, ...props }: any) => (
  <thead {...props}>
    {children}
  </thead>
);

const CustomTr = ({ children, ...props }: any) => (
  <tr {...props}>
    {children}
  </tr>
);

const CustomTh = ({ children, ...props }: any) => (
  <th 
    {...props} 
    className="border border-gray-300 px-4 py-2 bg-gray-50 font-semibold text-left"
  >
    {children}
  </th>
);

const CustomTd = ({ children, ...props }: any) => (
  <td 
    {...props} 
    className="border border-gray-300 px-4 py-2"
  >
    {children}
  </td>
);

const Code = ({ className, children, ...props }: any) => {
  const isCodeBlock = className && className.includes('language-');
  
  if (isCodeBlock) {
    const [copied, setCopied] = useState(false);
    const codeRef = useRef<HTMLPreElement>(null);
    const language = className.replace(/language-/, '') || 'code';
    
    const handleCopy = () => {
      if (codeRef.current) {
        navigator.clipboard.writeText(codeRef.current.textContent || '');
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    };
    
    const lines = String(children).trim().split('\n').length;
    const lineNumbers = Array.from({ length: lines }, (_, i) => i + 1);
    
    return (
      <div className="my-4 rounded-md overflow-hidden border border-gray-200 dark:border-gray-700 shadow-sm">
        <div className="bg-gray-50 dark:bg-gray-800 px-4 py-2 flex justify-between items-center">
          <div className="flex items-center space-x-2">
            <div className="w-3 h-3 rounded-full bg-red-500"></div>
            <div className="w-3 h-3 rounded-full bg-yellow-500"></div>
            <div className="w-3 h-3 rounded-full bg-green-500"></div>
            <span className="ml-2 text-xs font-medium text-gray-600 dark:text-gray-300">{language}</span>
          </div>
          <button 
            onClick={handleCopy}
            className="p-1.5 rounded-md text-gray-500 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
            aria-label="复制代码"
          >
            {copied ? <Check size={16} className="text-green-500" /> : <Copy size={16} />}
          </button>
        </div>
        <div className="bg-white dark:bg-gray-900 flex">
          <div className="bg-gray-50 dark:bg-gray-800 text-right pr-1.5 pl-1.5 py-1 border-r border-gray-200 dark:border-gray-700 select-none">
            {lineNumbers.map(num => (
              <div key={num} className="text-gray-500 dark:text-gray-400 font-mono leading-none" style={{ fontSize: '14px', padding: '1px' }}>
                {num}
              </div>
            ))}
          </div>
          <pre 
            ref={codeRef}
            className="flex-1 p-1 overflow-x-auto font-mono text-gray-800 dark:text-gray-300"
            style={{ lineHeight: '1.1', margin: 0, padding: '4px', fontSize: '14px' }}
          >
            <code className={className} style={{ lineHeight: '1.1', margin: 0, padding: 0, fontSize: '14px' }}>{children}</code>
          </pre>
        </div>
      </div>
    );
  }
  
  return (
    <code 
      className="bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded-md text-sm font-mono text-gray-800 dark:text-gray-200"
      {...props}
    >
      {children}
    </code>
  );
};

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
  displayTitle?: string; // 新增：用于节点名称展示的标题
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
   * @param index 节点顺序索引（从1开始）
   * @returns 渲染后的节点JSX元素
   */
  const renderSingleProcessNode = (node: ProcessNode, index?: number) => {
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
              {/* 节点图标/状态 - 显示节点顺序数字 */}
              <div className={`flex-shrink-0 h-10 w-10 rounded-full flex items-center justify-center ${statusColors[node.status]}`}>
                <span className="font-bold">{index}</span>
              </div>

              {/* 节点内容 */}
              <div className="bg-white border border-gray-200 rounded-lg p-3 shadow-sm flex-grow w-full">
                <div className="flex justify-between items-center mb-2">
                  <h4 className="font-medium text-sm">{node.displayTitle || node.name}</h4>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${statusColors[node.status]}`}>
                  {node.status === 'active' ? '执行中' :
                      node.status === 'completed' ? '已完成' : '待执行'}
                </span>
                </div>
                {node.description && (
                    <div className="prose prose-sm max-w-none prose-h1:font-bold prose-h1:text-lg prose-h2:font-bold prose-h2:text-base prose-h3:font-bold prose-h3:text-sm prose-p:my-2 prose-li:my-1 prose-code:bg-muted/50 prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded prose-code:text-xs prose-ol:pl-5 prose-ul:pl-5 prose-strong:font-bold break-words overflow-wrap:anywhere word-break:break-word">
                        <ReactMarkdown
                            remarkPlugins={[remarkGfm, remarkMath]}
                            rehypePlugins={[rehypeRaw, rehypeHighlight, rehypeKatex]}
                            components={{
                              code: Code,
                              table: CustomTable,
                              thead: CustomThead,
                              tr: CustomTr,
                              th: CustomTh,
                              td: CustomTd
                            }}
                        >
                            {node.description}
                        </ReactMarkdown>
                    </div>
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
                <div className="prose prose-sm max-w-none prose-h1:font-bold prose-h1:text-xl prose-h2:font-bold prose-h2:text-lg prose-h3:font-bold prose-h3:text-base prose-p:my-2 prose-li:my-1 prose-code:bg-muted/50 prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded prose-code:text-xs prose-ol:pl-5 prose-ul:pl-5 break-words overflow-wrap:anywhere word-break:break-word w-full">
                  <ReactMarkdown
                      remarkPlugins={[remarkGfm, remarkMath]}
                      rehypePlugins={[rehypeRaw, rehypeHighlight, rehypeKatex]}
                      components={{
                        code: Code,
                        table: CustomTable,
                        thead: CustomThead,
                        tr: CustomTr,
                        th: CustomTh,
                        td: CustomTd
                      }}
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
                      <File className="h-3.5 w-3.5 text-primary mr-1.5" />
                      <h4>引用的文档：({node.reference!.length})</h4>
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
                            <Card key={ref.chunkId || `${node.id}-${ref.documentId}`} className="border border-muted/30 bg-muted/50 hover:border-primary/50 hover:shadow-sm transition-all duration-300 transform hover:-translate-y-0.5">
                              <CardHeader className="p-3 pb-0">
                                <div className="flex items-center justify-between">
                                  <div className="flex items-center gap-2">
                                    <File className="h-4 w-4 text-primary" />
                                    <CardTitle className="text-sm font-medium">
                                      {ref.documentName || `文档 ${index + 1}`}
                                    </CardTitle>
                                    <button
                                      onClick={() => {
                                        if (ref.documentId) {
                                          const previewUrl = `/file-preview/${ref.documentId}`;
                                          window.open(previewUrl, '_blank');
                                        }
                                      }}
                                      className="text-xs font-medium text-primary/80 hover:text-primary transition-colors flex items-center gap-1"
                                      aria-label="查看文件"
                                    >
                                      <Eye className="h-3 w-3" />
                                      查看文件
                                    </button>
                                  </div>
                                  <span className="text-xs font-medium text-primary/80">
                                    [引用 {index + 1}]
                                  </span>
                                </div>
                                <div className="text-xs mt-0.5 text-muted-foreground">
                                  {ref.documentId ? `文档ID: ${ref.documentId}` : ''}
                                  {ref.pageRange ? `，页码: ${ref.pageRange}` : ''}
                                  {ref.contentType ? `，类型: ${ref.contentType}` : ''}
                                </div>
                              </CardHeader>
                              <CardContent className="p-3 pt-2">
                                <div className="text-xs text-muted-foreground max-h-[200px] overflow-y-auto bg-background/30 p-2 rounded-md">
                                  <ReactMarkdown
                                remarkPlugins={[remarkGfm, remarkMath]}
                                rehypePlugins={[rehypeRaw, rehypeHighlight, rehypeKatex]}
                                components={{
                                  code: Code,
                                  table: CustomTable,
                                  thead: CustomThead,
                                  tr: CustomTr,
                                  th: CustomTh,
                                  td: CustomTd
                                }}
                              >
                                {ref.text || ''}
                              </ReactMarkdown>
                                </div>
                              </CardContent>
                            </Card>
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
                {renderSingleProcessNode(node, index + 1)} {/* 传递索引+1作为节点顺序（从1开始计数） */}
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
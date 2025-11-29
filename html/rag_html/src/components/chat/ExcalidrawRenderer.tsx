'use client';

import React, { useState, useEffect, useCallback, Suspense } from 'react';

// 动态导入Excalidraw组件，避免SSR错误
const Excalidraw = React.lazy(() => import('@excalidraw/excalidraw').then(module => ({ default: module.Excalidraw })));

// 条件导入CSS，只在客户端环境导入
if (typeof window !== 'undefined') {
  import('@excalidraw/excalidraw/index.css');
}

interface ExcalidrawRendererProps {
  data?: any; // Excalidraw JSON格式数据
}

/**
 * Excalidraw渲染组件
 * 用于直接渲染Excalidraw绘图内容，无需agent样式加载方式
 */
const ExcalidrawRenderer: React.FC<ExcalidrawRendererProps> = ({ data }) => {
  const [isClient, setIsClient] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [renderData, setRenderData] = useState<any>(null);

  // 确保只在客户端环境中运行
  useEffect(() => {
    setIsClient(true);
  }, []);

  // 处理Excalidraw数据
  const processExcalidrawData = useCallback((inputData?: any) => {
    if (!inputData) {
      return null;
    }

    try {
      console.log('输入数据类型:', typeof inputData);
      console.log('输入数据结构:', JSON.stringify(inputData).substring(0, 200) + '...');
      
      // 1. 直接检查是否已经是标准Excalidraw数据格式
      if (inputData.elements && Array.isArray(inputData.elements)) {
        console.log('找到直接的Excalidraw数据格式');
        return inputData;
      }
      
      // 2. 处理JSON字符串格式
      if (typeof inputData === 'string') {
        console.log('处理JSON字符串格式输入');
        try {
          const parsedData = JSON.parse(inputData);
          return processExcalidrawData(parsedData);
        } catch (parseError) {
          console.error('解析JSON字符串失败:', parseError);
          // 尝试修复转义字符问题
          try {
            const fixedContent = inputData.replace(/\n/g, '\\n');
            const parsedData = JSON.parse(fixedContent);
            return processExcalidrawData(parsedData);
          } catch (fixedParseError) {
            console.error('修复后仍解析失败:', fixedParseError);
          }
          return null;
        }
      }
      
      // 3. 处理对象格式
      if (typeof inputData === 'object' && inputData !== null) {
        // 3.1 处理excalidraw节点
        if (inputData.nodeName === 'excalidraw' && inputData.content) {
          console.log('找到excalidraw节点');
          return processExcalidrawData(inputData.content);
        }
        
        // 3.2 处理messageType为excalidraw的数据
        if (inputData.messageType === 'excalidraw' && inputData.content) {
          console.log('找到messageType为excalidraw的数据');
          return processExcalidrawData(inputData.content);
        }
        
        // 3.3 处理content字段（可能是数组或对象）
        if (inputData.content) {
          console.log('处理content字段');
          // 无论content是数组还是对象，都直接递归处理
          // 这样可以避免Array.isArray判断错误的问题
          const contentResult = processExcalidrawData(inputData.content);
          if (contentResult) return contentResult;
        }
        
        // 3.4 处理data字段
        if (inputData.data) {
          console.log('处理data字段');
          const dataResult = processExcalidrawData(inputData.data);
          if (dataResult) return dataResult;
        }
        
        // 3.5 处理数组格式
        if (Array.isArray(inputData)) {
          console.log('处理数组格式');
          for (const item of inputData) {
            const itemResult = processExcalidrawData(item);
            if (itemResult) return itemResult;
          }
        }
        
        // 3.6 遍历对象的所有键查找嵌套数据
        console.log('遍历对象键查找嵌套数据');
        for (const key in inputData) {
          if (inputData.hasOwnProperty(key) && typeof inputData[key] === 'object' && inputData[key] !== null) {
            // 跳过已经检查过的字段
            if (key !== 'content' && key !== 'data') {
              const nestedResult = processExcalidrawData(inputData[key]);
              if (nestedResult) {
                console.log(`从键 ${key} 中找到Excalidraw数据`);
                return nestedResult;
              }
            }
          }
        }
      }
      
      console.log('未找到有效的Excalidraw数据结构');
      return null;
    } catch (error) {
      console.error('处理Excalidraw数据时出错:', error);
      return null;
    }
  }, []);

  // 当数据变化时，处理并设置渲染数据
    useEffect(() => {
      console.log('Excalidraw数据发生变化:', data);
      
      // 使用try-catch确保数据处理不会导致组件崩溃
      try {
        if (data) {
          const processedData = processExcalidrawData(data);
          console.log('处理后的Excalidraw数据:', processedData);
          setRenderData(processedData);
        } else {
          setRenderData(null);
        }
      } catch (error) {
        console.error('设置渲染数据时出错:', error);
        setRenderData(null);
      }
      
      // 模拟加载完成
      const timer = setTimeout(() => {
        setIsLoading(false);
      }, 300);
      
      return () => clearTimeout(timer);
    }, [data, processExcalidrawData]);

  // 服务器端渲染时返回占位符
  if (!isClient) {
    return (
      <div className="mt-4 p-4 border border-gray-200 rounded-lg bg-white">
        <div className="text-sm text-gray-600 mb-2">🎨 绘图内容</div>
        <div className="border rounded bg-gray-50 h-96 flex items-center justify-center">
          <div className="text-gray-400">Loading...</div>
        </div>
      </div>
    );
  }

  // Excalidraw配置选项
  const excalidrawOptions = {
    // 禁用编辑功能，仅用于查看
    editable: false,
    // 设置最小宽度以确保内容完整显示
    minWidth: 600,
    // 设置自动对焦为false以避免在渲染时获取焦点
    autoFocus: false,
    // 设置初始数据
    initialData: renderData || {
      elements: [],
      appState: {
        viewBackgroundColor: "#ffffff",
      },
      files: null
    },
    // 禁用复制粘贴功能
    clipboard: {
      handlePaste: false,
    },
    // 自定义工具提示
    name: "Excalidraw绘图",
    // 性能优化配置
    experimental: {
      enableCanvasScrolling: true,
    },
    // 禁用工具按钮，因为我们只是查看
    UIOptions: {
      canvasActions: {
        resetView: false,
        export: undefined, // 使用undefined而不是boolean以修复类型错误
        clearCanvas: false,
      },
      zoom: {
        showZoomControl: true,
      },
    },
  };

  return (
    <div className="mt-4 border border-gray-200 rounded-lg bg-white">
      <div className="text-sm text-gray-600 mb-2">
        <span>🎨 绘图内容</span>
      </div>

      <div 
        className={`border rounded transition-all duration-300 overflow-hidden relative ${isLoading ? 'opacity-50' : 'opacity-100'} h-[500px] md:h-[400px]`}
      >
        {isLoading && (
          <div className="absolute inset-0 flex items-center justify-center bg-white bg-opacity-80 z-10">
            <div className="flex flex-col items-center">
              <div className="h-8 w-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mb-2"></div>
              <span className="text-sm text-gray-500">渲染中...</span>
            </div>
          </div>
        )}
        <Suspense fallback={
          <div className="w-full h-full flex items-center justify-center">
            <span className="text-gray-500">加载Excalidraw组件...</span>
          </div>
        }>
          <Excalidraw {...excalidrawOptions} />
        </Suspense>
      </div>
    </div>
  );
};

export default React.memo(ExcalidrawRenderer);
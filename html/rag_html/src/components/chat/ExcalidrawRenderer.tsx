'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { Excalidraw } from '@excalidraw/excalidraw';
import '@excalidraw/excalidraw/index.css';

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
      // 尝试解析数据的不同可能格式
      let parsedData = null;
      
      // 1. 检查data是否已经是对象格式
      if (typeof inputData === 'object' && inputData !== null) {
        parsedData = inputData;
      }
      // 2. 检查data是否是JSON字符串
      else if (typeof inputData === 'string') {
        parsedData = JSON.parse(inputData);
      }
      
      if (!parsedData) {
        return null;
      }
      
      // 处理不同的嵌套结构，确保返回正确格式的数据
      if (parsedData.elements && Array.isArray(parsedData.elements)) {
        return parsedData;
      } else if (parsedData.data && parsedData.data.elements) {
        return parsedData.data;
      }
      
      return null;
    } catch (error) {
      console.error('处理Excalidraw数据失败:', error);
      return null;
    }
  }, []);

  // 当数据变化时，处理并设置渲染数据
  useEffect(() => {
    const processedData = processExcalidrawData(data);
    setRenderData(processedData);
    
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
    <div className="mt-4 p-4 border border-gray-200 rounded-lg bg-white transition-all duration-300">
      <div className="flex justify-between items-center text-sm text-gray-600 mb-2">
        <div className="flex items-center gap-1">
          <span>🎨 绘图内容</span>
          <span className="bg-green-100 text-green-800 text-xs px-2 py-0.5 rounded-full">已完成</span>
        </div>
        {renderData && renderData.elements && (
          <span className="text-xs text-gray-500">
            包含 {renderData.elements.length} 个元素
          </span>
        )}
      </div>

      <div 
        className={`border rounded transition-all duration-300 overflow-hidden relative ${isLoading ? 'opacity-50' : 'opacity-100'} h-[400px] md:h-[300px]`}
      >
        {isLoading && (
          <div className="absolute inset-0 flex items-center justify-center bg-white bg-opacity-80 z-10">
            <div className="flex flex-col items-center">
              <div className="h-8 w-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mb-2"></div>
              <span className="text-sm text-gray-500">渲染中...</span>
            </div>
          </div>
        )}
        <Excalidraw {...excalidrawOptions} />
      </div>
    </div>
  );
};

export default React.memo(ExcalidrawRenderer);
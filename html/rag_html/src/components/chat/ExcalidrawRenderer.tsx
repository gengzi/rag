'use client';

import React, { useState, useEffect } from 'react';
import { Excalidraw } from '@excalidraw/excalidraw';
import '@excalidraw/excalidraw/index.css';

interface ExcalidrawRendererProps {
  data?: any; // Excalidraw JSON格式数据
}

/**
 * Excalidraw渲染组件
 * 简化版本，先确保组件能正确嵌入和显示
 */
const ExcalidrawRenderer: React.FC<ExcalidrawRendererProps> = ({ data }) => {
  const [isClient, setIsClient] = useState(false);

  // 确保只在客户端环境中运行
  useEffect(() => {
    setIsClient(true);
  }, []);

  // 简单的初始数据，包含一些基本元素用于测试
  const getInitialData = () => {
    return {
      elements: [
        {
          id: "rect-1",
          type: "rectangle",
          x: 100,
          y: 100,
          width: 100,
          height: 100,
          strokeColor: "#1e1e1e",
          backgroundColor: "#a5d8ff",
          fillStyle: "solid",
          strokeWidth: 2,
          roughness: 1,
          opacity: 100,
        },
        {
          id: "text-1",
          type: "text",
          x: 120,
          y: 140,
          text: "Hello Excalidraw!",
          fontSize: 20,
          fontFamily: 1,
          textAlign: "left",
          verticalAlign: "top",
          strokeColor: "#1e1e1e",
          backgroundColor: "transparent",
        }
      ],
      appState: {
        viewBackgroundColor: "#ffffff",
      },
      files: null
    };
  };

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

  return (
    <div className="mt-4 p-4 border border-gray-200 rounded-lg bg-white">
      <div className="text-sm text-gray-600 mb-2">🎨 绘图内容</div>

      <div className="border rounded" style={{ height: '400px' }}>
        <Excalidraw />
      </div>
    </div>
  );
};

export default ExcalidrawRenderer;
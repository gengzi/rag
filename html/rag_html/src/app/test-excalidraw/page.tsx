import React from 'react';
import ExcalidrawRenderer from '@/components/chat/ExcalidrawRenderer';

/**
 * 测试页面 - 用于验证 Excalidraw 组件是否能正确嵌入
 */
export default function TestExcalidraw() {
  return (
    <div className="container mx-auto p-8">
      <h1 className="text-2xl font-bold mb-6">Excalidraw 组件测试</h1>

      <div className="mb-8">
        <h2 className="text-xl font-semibold mb-4">基础 Excalidraw 组件</h2>
        <p className="text-gray-600 mb-4">
          这是一个最简单的 Excalidraw 组件测试，应该显示一个可以绘制的空白画板。
        </p>

        <ExcalidrawRenderer />
      </div>

      <div className="mt-8 p-4 bg-blue-50 border border-blue-200 rounded">
        <h3 className="font-semibold text-blue-800 mb-2">检查清单：</h3>
        <ul className="list-disc list-inside text-blue-700 space-y-1">
          <li>Excalidraw 组件是否正常加载？</li>
          <li>画布是否显示？</li>
          <li>工具栏是否出现？</li>
          <li>能否进行绘图操作？</li>
          <li>控制台是否有错误信息？</li>
        </ul>
      </div>
    </div>
  );
}
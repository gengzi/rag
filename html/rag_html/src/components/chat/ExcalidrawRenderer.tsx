import React, { memo } from 'react';
import { Excalidraw, THEME } from '@excalidraw/excalidraw';

interface ExcalidrawRendererProps {
  data: any; // Excalidraw JSON格式数据
}

/**
 * Excalidraw渲染组件
 * 用于在聊天界面中渲染Excalidraw图形内容
 */
const ExcalidrawRenderer: React.FC<ExcalidrawRendererProps> = memo(({ data }) => {
  
  // 配置默认的Excalidraw初始化选项
  const excalidrawOptions = {
    initialData: data,
    width: '100%',
    height: 500,
    theme: THEME.LIGHT,
    readOnly: true, // 渲染模式，用户不能编辑
    gridSize: null,
    viewModeEnabled: true, // 启用查看模式
    ui: { 
      buttons: false, // 隐藏默认按钮
      panels: false, // 隐藏面板
      themeSwitch: false, // 隐藏主题切换
    },
    scrollToContent: true,
  };

  return (
    <div className="mt-4 p-2 border border-gray-200 rounded-lg bg-white overflow-hidden shadow-sm">
      <div className="text-sm text-gray-600 mb-2 px-2">
        🎨 绘图内容
      </div>
      <div className="excalidraw-container">
        <Excalidraw
          {...excalidrawOptions}
        />
      </div>
      <style jsx>{`
        .excalidraw-container {
          min-height: 300px;
          max-height: 600px;
          overflow: auto;
        }
      `}</style>
    </div>
  );
});

ExcalidrawRenderer.displayName = 'ExcalidrawRenderer';

export default ExcalidrawRenderer;
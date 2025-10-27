'use client';

import { useRouter } from 'next/navigation';
import FilePreview from '@/components/file-preview/file-preview';

interface FilePreviewPageProps {
  params: {
    documentId: string;
  };
}

const FilePreviewPage = ({ params }: FilePreviewPageProps) => {
  const router = useRouter();

  // 返回上一页或关闭窗口的处理函数
  const handleBack = () => {
    // 先尝试返回上一页
    if (window.document.referrer) {
      router.back();
    } else {
      // 如果没有上一页引用，可以选择直接关闭窗口
      // 注意：这在某些浏览器安全设置下可能无法正常工作
      if (typeof window !== 'undefined') {
        window.close();
      }
    }
  };

  // 获取路由参数中的documentId
  const { documentId } = params;

  return (
    <div className="w-full">
      <FilePreview 
        documentId={documentId} 
        onBack={handleBack} 
      />
    </div>
  );
};

export default FilePreviewPage;
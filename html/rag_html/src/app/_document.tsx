import { Html, Head, Main, NextScript } from 'next/document';

export default function Document() {
  return (
    <Html lang="zh-CN">
      <Head>
        {/* 可以在这里添加全局样式或其他头部元素 */}
      </Head>
      <body>
        <Main />
        <NextScript />
      </body>
    </Html>
  );
}
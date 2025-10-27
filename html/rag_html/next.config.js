/** @type {import('next').NextConfig} */
module.exports = {
  output: "standalone",
  reactStrictMode: false, // 禁用React严格模式，避免双重渲染
  experimental: {
    // This is needed for standalone output to work correctly
    outputFileTracingRoot: undefined,
  },
  // 添加API请求代理配置
  async rewrites() {
    return [
      {
        source: '/chat/:path*',
        destination: 'http://localhost:8883/chat/:path*',
      },
    ];
  },
};

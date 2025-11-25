// 简单的聊天功能测试
const testChat = async () => {
  console.log('Testing chat functionality...');

  try {
    // 测试获取聊天历史API
    const response = await fetch('http://localhost:8086/chat/msg/test-id/list', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        limit: 10,
        before: ''
      })
    });

    console.log('API Response status:', response.status);
    const data = await response.json();
    console.log('API Response data:', data);

  } catch (error) {
    console.error('API Test failed:', error);
  }
};

// 如果直接运行此脚本
if (typeof window === 'undefined') {
  testChat();
}
## 问题分析
当 `runMessageId` 存在时，页面组件中的 `useEffect` 会调用 `sendMessage`，但 `runMessageId` 状态不会被清除，导致 `useEffect` 不断触发，形成无限循环，从而不断请求 `stream/msg` 接口。

## 解决方案
1. **添加状态跟踪处理状态**：在组件中添加一个状态 `hasHandledRunMessageId`，用于跟踪是否已经处理过 `runMessageId`
2. **优化 useEffect 逻辑**：
   - 当 `runMessageId` 变化且未处理过时，调用 `sendMessage`
   - 调用后设置 `hasHandledRunMessageId` 为 `true`
   - 当 `runMessageId` 变化时，重置 `hasHandledRunMessageId` 为 `false`
3. **确保只处理一次**：通过状态管理确保每个 `runMessageId` 只被处理一次

## 具体修改
1. 在 `src/app/dashboard/chat/v2/[id]/page.tsx` 中添加 `hasHandledRunMessageId` 状态
2. 修改 `useEffect` 逻辑，添加处理状态判断
3. 确保 `runMessageId` 变化时重置处理状态

## 预期效果
- 每个 `runMessageId` 只会触发一次 `sendMessage` 调用
- 避免无限循环请求 `stream/msg` 接口
- 保持原有功能不变，即加载聊天记录后自动调用对话接口获取流式输出
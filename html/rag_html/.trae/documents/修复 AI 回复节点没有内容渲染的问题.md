## 问题分析
1. **`agent` 类型消息清空 `textContentBuffer`**：在 `processStreamData` 函数中，处理 `agent` 类型消息时会清空 `textContentBuffer`，导致 `assistantMessage` 的 `content` 字段为空
2. **`agent` 类型节点默认折叠**：在 `AgentAnswer` 组件中，`agent` 类型节点默认是折叠状态，用户需要点击才能看到内容
3. **流式输出没有正确渲染到消息体**：由于 `textContentBuffer` 被清空，AI 回复的消息体没有内容

## 解决方案
1. **修改 `processStreamData` 函数，保留 `textContentBuffer`**：处理 `agent` 类型消息时不要清空 `textContentBuffer`，确保消息体有内容
2. **修改 `AgentAnswer` 组件，默认展开 `agent` 节点**：将 `expandedAgents` 状态默认值改为所有 `agent` 节点都展开，方便用户查看内容
3. **优化 `agent` 节点内容渲染**：确保 `agent` 节点的 `description` 内容正确显示

## 具体修改
1. 在 `src/utils/messageFormatter.ts` 中：
   - 修改 `processStreamData` 函数，处理 `agent` 类型消息时不要清空 `textContentBuffer`

2. 在 `src/components/chat/agent-answer.tsx` 中：
   - 修改 `expandedAgents` 状态初始化逻辑，默认展开所有 `agent` 节点
   - 优化 `agent` 节点内容渲染，确保 `description` 内容正确显示

## 预期效果
- AI 回复的消息体有内容，不再是空的
- `agent` 类型节点默认展开，用户无需点击即可看到内容
- 流式输出正确渲染到 AI 助手回复的消息体中

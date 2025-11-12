package com.gengzi.node;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Map;
import java.util.function.Function;

/**
 * 定义默认的结果转换流程，如果无特殊要求，可以使用此结果转换
 * 将 ChatResponse 转为 Map<String, Object> 返回当前调用llm后，合并流示输出内容到某个结果节点存储
 */
public class DefaultMapToResult implements Function<ChatResponse, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(HumanFeedbackNode.class);

    private final String key;

    private DefaultMapToResult(Builder builder) {
        this.key = builder.key;
    }

    public static Builder builder(String key) {
        return new Builder(key); // 内部 new，但外部调用更简洁
    }

    @Override
    public Map<String, Object> apply(ChatResponse chatResponse) {
        logger.info("DefaultMapToResult apply");
        String text = chatResponse.getResult().getOutput().getText();
        // 原样存储text内容，不做处理
        return Map.of(key, text);
    }

    public static class Builder {
        private final String key;

        public Builder(String key) {
            this.key = key;
        }

        public DefaultMapToResult build() {
            if (StrUtil.isBlank(key)) {
                throw new IllegalArgumentException("key is not bank");
            }
            return new DefaultMapToResult(this);
        }
    }
}

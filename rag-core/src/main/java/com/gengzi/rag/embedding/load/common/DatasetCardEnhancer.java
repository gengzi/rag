package com.gengzi.rag.embedding.load.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 数据集卡片增强器
 * 使用 LLM 将结构化的数据集信息转换为自然语言描述，提升向量检索效果
 * 
 * @author: gengzi
 */
@Component
public class DatasetCardEnhancer {

    private static final Logger logger = LoggerFactory.getLogger(DatasetCardEnhancer.class);

    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    /**
     * 使用 LLM 增强 Dataset Card
     * 
     * @param rawCard  原始结构化卡片内容
     * @param fileName 文件名（用于日志记录）
     * @return LLM 生成的自然语言描述 + 原始结构化信息
     * @throws Exception LLM 调用失败时抛出异常
     */
    public String enhance(String rawCard, String fileName) throws Exception {
        SystemMessage systemMessage = new SystemMessage(
                "你是一名数据分析专家，擅长为数据集生成清晰、易于检索的自然语言描述。\n" +
                        "你的任务是：\n" +
                        "1. 分析数据集的结构和内容\n" +
                        "2. 推断数据集的业务用途和应用场景\n" +
                        "3. 提取关键业务术语和概念\n" +
                        "4. 生成一份便于向量检索的数据集卡片\n\n" +
                        "输出格式要求：\n" +
                        "【数据集概要】\n" +
                        "- 用 1-2 句话描述数据集的核心内容和用途\n" +
                        "- 提及关键字段和数据类型\n\n" +
                        "【业务场景】\n" +
                        "- 推断这个数据集可能用于什么业务分析\n" +
                        "- 适合回答哪些类型的问题\n\n" +
                        "【关键字段说明】\n" +
                        "- 列出 5-10 个最重要的字段\n" +
                        "- 每个字段用自然语言描述其含义和用途\n\n" +
                        "【数据特征】\n" +
                        "- 记录数量\n" +
                        "- 数据时间范围（如果有时间字段）\n" +
                        "- 其他重要统计特征\n\n" +
                        "注意：使用自然、流畅的语言，避免生硬的技术术语堆砌。目标是让用户通过自然语言查询就能准确匹配到这个数据集。");

        UserMessage userMessage = new UserMessage(
                "请为以下数据集生成一份易于检索的自然语言卡片：\n\n" + rawCard);

        logger.debug("开始 LLM 增强 Dataset Card: {}", fileName);
        String response = chatModel.call(systemMessage, userMessage);
        logger.debug("LLM 增强完成");

        // 在 LLM 输出后追加原始结构化信息，确保信息完整性
        return response + "\n\n=== 原始结构化信息 ===\n" + rawCard;
    }

    /**
     * 安全的增强方法，失败时返回原始卡片
     * 
     * @param rawCard  原始结构化卡片内容
     * @param fileName 文件名
     * @return LLM 增强后的卡片，失败时返回原始卡片
     */
    public String enhanceSafely(String rawCard, String fileName) {
        try {
            String enhanced = enhance(rawCard, fileName);
            logger.info("LLM 增强 Dataset Card 成功: {}", fileName);
            return enhanced;
        } catch (Exception e) {
            logger.warn("LLM 增强 Dataset Card 失败，使用原始版本: {} - {}", fileName, e.getMessage());
            return rawCard;
        }
    }
}

package com.gengzi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * LLM 提示词配置属性类
 *
 * <p>负责管理和加载各种 LLM 提示词模板，支持：</p>
 * <ul>
 *   <li>从配置文件读取提示词路径</li>
 *   <li>从 classpath 加载提示词文件内容</li>
 *   <li>提供便捷的提示词获取方法</li>
 *   <li>支持实体识别和社区总结等场景</li>
 * </ul>
 *
 * <p>配置前缀：`rag.prompt`</p>
 *
 * <p>配置示例（application.yml）：</p>
 * <pre>
 * rag:
 *   prompt:
 *     entity-recognition: prompt/ENTITY_RECOGNITION_PROMPT.md
 *     community-report: prompt/COMMUNITY_REPORT_PROMPT.md
 *     query-analysis: prompt/QUERY_ANALYSIS_PROMPT.md
 * </pre>
 *
 * <p>提示词文件位置：</p>
 * <pre>
 * src/main/resources/prompt/
 * ├── ENTITY_RECOGNITION_PROMPT.md    # 实体识别提示词
 * ├── COMMUNITY_REPORT_PROMPT.md       # 社区总结提示词
 * └── QUERY_ANALYSIS_PROMPT.md         # 查询分析提示词
 * </pre>
 *
 * @author RAG Graph Development Team
 * @version 1.0
 * @see ConfigurationProperties
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.prompt")
public class PromptProperties {

    /**
     * 实体识别提示词文件路径
     *
     * <p>此提示词用于从文本中提取实体和关系，包括：</p>
     * <ul>
     *   <li>实体识别（人物、组织、概念等）</li>
     *   <li>关系提取（包含、属于、相关等）</li>
     *   <li>实体类型和描述</li>
     *   <li>关系类型和描述</li>
     * </ul>
     *
     * <p>默认值：`prompt/ENTITY_RECOGNITION_PROMPT.md`</p>
     * <p>可在配置文件中通过 `rag.prompt.entity-recognition` 覆盖</p>
     */
    private String entityRecognition = "prompt/ENTITY_RECOGNITION_PROMPT.md";

    /**
     * 社区总结提示词文件路径
     *
     * <p>此提示词用于为社区生成总结报告，包括：</p>
     * <ul>
     *   <li>社区标题（5-10字）</li>
     *   <li>社区摘要（50-100字）</li>
     *   <li>置信度评分（0-10分）</li>
     *   <li>关键词提取</li>
     * </ul>
     *
     * <p>默认值：`prompt/COMMUNITY_REPORT_PROMPT.md`</p>
     * <p>可在配置文件中通过 `rag.prompt.community-report` 覆盖</p>
     */
    private String communityReport = "prompt/COMMUNITY_REPORT_PROMPT.md";

    /**
     * 查询分析提示词文件路径
     *
     * <p>此提示词用于分析用户查询，包括：</p>
     * <ul>
     *   <li>查询意图识别</li>
     *   <li>关键实体提取</li>
     *   <li>查询类型判断</li>
     *   <li>相关概念推荐</li>
     * </ul>
     *
     * <p>默认值：`prompt/QUERY_ANALYSIS_PROMPT.md`</p>
     * <p>可在配置文件中通过 `rag.prompt.query-analysis` 覆盖</p>
     */
    private String queryAnalysis = "prompt/QUERY_ANALYSIS_PROMPT.md";

    /**
     * 加载实体识别提示词内容
     *
     * <p>从 classpath 中读取实体识别提示词文件内容，
     * 用于 LLM 实体和关系提取。</p>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>从文档分块中提取实体</li>
     *   <li>构建知识图谱</li>
     *   <li>实体关系发现</li>
     * </ul>
     *
     * @return 提示词内容字符串
     * @throws IllegalStateException 如果文件不存在或读取失败
     */
    public String loadEntityRecognitionPrompt() {
        return loadPromptFromFile(entityRecognition);
    }

    /**
     * 加载社区总结提示词内容
     *
     * <p>从 classpath 中读取社区总结提示词文件内容，
     * 用于 LLM 生成社区报告。</p>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>社区发现后生成总结</li>
     *   <li>社区主题归纳</li>
     *   <li>知识图谱可解释性</li>
     * </ul>
     *
     * @return 提示词内容字符串
     * @throws IllegalStateException 如果文件不存在或读取失败
     */
    public String loadCommunityReportPrompt() {
        return loadPromptFromFile(communityReport);
    }

    /**
     * 加载查询分析提示词内容
     *
     * <p>从 classpath 中读取查询分析提示词文件内容，
     * 用于 LLM 分析用户查询。</p>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>用户查询意图分析</li>
     *   <li>关键实体提取</li>
     *   <li>查询扩展和优化</li>
     *   <li>相关概念推荐</li>
     * </ul>
     *
     * @return 提示词内容字符串
     * @throws IllegalStateException 如果文件不存在或读取失败
     */
    public String loadQueryAnalysisPrompt() {
        return loadPromptFromFile(queryAnalysis);
    }

    /**
     * 从 classpath 文件加载提示词内容
     *
     * <p>此方法从 classpath 中读取指定路径的文件内容，
     * 并转换为 UTF-8 字符串。</p>
     *
     * <p>文件查找逻辑：</p>
     * <ol>
     *   <li>使用 Spring 的 ClassPathResource 定位文件</li>
     *   <li>从 classpath 根路径开始查找</li>
     *   <li>使用 UTF-8 编码读取文件内容</li>
     *   <li>如果文件不存在，抛出 IllegalStateException</li>
     * </ol>
     *
     * @param path 提示词文件的 classpath 路径
     * @return 文件内容的字符串形式
     * @throws IllegalStateException 如果文件不存在或读取失败
     */
    private String loadPromptFromFile(String path) {
        try {
            // 使用 ClassPathResource 从 classpath 加载文件
            ClassPathResource resource = new ClassPathResource(path);

            // 以 UTF-8 编码读取文件内容
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 文件不存在或读取失败，抛出运行时异常
            throw new IllegalStateException("无法加载提示词文件: " + path, e);
        }
    }
}

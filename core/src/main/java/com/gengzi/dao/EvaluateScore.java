package com.gengzi.dao;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/**
 * rag评估批次得分表
 */
@Getter
@Setter
@Entity
@Table(name = "evaluate_score", schema = "rag_db", uniqueConstraints = {
        @UniqueConstraint(name = "batch_num_UNIQUE", columnNames = {"batch_num"})
})
public class EvaluateScore {
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * 批次号
     */
    @Size(max = 32)
    @NotNull
    @Column(name = "batch_num", nullable = false, length = 32)
    private String batchNum;

    /**
     * 创建时间
     */
    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    /**
     * 忠实度:评估llm回答是否忠实上下文中的事实，防止回答夸大和偏离
     实现逻辑：通过llm  将检索到上下文信息，与llm回答内容进行评估，计算一个匹配度得分（0-1）

     生成指标
     */
    @Size(max = 32)
    @Column(name = "faithfulness_average_score", length = 32)
    private String faithfulnessAverageScore;

    /**
     * 答案相关性：衡量llm回答是否有效的响应了用户的原始查询
     实现逻辑：使用llm 对比问题和答案关联程度（0-1）


     生成指标
     */
    @Size(max = 32)
    @Column(name = "answer_relevancy_average_score", length = 32)
    private String answerRelevancyAverageScore;

    /**
     * 答案相似度：评估生成答案与标准答案的相似度
     实现逻辑：使用llm 来判断，生成答案与标准答案的相似度


     综合指标（结合检索与生成）
     */
    @Size(max = 32)
    @Column(name = "answer_similarity_average_score", length = 32)
    private String answerSimilarityAverageScore;

    /**
     * 上下文召回率：检索到的上下文块是否完整覆盖了用户问题的所需关键信息
     计算公式：上下文召回率 = （检索到的上下文中包含的关键事实数量） / （标准答案中所有关键事实的总数量）
     在我们设计的存在每个块的id，可以通过块id计算
     公式：判断检索到的块id是否在 标准答案块id 中存在 （存在1） 不存在 （0） 部分存在 成功匹配的块id个数 / 标准答案块id个数

     检索相关指标

     */
    @Size(max = 32)
    @Column(name = "context_recall_average_score", length = 32)
    private String contextRecallAverageScore;

    /**
     * 上下文精确度：检索的上下文中有信息与无用信息的比例
     实现逻辑：在我们设计中，有用信息就是标准答案的块id
     判断我们检索出的块id 是否在标准答案块id中，在 就使用 匹配的检索块id /  检索出的块总数
     如果检索出的块id 不存在标准答案块中， 那有用信息为0， 0/ 检索出块的总数

     检索相关指标
     */
    @Size(max = 32)
    @Column(name = "context_precision_average_score", length = 32)
    private String contextPrecisionAverageScore;

    /**
     * 上下文相关性：衡量检索到的文档与用户问题的相关性
     实现逻辑：通过llm 将用户问题和检索的文档判断相关性（一个问题，可能匹配多个块信息，取每个块的分数，求一个平均值）

     检索相关指标
     */
    @Size(max = 32)
    @Column(name = "context_relevancy_average_score", length = 32)
    private String contextRelevancyAverageScore;

    /**
     * 综合平均得分： Σ（基础指标得分 × 指标权重）
     */
    @Size(max = 32)
    @Column(name = "overall_score", length = 32)
    private String overallScore;

    @Lob
    @Column(name = "direction_improvement")
    private String directionImprovement;

    @Size(max = 32)
    @Column(name = "size", length = 32)
    private String size;

}
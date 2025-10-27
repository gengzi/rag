package com.gengzi.ui.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.gengzi.config.WeightOfBasicIndicators;
import com.gengzi.dao.Document;
import com.gengzi.dao.EvaluateDatum;
import com.gengzi.dao.EvaluateScore;
import com.gengzi.dao.repository.DocumentRepository;
import com.gengzi.dao.repository.EvaluateDatumRepository;
import com.gengzi.dao.repository.EvaluateScoreRepository;
import com.gengzi.request.*;
import com.gengzi.response.ChatAnswerResponse;
import com.gengzi.search.service.ChatRagService;
import com.gengzi.ui.service.EvaluateService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvaluateServiceImpl implements EvaluateService {

    private static final Logger logger = LoggerFactory.getLogger(EvaluateServiceImpl.class);
    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

//    @Autowired
//    @Qualifier("openAiChatModelOutPutJson")
//    private ChatModel chatModelOutPutJson;

    @Autowired
    private EvaluateDatumRepository evaluateDatumRepository;

    @Autowired
    private ChatRagService chatRagService;

    @Autowired
    private WeightOfBasicIndicators weightOfBasicIndicators;

    @Autowired
    private EvaluateScoreRepository evaluateScoreRepository;

    private static List<Question> updateQuestion(List<Question> question1, List<Question> question2) {
        ArrayList<Question> questions = new ArrayList<>();
        for (int i = 0; i < question1.size(); i++) {
            Question question = question1.get(i);
            Question questionListQuestion = question2.get(i);
            question.setQuestion(question.getQuestion() + "." + questionListQuestion.getQuestion());
            question.setReferenceAnswer(question.getReferenceAnswer() + "." + questionListQuestion.getReferenceAnswer());
            ArrayList<String> docs = new ArrayList<>();
            docs.addAll(question.getRelatedDocumentList());
            docs.addAll(questionListQuestion.getRelatedDocumentList());
            question.setRelatedDocumentList(docs);
            ArrayList<String> chunks = new ArrayList<>();
            chunks.addAll(question.getRelatedChunkIds());
            chunks.addAll(questionListQuestion.getRelatedChunkIds());
            question.setRelatedChunkIds(chunks);
            questions.add(question);
        }
        return questions;
    }

    /**
     * 如果是存在相关文档，请在一个批次中全部处理。保证生成的预估训练集数据的完整性
     * 如：
     * 文档1：k8s的内容
     * 文档2：k8s的部署信息
     * 这就是相关文档，某个问题可能要从这两个文档的文档块中检索出答案
     *
     * @param documentIds
     * @param batchNum
     * @throws IOException
     */
    @Override
    public void evaluateGenerate(List<String> documentIds, String batchNum) throws IOException {
        List<Document> documentByChunkNumGreaterThan = documentRepository.findDocumentByChunkNumGreaterThan(1);
        for (Document document : documentByChunkNumGreaterThan) {

            // 获取文档的 id
            String documentId = document.getId();
            if (documentIds != null && documentIds.size() > 0 && !documentIds.contains(documentId)) {
                continue;
            }
            // 查询es获取文档块列表
            List<Map> documents = (List<Map>) findByMetadataDocumentId(documentId);
            logger.info("documents:{}", documents.get(0).toString());
            ArrayList<EvaluateDocument> evaluateDocuments = new ArrayList<>();
            // 构造json串
            documents.forEach(v -> {
                String content = v.get("content").toString();
                String chunkId = v.get("id").toString();
                EvaluateDocument evaluateDocument = new EvaluateDocument();
                evaluateDocument.setDocumentId(documentId);
                evaluateDocument.setChunkId(chunkId);
                evaluateDocument.setContent(content);

                evaluateDocuments.add(evaluateDocument);
            });
            String evaluateDocumentStr = JSONUtil.toJsonStr(evaluateDocuments);
            String questionAndAnswers = documentQuestionAndAnswer(evaluateDocumentStr);
            logger.info("questionAndAnswers:{}", questionAndAnswers);
            try {
                if (JSONUtil.isJson(questionAndAnswers)) {
                    save(questionAndAnswers, documentId, batchNum);
                }
            } catch (Exception e) {
                logger.error("save error:{}", e.getMessage(), e);
                continue;
            }

        }
    }

    /**
     * @param coonversationId
     */
    @Override
    public void evaluate(String coonversationId, String batchNum) {
        List<EvaluateDatum> evaluateDataByLlmAnswerIsEmpty = evaluateDatumRepository.findByLlmAnswerEqualsAndBatchNumEquals(batchNum);
        evaluateDataByLlmAnswerIsEmpty.forEach(evaluateDatum -> {
            RagChatReq ragChatReq = new RagChatReq();
            ragChatReq.setQuestion(evaluateDatum.getQuestion());
            ragChatReq.setConversationId(coonversationId);
            ChatAnswerResponse chatAnswerResponse = chatRagService.chatRagEvaluate(ragChatReq);
            llmAnswerSave(evaluateDatum, chatAnswerResponse);

        });
    }

    /**
     * @param batchNum
     */
    @Override
    @Async("asyncExecutor")
    public void evaluate(String batchNum) {
        List<EvaluateDatum> evaluateDataByBatchNum = evaluateDatumRepository.findEvaluateDataByBatchNum(batchNum);
        String kbId = evaluateDataByBatchNum.stream().findFirst().get().getKbId();
        String coonversationId = chatRagService.createEvaluateConversation(kbId);
        asyncExec(batchNum, coonversationId);
        evaluateCalculate(batchNum);
        evaluateStatistics(batchNum);
    }


    public void asyncExec(String batchNum, String coonversationId) {
        List<EvaluateDatum> evaluateDataByLlmAnswerIsEmpty = evaluateDatumRepository.findByLlmAnswerEqualsAndBatchNumEquals(batchNum);
        evaluateDataByLlmAnswerIsEmpty.forEach(evaluateDatum -> {
            RagChatReq ragChatReq = new RagChatReq();
            ragChatReq.setQuestion(evaluateDatum.getQuestion());
            ragChatReq.setConversationId(coonversationId);
            ChatAnswerResponse chatAnswerResponse = chatRagService.chatRagEvaluate(ragChatReq);
            llmAnswerSave(evaluateDatum, chatAnswerResponse);
        });
    }

    /**
     * 计算指标
     *
     * @param batchNum
     */
    @Override
    public void evaluateCalculate(String batchNum) {
        List<EvaluateDatum> evaluateDataByBatchNum = evaluateDatumRepository.findEvaluateDataByBatchNum(batchNum);
        evaluateDataByBatchNum.forEach(evaluateDatum -> {
            if (StrUtil.isBlank(evaluateDatum.getLlmAnswer())) {
                return;
            }
            if (StrUtil.isNotBlank(evaluateDatum.getContextRecall())) {
                calculatedMetricstemp(evaluateDatum);
                return;
            }

            calculatedMetrics(evaluateDatum);
        });


    }

    /**
     * 统计评估结果
     *
     * @param batchNum
     */
    @Override
    public void evaluateStatistics(String batchNum) {
        String totalScore = "0";
        String faithfulnessAverageScore = "0";
        String answerSimilarityAverageScore = "0";
        String answerRelevancyAverageScore = "0";
        String contextRecallAverageScore = "0";
        String contextPrecisionAverageScore = "0";
        String contextRelevancyAverageScore = "0";

        // 根据批次查询指标信息
        List<EvaluateDatum> evaluateDataByBatchNum = evaluateDatumRepository.findEvaluateDataByBatchNum(batchNum);
        String size = evaluateDataByBatchNum.size() + "";
        for (EvaluateDatum evaluateDatum : evaluateDataByBatchNum) {
            // 将指标信息按权重进行加权平均，计算每条的数据得分
            saveScore(evaluateDatum);
            // 将每项得分相加求平均，得到每项指标的得分
            String score = evaluateDatum.getScore();
            totalScore = NumberUtil.add(score, totalScore).toPlainString();
            faithfulnessAverageScore = NumberUtil.add(evaluateDatum.getFaithfulness(), faithfulnessAverageScore).toPlainString();
            answerSimilarityAverageScore = NumberUtil.add(evaluateDatum.getAnswerSimilarity(), answerSimilarityAverageScore).toPlainString();
            answerRelevancyAverageScore = NumberUtil.add(evaluateDatum.getAnswerRelevancy(), answerRelevancyAverageScore).toPlainString();
            contextRecallAverageScore = NumberUtil.add(evaluateDatum.getContextRecall(), contextRecallAverageScore).toPlainString();
            contextPrecisionAverageScore = NumberUtil.add(evaluateDatum.getContextPrecision(), contextPrecisionAverageScore).toPlainString();
            contextRelevancyAverageScore = NumberUtil.add(evaluateDatum.getContextRelevancy(), contextRelevancyAverageScore).toPlainString();
        }
        EvaluateScore evaluateScore = new EvaluateScore();
        List<EvaluateScore> byBatchNum = evaluateScoreRepository.findByBatchNum(batchNum);
        if (byBatchNum != null && byBatchNum.size() > 0) {
            evaluateScore = byBatchNum.get(0);
        }
        evaluateScore.setBatchNum(batchNum);
        evaluateScore.setCreateTime(Instant.now());
        evaluateScore.setOverallScore(NumberUtil.div(totalScore, size, 3).toPlainString());
        evaluateScore.setFaithfulnessAverageScore(NumberUtil.div(faithfulnessAverageScore, size, 3).toPlainString());
        evaluateScore.setAnswerSimilarityAverageScore(NumberUtil.div(answerSimilarityAverageScore, size, 3).toPlainString());
        evaluateScore.setAnswerRelevancyAverageScore(NumberUtil.div(answerRelevancyAverageScore, size, 3).toPlainString());
        evaluateScore.setContextRecallAverageScore(NumberUtil.div(contextRecallAverageScore, size, 3).toPlainString());
        evaluateScore.setContextPrecisionAverageScore(NumberUtil.div(contextPrecisionAverageScore, size, 3).toPlainString());
        evaluateScore.setContextRelevancyAverageScore(NumberUtil.div(contextRelevancyAverageScore, size, 3).toPlainString());
        String directionImprovement = directionImprovementByIndicator(evaluateScore);
        evaluateScore.setDirectionImprovement(directionImprovement);
        evaluateScore.setSize(size);
        evaluateScoreRepository.save(evaluateScore);

    }

    /**
     * 获取统计折线图信息
     *
     * @return
     */
    @Override
    public List<?> evaluateStatisticsLineChart() {
        List<EvaluateScore> all = evaluateScoreRepository.findAll();
        return all;
    }

    /**
     * @param batchNum
     * @return
     */
    @Override
    public Page<?> evaluateStatisticsByBatchNum(String batchNum, Pageable pageable) {
        // 构建动态查询条件
        Specification<EvaluateDatum> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 条件1：名称模糊匹配（如果name不为空）
            if (batchNum != null && !batchNum.isEmpty()) {
                predicates.add(cb.equal(root.get("batchNum"), batchNum));
            }
            // 添加排序：按 updateTime 倒序（如果需要按 createTime 排序，替换字段名即可）
            query.orderBy(cb.desc(root.get("createTime")));
            // 组合所有条件
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return evaluateDatumRepository.findAll(spec, pageable);

    }

    /**
     * @return
     */
    @Override
    public List<?> evaluateStatisticsBatchNums(Boolean isUntrainedBatch) {
        if (isUntrainedBatch != null && isUntrainedBatch) {
            return evaluateDatumRepository.findUntrainedBatchNums();
        }
        return evaluateDatumRepository.findAllBatchNums();
    }

    /**
     * 创建评估
     *
     * @param req
     */
    @Override
    @Async
    public void evaluateCreate(EvaluateCreateReq req) throws IOException {
        // 单文档生成
        List<Document> singledocuments = documentRepository.findDocumentByIdIn(req.getSingleDocumentIds());
        String batchNum = req.getBatchNum();

        for (Document document : singledocuments) {
            // 获取文档的 id
            String documentId = document.getId();
            // 查询es获取文档块列表
            List<Map> documents = (List<Map>) findByMetadataDocumentId(documentId);
            logger.info("documents:{}", documents.get(0).toString());
            ArrayList<EvaluateDocument> evaluateDocuments = new ArrayList<>();
            // 构造json串
            documents.forEach(v -> {
                String content = v.get("content").toString();
                String chunkId = v.get("id").toString();
                EvaluateDocument evaluateDocument = new EvaluateDocument();
                evaluateDocument.setDocumentId(documentId);
                evaluateDocument.setChunkId(chunkId);
                evaluateDocument.setContent(content);

                evaluateDocuments.add(evaluateDocument);
            });
            String evaluateDocumentStr = JSONUtil.toJsonStr(evaluateDocuments);
            String questionAndAnswers = documentQuestionAndAnswerBySingle(evaluateDocumentStr, req.isColloquial());
            logger.info("questionAndAnswers:{}", questionAndAnswers);
            try {
                if (JSONUtil.isJson(questionAndAnswers)) {
                    List<Question> questionList = JSONUtil.toList(questionAndAnswers, Question.class);
                    saveEvaluateDatum(questionList, req);
                }
            } catch (Exception e) {
                logger.error("save error:{}", e.getMessage(), e);
                continue;
            }

        }

        for (List<String> multipleDocumentId : req.getMultipleDocumentIds()) {
            List<Question> questions = new ArrayList<>();
            // 多文档联合生成
            List<Document> multipledocuments = documentRepository.findDocumentByIdIn(multipleDocumentId);
            for (Document document : multipledocuments) {
                // 获取文档的 id
                String documentId = document.getId();
                ArrayList<EvaluateDocument> evaluateDocuments = new ArrayList<>();
                // 查询es获取文档块列表
                List<Map> documents = (List<Map>) findByMetadataDocumentId(documentId);
                logger.info("documents:{}", documents.get(0).toString());
                // 构造json串
                documents.forEach(v -> {
                    String content = v.get("content").toString();
                    String chunkId = v.get("id").toString();
                    EvaluateDocument evaluateDocument = new EvaluateDocument();
                    evaluateDocument.setDocumentId(documentId);
                    evaluateDocument.setChunkId(chunkId);
                    evaluateDocument.setContent(content);
                    evaluateDocuments.add(evaluateDocument);
                });
                String evaluateDocumentStr = JSONUtil.toJsonStr(evaluateDocuments);
                String questionAndAnswers = documentQuestionAndAnswerBySingle(evaluateDocumentStr, req.isColloquial());
                logger.info("questionAndAnswers:{}", questionAndAnswers);
                try {
                    if (JSONUtil.isJson(questionAndAnswers)) {
                        List<Question> questionList = JSONUtil.toList(questionAndAnswers, Question.class);
                        if (questions.size() > 0) {
                            if (questions.size() >= questionList.size()) {
                                questions = updateQuestion(questionList, questions);
                            } else {
                                questions = updateQuestion(questions, questionList);
                            }
                        } else {
                            questions.addAll(questionList);
                        }

                    }
                } catch (Exception e) {
                    logger.error("save error:{}", e.getMessage(), e);
                    continue;
                }
            }
            // 问题融合
            saveEvaluateDatum(questions, req);


        }


    }

    @Transactional(rollbackFor = Exception.class)
    public void saveScore(EvaluateDatum evaluateDatum) {
        String score = calculate(evaluateDatum);
        evaluateDatum.setScore(score);
        evaluateDatumRepository.save(evaluateDatum);
    }


    private String calculate(EvaluateDatum evaluateDatum) {
        BigDecimal faithfulnessSocre = NumberUtil.mul(evaluateDatum.getFaithfulness(), indicatorsFormat(weightOfBasicIndicators.getFaithfulness()));
        BigDecimal answerRelevancySocre = NumberUtil.mul(evaluateDatum.getAnswerRelevancy(), indicatorsFormat(weightOfBasicIndicators.getAnswerRelevancy()));
        BigDecimal answerSimilaritySocre = NumberUtil.mul(evaluateDatum.getAnswerSimilarity(), indicatorsFormat(weightOfBasicIndicators.getAnswerSimilarity()));
        BigDecimal contextRecallSocre = NumberUtil.mul(evaluateDatum.getContextRecall(), indicatorsFormat(weightOfBasicIndicators.getContextRecall()));
        BigDecimal contextPrecisionSocre = NumberUtil.mul(evaluateDatum.getContextPrecision(), indicatorsFormat(weightOfBasicIndicators.getContextPrecision()));
        BigDecimal contextRelevancySocre = NumberUtil.mul(evaluateDatum.getContextRelevancy(), indicatorsFormat(weightOfBasicIndicators.getContextRelevancy()));
        BigDecimal score = NumberUtil.add(faithfulnessSocre, answerRelevancySocre, answerSimilaritySocre, contextRecallSocre, contextPrecisionSocre, contextRelevancySocre);
        return NumberUtil.roundStr(score.toPlainString(), 3);

    }


    private String indicatorsFormat(String faithfulness) {
        // 1. 去除百分号
        String numStr = faithfulness.replace("%", "");
        // 2. 转换为BigDecimal并除以100
        BigDecimal decimal = new BigDecimal(numStr)
                .divide(new BigDecimal("100")); // 除以100得到小数
        return decimal.toPlainString();
    }

    @Transactional(rollbackFor = Exception.class)
    public void calculatedMetricstemp(EvaluateDatum evaluateDatum) {
        // 计算指标：检索指标
        // 上下文召回率
        String contextRecall = contextRecall(evaluateDatum);
        evaluateDatum.setContextRecall(contextRecall);
        // 上下文精确率
        String contextPrecision = contextPrecision(evaluateDatum);
        evaluateDatum.setContextPrecision(contextPrecision);
        // 上下文相关性
        evaluateDatumRepository.save(evaluateDatum);
    }

    @Transactional(rollbackFor = Exception.class)
    public void calculatedMetrics(EvaluateDatum evaluateDatum) {
        // 计算指标：检索指标
        // 上下文召回率
        String contextRecall = contextRecall(evaluateDatum);
        evaluateDatum.setContextRecall(contextRecall);
        // 上下文精确率
        String contextPrecision = contextPrecision(evaluateDatum);
        evaluateDatum.setContextPrecision(contextPrecision);
        // 上下文相关性
        String contextRelevancyLlmResult = jsonFormat(contextRelevancyReason(evaluateDatum));
        evaluateDatum.setContextRelevancyLlmResult(contextRelevancyLlmResult);
        String contextRelevancy = contextRelevancy(JSONUtil.toList(contextRelevancyLlmResult, RelevancyJson.class));
        evaluateDatum.setContextRelevancy(contextRelevancy);


        // 计算指标：生成指标
        // 忠实度
        String faithfulnessLlmResult = jsonFormat(faithfulnessReason(evaluateDatum));
        evaluateDatum.setFaithfulnessLlmResult(faithfulnessLlmResult);
        String faithfulness = faithfulness(JSONUtil.toBean(faithfulnessLlmResult, RelevanceJson.class));
        evaluateDatum.setFaithfulness(faithfulness);
        // 答案相关性
        String answerRelevancyLlmResult = jsonFormat(answerRelevancyReason(evaluateDatum));
        evaluateDatum.setAnswerRelevancyLlmResult(answerRelevancyLlmResult);
        String answerRelevancy = answerRelevancy(JSONUtil.toBean(answerRelevancyLlmResult, RelevanceJson.class));
        evaluateDatum.setAnswerRelevancy(answerRelevancy);
        // 答案相似度
        String answerSimilarityLlmResult = jsonFormat(answerSimilarityReason(evaluateDatum));
        evaluateDatum.setAnswerSimilarityLlmResult(answerSimilarityLlmResult);
        String answerSimilarity = answerSimilarity(JSONUtil.toBean(answerSimilarityLlmResult, RelevanceJson.class));
        evaluateDatum.setAnswerSimilarity(answerSimilarity);

        evaluateDatumRepository.save(evaluateDatum);
    }

    private String jsonFormat(String json) {
        if (json.startsWith("```json")) {
            String replace = json.replace("```json", "").replace("```", "");
            return replace.trim();
        }
        return json.trim();
    }

    private String answerSimilarity(RelevanceJson relevancyJsons) {
        return relevancyJsons.getScore();
    }


    private String answerSimilarityReason(EvaluateDatum evaluateDatum) {
        String llmAnswer = evaluateDatum.getLlmAnswer();
        String referenceAnswer = evaluateDatum.getReferenceAnswer();
        return answerSimilarityByQuestionAndAnswer(referenceAnswer, llmAnswer);
    }


    private String answerRelevancy(RelevanceJson relevancyJsons) {
        return relevancyJsons.getScore();
    }

    private String answerRelevancyReason(EvaluateDatum evaluateDatum) {
        String llmAnswer = evaluateDatum.getLlmAnswer();
        String question = evaluateDatum.getQuestion();
        return answerRelevanceByQuestionAndAnswer(question, llmAnswer);
    }

    private String faithfulness(RelevanceJson relevancyJsons) {
        return relevancyJsons.getScore();
    }


    private String faithfulnessReason(EvaluateDatum evaluateDatum) {
        String llmChunkId = evaluateDatum.getLlmChunkId();
        String llmAnswer = evaluateDatum.getLlmAnswer();

        List<String> llmChunkIds = JSONUtil.toList(llmChunkId, String.class).stream().distinct().collect(Collectors.toList());
        // 根据块id查询es获取文档块信息
        try {
            ArrayList<ChunkDeatils> contentList = new ArrayList<>();
            for (String chunkId : llmChunkIds) {
                List<Map> documents = (List<Map>) findByChunkId(chunkId);

                ChunkDeatils chunkDeatils = new ChunkDeatils();
                chunkDeatils.setContent((String) documents.get(0).get("content"));
                chunkDeatils.setChunkId(chunkId);
                contentList.add(chunkDeatils);
            }
            // 调用大模型获取评分
            return faithfulnessByContextAndAnswer(llmAnswer, contentList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 上下文召回率计算
     *
     * @param evaluateDatum
     * @return
     */
    private String contextRecall(EvaluateDatum evaluateDatum) {
        // 标准上下文块
        String chunkId = evaluateDatum.getChunkId();
        List<String> chunkIds = JSONUtil.toList(chunkId, String.class).stream().distinct().collect(Collectors.toList());
        // 检索出来的上下文块
        String llmChunkId = evaluateDatum.getLlmChunkId();
        List<String> llmChunkIds = JSONUtil.toList(llmChunkId, String.class).stream().distinct().collect(Collectors.toList());
        if (llmChunkIds.size() == 0) {
            return "0";
        }
        if (llmChunkIds.containsAll(chunkIds)) {
            // 检索出来的上下文块包含标准上下文块
            return "1";
        }
        int num = 0;
        for (String cid : llmChunkIds) {
            if (chunkIds.contains(cid)) {
                num++;
            }
        }
        if (num == 0) {
            return "0";
        } else {
            // 保留三位小数
            return NumberUtil.roundStr(NumberUtil.div(num, chunkIds.size()), 3);
        }
    }


    /**
     * 上下文精确度计算
     *
     * @param evaluateDatum
     * @return
     */
    private String contextPrecision(EvaluateDatum evaluateDatum) {
        // 标准上下文块
        String chunkId = evaluateDatum.getChunkId();
        List<String> chunkIds = JSONUtil.toList(chunkId, String.class).stream().distinct().collect(Collectors.toList());
        // 检索出来的上下文块
        String llmChunkId = evaluateDatum.getLlmChunkId();
        List<String> llmChunkIds = JSONUtil.toList(llmChunkId, String.class).stream().distinct().collect(Collectors.toList());

        if (llmChunkIds.size() == 0) {
            return "0";
        }

        int num = 0;
        for (String cid : llmChunkIds) {
            if (chunkIds.contains(cid)) {
                num++;
            }
        }
        if (num == 0) {
            return "0";
        }
        return NumberUtil.roundStr(NumberUtil.div(num, llmChunkIds.size()), 3);
    }


    /**
     * 上下文精确度计算
     *
     * @return
     */
    private String contextRelevancyReason(EvaluateDatum evaluateDatum) {
        String question = evaluateDatum.getQuestion();
        String llmChunkId = evaluateDatum.getLlmChunkId();
        List<String> llmChunkIds = JSONUtil.toList(llmChunkId, String.class).stream().distinct().collect(Collectors.toList());
        if (llmChunkIds.size() == 0) {
            RelevancyJson relevancyJson = new RelevancyJson();
            relevancyJson.setScore("0");
            relevancyJson.setReason("无");
            relevancyJson.setChunkId("无");
            return JSONUtil.toJsonStr(List.of(relevancyJson));
        }
        // 根据块id查询es获取文档块信息
        try {
            ArrayList<ChunkDeatils> contentList = new ArrayList<>();
            for (String chunkId : llmChunkIds) {
                List<Map> documents = (List<Map>) findByChunkId(chunkId);
                // 调用大模型获取评分
                ChunkDeatils chunkDeatils = new ChunkDeatils();
                chunkDeatils.setContent((String) documents.get(0).get("content"));
                chunkDeatils.setChunkId(chunkId);
                contentList.add(chunkDeatils);
            }
            return contextRelevancyByQuestionAndContext(question, contentList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String contextRelevancy(List<RelevancyJson> relevancyJsons) {
        double score = 0;
        List<String> collect = relevancyJsons.stream().map(RelevancyJson::getScore).collect(Collectors.toList());
        for (String s : collect) {
            score += Double.parseDouble(s);
        }
        return NumberUtil.roundStr(NumberUtil.div(score, relevancyJsons.size()), 3);
    }


    @Transactional(rollbackFor = Exception.class)
    public void llmAnswerSave(EvaluateDatum evaluateDatum, ChatAnswerResponse chatAnswerResponse) {
        String answer = chatAnswerResponse.getAnswer();
        evaluateDatum.setLlmAnswer(answer);
        ArrayList<String> documentIds = new ArrayList<>();
        ArrayList<String> chunkIds = new ArrayList<>();
        chatAnswerResponse.getReference().getReference().forEach(
                referenceDocument -> {
                    documentIds.add(referenceDocument.getDocumentId());
                    chunkIds.add(referenceDocument.getChunkId());
                }
        );
        Set<String> documentIdSet = new LinkedHashSet<>(documentIds);
        documentIds.clear();
        documentIds.addAll(documentIdSet);
        Set<String> chunkIdsSet = new LinkedHashSet<>(chunkIds);
        chunkIds.clear();
        chunkIds.addAll(chunkIdsSet);
        evaluateDatum.setLlmDocumentId(JSONUtil.toJsonStr(documentIds));
        evaluateDatum.setLlmChunkId(JSONUtil.toJsonStr(chunkIds));
        evaluateDatumRepository.save(evaluateDatum);
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(String questionAndAnswers, String documentId, String batchNum) {
        List<Question> questions = JSONUtil.toList(questionAndAnswers, Question.class);
        questions.forEach(question -> {
            EvaluateDatum evaluateDatum = new EvaluateDatum();
            evaluateDatum.setCreateTime(Instant.now());
            evaluateDatum.setQuestion(question.getQuestion());
            evaluateDatum.setChunkId(JSONUtil.toJsonStr(question.getRelatedChunkIds()));
            evaluateDatum.setReferenceAnswer(question.getReferenceAnswer());
            evaluateDatum.setDocumentId(JSONUtil.toJsonStr(List.of(documentId)));
            evaluateDatum.setBatchNum(batchNum);
            evaluateDatumRepository.save(evaluateDatum);
            evaluateDatumRepository.flush();
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveEvaluateDatum(List<Question> questions, EvaluateCreateReq req) {
        questions.forEach(question -> {
            EvaluateDatum evaluateDatum = new EvaluateDatum();
            evaluateDatum.setCreateTime(Instant.now());
            evaluateDatum.setQuestion(question.getQuestion());
            evaluateDatum.setChunkId(JSONUtil.toJsonStr(question.getRelatedChunkIds()));
            evaluateDatum.setReferenceAnswer(question.getReferenceAnswer());
            evaluateDatum.setDocumentId(JSONUtil.toJsonStr(question.getRelatedDocumentList()));
            evaluateDatum.setBatchNum(req.getBatchNum());
            evaluateDatum.setKbId(req.getKbId());
            evaluateDatumRepository.save(evaluateDatum);
            evaluateDatumRepository.flush();
        });
    }

    public String documentQuestionAndAnswerBySingle(String documentInfo, boolean colloquial) {
        String sysPromptStr = "你是一名专业的RAG（检索增强生成）评估数据集构建专家，具备精准提炼文档关键信息、设计高质量问题及匹配对应答案的能力。你的核心任务是根据用户提供的文档块信息（包含文档id、块id、块内容），生成一套符合RAG评估标准的结构化数据集。\n" +
                "在生成数据集时，需严格遵循以下要求：\n" +
                "- 问题设计要求：问题需基于文档块内容生成，必须为口语化问题，必须生成单文档块，跨文档块，跨文档的问题，覆盖不同难度层次（基础事实类、综合理解类、推理分析类、跨文档块问题，跨文档问题），表述清晰、无歧义，能准确考察对文档内容的理解与运用能力，避免生成与文档无关或无法从文档中找到答案的问题。\n" +
                "- related_document_list要求：准确列出问题关联的所有document_id，单个文档则数组含该id，多个文档需全部列出，确保关联性精准。\n" +
                "- reference_answer要求：严格依据文档块内容生成，准确、完整、简洁，直接回应问题，避免添加文档外信息或主观解读，保证客观性。\n" +
                "- related_chunk_ids要求：精准匹配问题及参考答案对应的所有chunk_id，不遗漏关键块、不包含无关块，多块整合则列出所有相关id。\n" +
                "- JSON格式强制规范：最终输出仅保留JSON内容，不得添加任何多余文本（如解释说明、注释等）。JSON需包含以下5个固定字段，字段名不可修改：\n" +
                " JSON字段名与格式强制规范（重点强化字段名）\n" +
                "1. **所有字段名必须用双引号包裹，不可省略、不可用单引号**  \n" +
                "   必须包含且仅包含以下5个固定字段，字段名不可修改，格式需完全匹配：\n" +
                "   - \"question_id\"：整数类型，从1开始递增（如1、2、3）。\n" +
                "   - \"related_document_list\"：数组类型，元素为带双引号的document_id字符串（如[\"doc_001\",\"doc_002\"]）。\n" +
                "   - \"question\"：字符串类型，带双引号，内部双引号转义为\\\"。\n" +
                "   - \"reference_answer\"：字符串类型，带双引号，严格依文档生成，内部双引号转义为\\\"。\n" +
                "   - \"related_chunk_ids\"：数组类型，元素为带双引号的chunk_id字符串（如[\"chunk_001\",\"chunk_003\"]）。" +
                "**输出前检查**：生成后请自动检查所有字段名和字符串值是否都被双引号包裹，确保无遗漏。";
        String text = "请基于以下文档块信息，按照系统提示词要求生成RAG评估数据集，并以JSON格式返回：\n" + documentInfo;
        if (colloquial) {
            sysPromptStr = "你是一名专业的RAG（检索增强生成）评估数据集构建专家，具备精准提炼文档关键信息、设计高质量问题及匹配对应答案的能力。你的核心任务是根据用户提供的文档块信息（包含文档id、块id、块内容），生成一套符合RAG评估标准的结构化数据集。\n" +
                    "在生成数据集时，需严格遵循以下要求：\n" +
                    "- 问题设计要求：问题需基于文档块内容生成，必须为口语化问题，必须生成单文档块，跨文档块，跨文档的问题，覆盖不同难度层次（基础事实类、综合理解类、推理分析类、跨文档块问题，跨文档问题），表述清晰、无歧义，能准确考察对文档内容的理解与运用能力，避免生成与文档无关或无法从文档中找到答案的问题。\n" +
                    "- related_document_list要求：准确列出问题关联的所有document_id，单个文档则数组含该id，多个文档需全部列出，确保关联性精准。\n" +
                    "- reference_answer要求：严格依据文档块内容生成，准确、完整、简洁，直接回应问题，避免添加文档外信息或主观解读，保证客观性。\n" +
                    "- related_chunk_ids要求：精准匹配问题及参考答案对应的所有chunk_id，不遗漏关键块、不包含无关块，多块整合则列出所有相关id。\n" +
                    "- JSON格式强制规范：最终输出仅保留JSON内容，不得添加任何多余文本（如解释说明、注释等）。JSON需包含以下5个固定字段，字段名不可修改：\n" +
                    " JSON字段名与格式强制规范（重点强化字段名）\n" +
                    "1. **所有字段名必须用双引号包裹，不可省略、不可用单引号**  \n" +
                    "   必须包含且仅包含以下5个固定字段，字段名不可修改，格式需完全匹配：\n" +
                    "   - \"question_id\"：整数类型，从1开始递增（如1、2、3）。\n" +
                    "   - \"related_document_list\"：数组类型，元素为带双引号的document_id字符串（如[\"doc_001\",\"doc_002\"]）。\n" +
                    "   - \"question\"：字符串类型，带双引号，内部双引号转义为\\\"。\n" +
                    "   - \"reference_answer\"：字符串类型，带双引号，严格依文档生成，内部双引号转义为\\\"。\n" +
                    "   - \"related_chunk_ids\"：数组类型，元素为带双引号的chunk_id字符串（如[\"chunk_001\",\"chunk_003\"]）。" +
                    "**输出前检查**：生成后请自动检查所有字段名和字符串值是否都被双引号包裹，确保无遗漏。";
        }
        SystemMessage systemMessage = new SystemMessage(sysPromptStr);
        UserMessage userMessage = new UserMessage(text);
        String response = chatModel.call(systemMessage, userMessage);
        if (response.startsWith("```json")) {
            String replace = response.replace("```json", "").replace("```", "");
            logger.info("response:" + replace);
            return replace;
        }
        logger.info("response:" + response);
        return response;
    }

//    public String questionsAndAnswersFusion(String questionAndAnswers) {
//
//        String sysPromptStr = "你是 RAG 系统测试数据处理助手，核心任务是接收含 “问题(字段名：question)、答案（字段名：referenceAnswer）、相关文档id列表（字段名：relatedDocumentList）、相关片段id列表（字段名：relatedChunkIds）” 的条目列表，按规则合并相似问题并生成指定 JSON 格式结果。请严格遵守以下要求：\n" +
//                "相似问题判定标准\n" +
//                "核心语义一致：提问意图、核心诉求有重合（如 “K8s Deployment 作用” 与 “Kubernetes 中其他功能” 视为相似）。\n" +
//                "忽略非核心差异：不区分句式、用词、标点的细微不同，仅以 “用户核心需求” 为判断依据。\n" +
//                "字段合并规则\n" +
//                "question_id：为每个合并组分配唯一序号，从 1 开始递增（独立条目也需单独分配）。\n" +
//                "related_document_list：收集合并组内所有不重复的 docid，以数组形式存储，元素为原始 docid 字符串。\n" +
//                "question：保留合并组内最简洁、覆盖性最强的 1 个问题；若需体现所有相似表述，可用 “[问题 1 / 问题 2]” 格式（如 “[K8s Deployment 作用 / Kubernetes 中 Deployment 主要功能]”）。\n" +
//                "reference_answer：合并所有相似问题对应的答案，用 “1. 答案内容；2. 答案内容” 的序号格式整合，完全重复的答案需去重。\n" +
//                "related_chunk_ids：收集合并组内所有不重复的 chunkid，以数组形式存储，元素为原始 chunkid 字符串。\n" +
//                "输出格式强制要求\n" +
//                "仅输出 JSON 数组，无任何额外文字（包括解释、说明、换行注释）。\n" +
//                "JSON 需符合标准语法，字段名与示例完全一致，数组元素无多余逗号。\n" +
//                "独立条目（无相似问题的条目）需按相同字段格式单独作为 JSON 数组元素，不单独分类标注。\n" +
//                "**输出前检查**：生成后请自动检查所有字段名和字符串值是否都被双引号包裹，确保无遗漏。";
//        String text = "以下是我用于 RAG 系统测试的 “问题 - 答案 - chunkid-docid” 条目列表，请按规则合并相似问题，并生成与示例格式完全一致的 JSON 结果：\n" + questionAndAnswers;
//        SystemMessage systemMessage = new SystemMessage(sysPromptStr);
//        UserMessage userMessage = new UserMessage(text);
//        String response = chatModel.call(systemMessage, userMessage);
//        if (response.startsWith("```json")) {
//            String replace = response.replace("```json", "").replace("```", "");
//            logger.info("response:" + replace);
//            return replace;
//        }
//        logger.info("response:" + response);
//        return response;
//    }


    public String documentQuestionAndAnswer(String documentInfo) {
        String sysPromptStr = "你是一名专业的RAG（检索增强生成）评估数据集构建专家，具备精准提炼文档关键信息、设计高质量问题及匹配对应答案的能力。你的核心任务是根据用户提供的文档块信息（包含文档id、块id、块内容），生成一套符合RAG评估标准的结构化数据集。\n" +
                "在生成数据集时，需严格遵循以下要求：\n" +
                "- 问题设计要求：问题需基于文档块内容生成，必须生成单文档块，跨文档块，跨文档的问题，覆盖不同难度层次（基础事实类、综合理解类、推理分析类、跨文档块问题，跨文档问题），表述清晰、无歧义，能准确考察对文档内容的理解与运用能力，避免生成与文档无关或无法从文档中找到答案的问题。\n" +
                "- related_document_list要求：准确列出问题关联的所有document_id，单个文档则数组含该id，多个文档需全部列出，确保关联性精准。\n" +
                "- reference_answer要求：严格依据文档块内容生成，准确、完整、简洁，直接回应问题，避免添加文档外信息或主观解读，保证客观性。\n" +
                "- related_chunk_ids要求：精准匹配问题及参考答案对应的所有chunk_id，不遗漏关键块、不包含无关块，多块整合则列出所有相关id。\n" +
                "- JSON格式强制规范：最终输出仅保留JSON内容，不得添加任何多余文本（如解释说明、注释等）。JSON需包含以下5个固定字段，字段名不可修改：\n" +
                "        \"question_id\"：从1开始按顺序递增的整数，如1、2、3...\n" +
                "- \"related_document_list\"：数组形式，元素为字符串类型的document_id，如[\"doc_001\",\"doc_002\"]\n" +
                "- \"question\"：字符串类型的问题内容，需用双引号包裹，内部若含双引号需转义为\\\"\n" +
                "- \"reference_answer\"：字符串类型的参考答案，用双引号包裹，内部双引号转义为\\\"\n" +
                "- \"related_chunk_ids\"：数组形式，元素为字符串类型的chunk_id，如[\"chunk_001\",\"chunk_003\"] \n" +
                "**输出前检查**：生成后请自动检查所有字段名和字符串值是否都被双引号包裹，确保无遗漏。";
        String text = "请基于以下文档块信息，按照系统提示词要求生成RAG评估数据集，并以JSON格式返回：\n" + documentInfo;
        SystemMessage systemMessage = new SystemMessage(sysPromptStr);
        UserMessage userMessage = new UserMessage(text);
        String response = chatModel.call(systemMessage, userMessage);
        if (response.startsWith("```json")) {
            String replace = response.replace("```json", "").replace("```", "");
            logger.info("response:" + replace);
            return replace;
        }
        logger.info("response:" + response);
        return response;
    }


    public String contextRelevancyByQuestionAndContext(String question, List<ChunkDeatils> context) {
        String sysPromptStr = "你将作为一名严格的文本相关性评估专家，负责判断 “检索文档” 与 “用户问题” 之间的关联程度，并给出 0-1 分的精准评分。请严格遵循以下规则执行：\n" +
                "评分标准：\n" +
                "0 分：文档内容与用户问题完全无关，无任何可用于回答问题的信息。\n" +
                "0.1-0.4 分：文档内容与用户问题关联性极低，仅包含极少量微弱相关的信息，无法为回答问题提供有效支撑。\n" +
                "0.5-0.7 分：文档内容与用户问题部分相关，包含一定可用于回答问题的信息，但信息不完整或关联性一般，需结合其他文档补充。\n" +
                "0.8-1.0 分：文档内容与用户问题高度相关，包含直接、完整且关键的信息，可独立用于准确回答用户问题。\n" +
                "输出要求：\n" +
                "仅基于文档原文和问题判断，不引入外部知识。\n" +
                "必须返回 JSON 格式，字段为：\"chunkId\"（分块序号）、\"score\"（评分，数字类型，保留 3 位小数）、\"reason\"（评分理由，字符串类型，1-2 句话）。\n" +
                "多文档评估时，返回 JSON 数组（每个元素对应一个文档）。\n" +
                "禁止任何额外文本（如解释、说明），仅输出 JSON。";
        String userPromptStr = "请你批量评估以下所有 “检索文档” 与 “用户问题” 的相关性，每个文档单独评分，且严格遵循系统提示中的评分规则,按系统提示返回 JSON 数组:\n";
        String userQuestionStr = "用户问题：" + question + "\n";
        String userContextStr = "检索文档列表: \n";
        for (int i = 1; i <= context.size(); i++) {
            userContextStr += "chunkId：" + i + ",内容：" + context.get(i - 1) + "\n";
        }

        SystemMessage systemMessage = new SystemMessage(sysPromptStr);
        UserMessage userMessage = new UserMessage(userPromptStr + userQuestionStr + userContextStr);
        String response = chatModel.call(systemMessage, userMessage);
        logger.info("查询相关性-结果：{}", response);
        return response;
    }

    public String faithfulnessByContextAndAnswer(String answer, List<ChunkDeatils> context) {
        String sysPromptStr = "你是一名严格的文本忠实度评估专家，任务是判断 “生成回答” 是否完全基于 “检索到的上下文”，不包含任何未在上下文中出现的信息（包括事实、观点、数据等）。请严格遵循以下规则：\n" +
                "评分标准（0-1 分，保留 3 位小数）：\n" +
                "1.0 分：回答中所有信息（事实、观点、数据等）均能在上下文中找到明确依据，无任何编造内容。\n" +
                "0.8-0.9 分：回答中几乎所有信息均来自上下文，仅存在极个别细微的、不影响核心事实的 “合理推断”（如基于上下文逻辑的自然延伸，且未引入新信息）。\n" +
                "0.5-0.7 分：回答中存在部分信息未在上下文中出现（如新增次要事实、无关细节），但核心结论仍基于上下文，未严重偏离。\n" +
                "0.1-0.4 分：回答中存在较多未在上下文中出现的信息，核心结论部分依赖编造内容，与上下文关联性较弱。\n" +
                "0.0 分：回答中所有核心信息均未在上下文中出现，完全基于编造内容，与上下文无关。\n" +
                "输出要求：\n" +
                "仅对比 “生成回答” 与 “检索到的上下文”，不引入外部知识或判断信息的真实性（只需判断是否来自上下文）。\n" +
                "必须返回 JSON 格式，字段为：\"score\"（评分，数字类型，保留 3 位小数）、\"reason\"（评分理由，字符串类型，需明确指出回答中是否有未在上下文出现的信息，若有则举例说明）。\n" +
                "禁止任何额外文本，仅输出 JSON。";
        String userPromptStr = "请评估以下 “生成回答” 相对于 “检索到的上下文” 的忠实度，并按系统提示返回 JSON：\n";
        String userContextStr = "检索到的上下文： \n";
        for (int i = 1; i <= context.size(); i++) {
            userContextStr += "chunkId：" + i + ",内容：" + context.get(i - 1) + "\n";
        }
        String userAnswerStr = "生成回答：" + answer + "\n";

        SystemMessage systemMessage = new SystemMessage(sysPromptStr);
        UserMessage userMessage = new UserMessage(userPromptStr + userContextStr + userAnswerStr);
        String response = chatModel.call(systemMessage, userMessage);
        logger.info("忠实度-结果：{}", response);
        return response;
    }

    public String answerSimilarityByQuestionAndAnswer(String referenceAnswer, String answer) {
        String sysPromptStr = "你是一名严格的答案相似度评估专家，任务是判断 RAG 系统的 “生成回答” 与 “理想标准答案” 在核心信息、逻辑结构、表述意图上的相似程度。请严格遵循以下规则：\n" +
                "评分标准（0-1 分，保留 3 位小数）：\n" +
                "1.0 分：生成回答与理想标准答案完全一致，核心信息、逻辑框架、关键结论均无差异，仅可能存在表述措辞的细微不同。\n" +
                "0.8-0.9 分：生成回答与理想标准答案高度相似，核心信息和关键结论完全一致，仅在次要细节（如举例、补充说明）或表述顺序上存在微小差异。\n" +
                "0.5-0.7 分：生成回答与理想标准答案部分相似，核心结论一致，但在核心信息的完整性（如缺失 1-2 个关键要点）或逻辑结构上存在明显差异。\n" +
                "0.1-0.4 分：生成回答与理想标准答案低度相似，核心结论部分重合，核心信息缺失较多，或逻辑结构差异极大，仅存在少量关联内容。\n" +
                "0.0 分：生成回答与理想标准答案完全不相似，核心结论相悖或无关，无任何一致的核心信息。\n" +
                "输出要求：\n" +
                "仅对比 “生成回答” 与 “理想标准答案”，不引入外部知识或判断内容真实性，聚焦 “信息与结构的重合度”。\n" +
                "必须返回 JSON 格式，字段为：\"score\"（评分，数字类型，保留 3 位小数）、\"reason\"（评分理由，字符串类型，需明确指出两者在核心信息、逻辑结构上的相似 / 差异点，举例说明）。\n" +
                "禁止任何额外文本，仅输出 JSON。";
        String userPromptStr = "请评估以下 “生成回答” 与 “理想标准答案” 的相似度，并按系统提示返回 JSON：\n";
        String userQuestionStr = "理想标准答案：" + referenceAnswer + "\n";
        String userAnswerStr = "生成回答：" + answer + "\n";
        SystemMessage systemMessage = new SystemMessage(sysPromptStr);
        UserMessage userMessage = new UserMessage(userPromptStr + userQuestionStr + userAnswerStr);
        String response = chatModel.call(systemMessage, userMessage);
        logger.info("答案相似度-结果：{}", response);
        return response;
    }

    public String answerRelevanceByQuestionAndAnswer(String question, String answer) {
        String sysPromptStr = "你是一名严格的答案相关性评估专家，任务是判断 “生成回答” 与 “用户问题” 的匹配程度，即回答是否直接、充分地回应了问题的核心需求。请严格遵循以下规则：\n" +
                "评分标准（0-1 分，保留 3 位小数）：\n" +
                "1.0 分：回答完全聚焦问题核心，包含所有必要信息，能彻底解决用户疑问，无冗余内容。\n" +
                "0.8-0.9 分：回答紧密围绕问题核心，覆盖主要需求，仅缺少个别次要细节，整体能有效解决疑问。\n" +
                "0.5-0.7 分：回答部分关联问题核心，提及部分相关信息，但存在明显遗漏或偏离（如过多无关细节），需结合其他信息才能完全解决疑问。\n" +
                "0.1-0.4 分：回答与问题核心关联性较弱，仅涉及极少相关内容，大部分内容偏离主题，无法有效解决疑问。\n" +
                "0.0 分：回答与问题完全无关，未提及任何与问题相关的信息，完全无法解决疑问。\n" +
                "输出要求：\n" +
                "仅基于 “用户问题” 和 “生成回答” 判断，不考虑回答是否基于上下文（忠实度）或信息真实性。\n" +
                "必须返回 JSON 格式，字段为：\"score\"（评分，数字类型，保留 3 位小数）、\"reason\"（评分理由，字符串类型，需明确指出回答是否覆盖问题核心，若偏离则举例说明）。\n" +
                "禁止任何额外文本，仅输出 JSON。";
        String userPromptStr = "请评估以下 “生成回答” 与 “用户问题” 的相关性，并按系统提示返回 JSON：\n";
        String userQuestionStr = "用户问题：" + question + "\n";
        String userAnswerStr = "生成回答：" + answer + "\n";
        SystemMessage systemMessage = new SystemMessage(sysPromptStr);
        UserMessage userMessage = new UserMessage(userPromptStr + userQuestionStr + userAnswerStr);
        String response = chatModel.call(systemMessage, userMessage);
        logger.info("答案相关性-结果：{}", response);
        return response;
    }

    public String directionImprovementByIndicator(EvaluateScore evaluateScore) {
        String sysPromptStr = "你是一名资深的 RAG（检索增强生成）系统优化专家，具备丰富的 RAG 评估与调优经验。请基于用户提供的 RAG 评估指标数据，按照以下逻辑输出改造指南：\n" +
                "指标解读：先明确各指标的含义及当前表现反映的核心问题（如召回率低代表检索环节漏检相关文档，准确率低代表生成内容与参考答案偏差大等）；\n" +
                "问题定位：结合指标间关联性分析根因（如 “精确率低但召回率高” 可能是检索策略过于宽泛，引入过多噪声文档）；\n" +
                "改造方案：针对每个问题提供可落地的优化措施，需区分 “检索环节”“生成环节”“数据环节” 三类优化方向，每个方案说明具体操作（如检索环节调整向量模型、优化检索参数；生成环节优化 Prompt 模板、增加事实校验；数据环节清洗知识库、补充高频问题相关文档等）；\n" +
                "优先级建议：按 “紧急性 + 投入产出比” 对改造方案排序，标注哪些是 “立即执行”，哪些是 “长期优化”；\n" +
                "效果验证：说明每个改造方案落地后，如何通过指标复查验证效果（如召回率提升需重新统计相关文档的命中数量）。\n" +
                "请确保指南专业、具体，避免空泛建议，需紧密结合用户提供的指标数据展开。";
        String userPromptStr = "我正在开展 RAG 系统的评估工作，当前统计的指标如下（若部分指标未统计可说明，你基于已有指标分析）：\n";
        String retrievalProcessIndicatorsStr = "检索环节指标：\n" +
                "上下文召回率：" + evaluateScore.getContextRecallAverageScore() + "\n" +
                "上下文精确度：" + evaluateScore.getContextPrecisionAverageScore() + "\n" +
                "上下文相关性：" + evaluateScore.getContextRelevancyAverageScore() + "\n";
        String generationProcessIndicatorsStr = "生成环节指标：\n" +
                " 忠实度：" + evaluateScore.getFaithfulnessAverageScore() + "\n" +
                " 答案相关性：" + evaluateScore.getAnswerRelevancyAverageScore() + "\n";
        String comprehensiveExperienceIndicators = "综合体验指标：\n" +
                " 综合平均得分：" + evaluateScore.getOverallScore() + "\n" +
                " 答案相似度：" + evaluateScore.getAnswerSimilarityAverageScore() + "\n";

        SystemMessage systemMessage = new SystemMessage(sysPromptStr);
        UserMessage userMessage = new UserMessage(userPromptStr + retrievalProcessIndicatorsStr + generationProcessIndicatorsStr + comprehensiveExperienceIndicators);
        String response = chatModel.call(systemMessage, userMessage);
        logger.info("改进方向-结果：{}", response);
        return response;
    }


    /**
     * 根据metadata.documentId精确查询文档
     *
     * @param targetDocumentId 要查询的documentId值
     * @return 匹配的文档列表（包含完整字段）
     */
    public List<?> findByMetadataDocumentId(String targetDocumentId) throws IOException {
        // 1. 创建搜索请求
        SearchRequest searchRequest = SearchRequest.of(sr -> sr
                .index("rag_store_new")
                .query(q -> q.term(eq -> eq.field("metadata.documentId").value(targetDocumentId)))
                .size(1000)
        );

        // 3. 执行查询
        SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

        // 4. 解析结果
//        return response.hits().hits().stream()
//                .map(v -> v.source())
//                .collect(Collectors.toList());
        return response.hits().hits().stream()
                .map(v -> v.source())
                .collect(Collectors.toList());
    }

    /**
     * 根据id精确查询文档
     *
     * @param chunkId 要查询的chunkId值
     * @return 匹配的文档列表（包含完整字段）
     */
    public List<?> findByChunkId(String chunkId) throws IOException {
        // 1. 创建搜索请求
        SearchRequest searchRequest = SearchRequest.of(sr -> sr
                .index("rag_store_new")
                .query(q -> q.term(eq -> eq.field("id").value(chunkId)))
                .size(100)
        );

        // 3. 执行查询
        SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

        return response.hits().hits().stream()
                .map(v -> v.source())
                .collect(Collectors.toList());
    }
}

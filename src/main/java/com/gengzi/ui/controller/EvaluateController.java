package com.gengzi.ui.controller;


import com.gengzi.request.EvaluateCreateReq;
import com.gengzi.request.EvaluateStartReq;
import com.gengzi.response.Result;
import com.gengzi.ui.service.EvaluateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 评估rag系统
 */
@RestController
@Tag(name = "rag评估", description = "rag评估")
public class EvaluateController {


    @Autowired
    private EvaluateService evaluateService;

    /**
     * 通过llm生成评估集数据+人工修正
     */
    @PostMapping("/evaluate/create")
    public Result<?> evaluateCreate(@RequestBody EvaluateCreateReq req) throws IOException {
        evaluateService.evaluateCreate(req);
        return Result.success(true);
    }


    /**
     * 评估训练集和真实回答
     */
    @PostMapping("/evaluate/start")
    public Result<?> evaluateStart(@RequestBody EvaluateStartReq req) {
        evaluateService.evaluate(req.getBatchNum());
        return Result.success(true);
    }


    /**
     * 通过llm生成评估集数据+人工修正
     */
    @GetMapping("/evaluate/generate")
    public void evaluateGenerate(@RequestParam(value = "documentIds", required = false) List<String> documentIds,
                                 @RequestParam(value = "batchNum") String batchNum) throws IOException {
        evaluateService.evaluateGenerate(documentIds, batchNum);
    }


    /**
     * 评估训练集和真实回答
     */
    @GetMapping("/evaluate")
    public void evaluate(@RequestParam(value = "coonversationId") String coonversationId,
                         @RequestParam(value = "batchNum") String batchNum) {
        evaluateService.evaluate(coonversationId, batchNum);
    }


    /**
     * 基于数据库训练集数据，进行指标的计算
     */
    @GetMapping("/evaluate/calculate")
    public void evaluateCalculate(@RequestParam(value = "batchNum") String batchNum) {
        evaluateService.evaluateCalculate(batchNum);
    }

    /**
     * 统计评估结果
     */
    @GetMapping("/evaluate/statistics")
    public void evaluateStatistics(@RequestParam(value = "batchNum") String batchNum) {
        evaluateService.evaluateStatistics(batchNum);
    }


    /**
     * 获取图信息和改进指南
     */
    @GetMapping("/evaluate/statistics/linechart")
    public List<?> evaluateStatisticsLineChart() {
        return evaluateService.evaluateStatisticsLineChart();
    }


    /**
     * 根据批次获取评估集数据
     */
    @GetMapping("/evaluate/get/statistics/batchnum")
    public Result<?> evaluateStatisticsByBatchNum(@RequestParam(value = "batchNum") String batchNum,
                                                  @PageableDefault(page = 0, size = 10) Pageable pageable) {
        Page<?> documents = evaluateService.evaluateStatisticsByBatchNum(batchNum, pageable);
        return Result.success(documents);
    }

    /**
     * 获取所有批次信息
     * @param isUntrainedBatch 是否为未训练批次
     */
    @GetMapping("/evaluate/get/batchnums")
    public List<?> evaluateStatisticsBatchNums(@RequestParam(required = false)  boolean isUntrainedBatch) {
        return evaluateService.evaluateStatisticsBatchNums(isUntrainedBatch);
    }




}

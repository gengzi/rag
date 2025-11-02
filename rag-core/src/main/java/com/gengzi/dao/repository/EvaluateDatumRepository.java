package com.gengzi.dao.repository;

import com.gengzi.dao.EvaluateDatum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluateDatumRepository extends JpaRepository<EvaluateDatum, Long>, JpaSpecificationExecutor<EvaluateDatum> {


    @Query("select e from EvaluateDatum e where e.batchNum = :batchNum and (e.llmAnswer = '' or e.llmAnswer is null)    ")
    List<EvaluateDatum> findByLlmAnswerEqualsAndBatchNumEquals(String batchNum);

    List<EvaluateDatum> findEvaluateDataByBatchNum(@Size(max = 32) @NotNull String batchNum);

    @Query("select distinct  e.batchNum from EvaluateDatum e")
    List<String> findAllBatchNums();

    @Query("select distinct  e.batchNum from EvaluateDatum e where (e.llmAnswer = '' or e.llmAnswer is null)  ")
    List<String> findUntrainedBatchNums();
}